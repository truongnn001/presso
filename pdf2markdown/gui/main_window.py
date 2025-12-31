"""
Giao diện GUI cho PDF to Markdown Converter
Hỗ trợ Python 3.13
"""

import tkinter as tk
from tkinter import ttk, filedialog, messagebox, scrolledtext
from pathlib import Path
import threading
import logging
from datetime import datetime
import json
import os
import sys

from pdf2markdown import PDFToMarkdownConverter, find_pdf_files


class PDFConverterGUI:
    """Giao diện GUI cho PDF to Markdown Converter"""
    
    def __init__(self, root):
        self.root = root
        self.root.title("PDF to Markdown Converter")
        self.root.geometry("950x750")
        self.root.resizable(True, True)
        
        # File config để lưu đường dẫn (tìm trong config/ hoặc parent)
        config_dir = Path(__file__).parent.parent / "config"
        if not config_dir.exists():
            config_dir = Path(__file__).parent.parent.parent / "pdf2markdown" / "config"
        if not config_dir.exists():
            config_dir = Path(__file__).parent.parent.parent  # Fallback về root
        self.config_file = config_dir / "gui_config.json"
        
        # Biến
        self.input_path = tk.StringVar()
        self.output_path = tk.StringVar()
        self.is_processing = False
        self.converter = None
        self.total_files = 0
        self.current_file_index = 0
        self.stop_requested = False
        self.selected_files = []  # Danh sách file đã chọn (cho chế độ multiple)
        
        # Load config đã lưu
        self.load_config()
        
        # Setup logging để hiển thị trong GUI
        self.setup_logging()
        
        # Tạo giao diện
        self.create_widgets()
        
        # Center window
        self.center_window()
    
    def setup_logging(self):
        """Setup logging để hiển thị trong GUI"""
        self.log_handler = GUIHandler(self)
        logger = logging.getLogger()
        # Xóa các handler cũ để tránh duplicate
        logger.handlers = []
        logger.addHandler(self.log_handler)
        logger.setLevel(logging.INFO)
    
    def center_window(self):
        """Căn giữa cửa sổ"""
        self.root.update_idletasks()
        width = self.root.winfo_width()
        height = self.root.winfo_height()
        x = (self.root.winfo_screenwidth() // 2) - (width // 2)
        y = (self.root.winfo_screenheight() // 2) - (height // 2)
        self.root.geometry(f'{width}x{height}+{x}+{y}')
    
    def create_widgets(self):
        """Tạo các widget cho giao diện"""
        
        # Frame chính
        main_frame = ttk.Frame(self.root, padding="10")
        main_frame.grid(row=0, column=0, sticky=(tk.W, tk.E, tk.N, tk.S))
        
        # Cấu hình grid
        self.root.columnconfigure(0, weight=1)
        self.root.rowconfigure(0, weight=1)
        main_frame.columnconfigure(1, weight=1)
        
        # Tiêu đề
        title_label = ttk.Label(
            main_frame, 
            text="PDF to Markdown Converter", 
            font=("Arial", 16, "bold")
        )
        title_label.grid(row=0, column=0, columnspan=3, pady=(0, 20))
        
        # Section 1: Input
        input_frame = ttk.LabelFrame(main_frame, text="Đầu vào (Input)", padding="10")
        input_frame.grid(row=1, column=0, columnspan=3, sticky=(tk.W, tk.E), pady=5)
        input_frame.columnconfigure(1, weight=1)
        
        ttk.Label(input_frame, text="File/Thư mục PDF:").grid(row=0, column=0, sticky=tk.W, padx=5)
        ttk.Entry(input_frame, textvariable=self.input_path, width=50).grid(
            row=0, column=1, sticky=(tk.W, tk.E), padx=5
        )
        ttk.Button(
            input_frame, 
            text="Chọn...", 
            command=self.browse_input
        ).grid(row=0, column=2, padx=5)
        
        # Radio buttons cho chế độ
        self.mode_var = tk.StringVar(value="file")
        # Thêm callback để reset khi chuyển chế độ
        self.mode_var.trace('w', self.on_mode_change)
        mode_frame = ttk.Frame(input_frame)
        mode_frame.grid(row=1, column=0, columnspan=3, pady=5)
        
        ttk.Radiobutton(
            mode_frame, 
            text="File PDF đơn", 
            variable=self.mode_var, 
            value="file"
        ).pack(side=tk.LEFT, padx=10)
        
        ttk.Radiobutton(
            mode_frame, 
            text="Nhiều file", 
            variable=self.mode_var, 
            value="multiple"
        ).pack(side=tk.LEFT, padx=10)
        
        ttk.Radiobutton(
            mode_frame, 
            text="Thư mục (Batch)", 
            variable=self.mode_var, 
            value="folder"
        ).pack(side=tk.LEFT, padx=10)
        
        # Section 2: Output
        output_frame = ttk.LabelFrame(main_frame, text="Đầu ra (Output)", padding="10")
        output_frame.grid(row=2, column=0, columnspan=3, sticky=(tk.W, tk.E), pady=5)
        output_frame.columnconfigure(1, weight=1)
        
        ttk.Label(output_frame, text="Thư mục output:").grid(row=0, column=0, sticky=tk.W, padx=5)
        ttk.Entry(output_frame, textvariable=self.output_path, width=50).grid(
            row=0, column=1, sticky=(tk.W, tk.E), padx=5
        )
        ttk.Button(
            output_frame, 
            text="Chọn...", 
            command=self.browse_output
        ).grid(row=0, column=2, padx=5)
        
        # Checkbox cho recursive
        self.recursive_var = tk.BooleanVar(value=True)
        ttk.Checkbutton(
            output_frame,
            text="Tìm đệ quy trong thư mục con (Recursive)",
            variable=self.recursive_var
        ).grid(row=1, column=0, columnspan=3, pady=5, sticky=tk.W)
        
        # Section 3: Nút bắt đầu
        button_frame = ttk.Frame(main_frame)
        button_frame.grid(row=3, column=0, columnspan=3, pady=10)
        
        self.start_button = ttk.Button(
            button_frame,
            text="Bắt đầu chuyển đổi",
            command=self.start_conversion
        )
        self.start_button.pack(side=tk.LEFT, padx=5)
        
        self.stop_button = ttk.Button(
            button_frame,
            text="Dừng",
            command=self.stop_conversion,
            state=tk.DISABLED
        )
        self.stop_button.pack(side=tk.LEFT, padx=5)
        
        # Section 4: Progress với phần trăm
        progress_frame = ttk.LabelFrame(main_frame, text="Tiến trình", padding="10")
        progress_frame.grid(row=4, column=0, columnspan=3, sticky=(tk.W, tk.E), pady=5)
        progress_frame.columnconfigure(0, weight=1)
        
        # Label hiển thị file hiện tại
        self.progress_var = tk.StringVar(value="Sẵn sàng...")
        progress_label = ttk.Label(progress_frame, textvariable=self.progress_var)
        progress_label.grid(row=0, column=0, sticky=tk.W, pady=(0, 5))
        
        # Progress bar với phần trăm
        progress_bar_frame = ttk.Frame(progress_frame)
        progress_bar_frame.grid(row=1, column=0, sticky=(tk.W, tk.E), pady=5)
        progress_bar_frame.columnconfigure(0, weight=1)
        
        self.progress_bar = ttk.Progressbar(
            progress_bar_frame, 
            mode='determinate',
            length=400
        )
        self.progress_bar.grid(row=0, column=0, sticky=(tk.W, tk.E), padx=(0, 10))
        
        # Label phần trăm
        self.percent_var = tk.StringVar(value="0%")
        percent_label = ttk.Label(progress_bar_frame, textvariable=self.percent_var, width=6)
        percent_label.grid(row=0, column=1, sticky=tk.E)
        
        # Label thống kê nhanh
        self.stats_var = tk.StringVar(value="")
        stats_label = ttk.Label(progress_frame, textvariable=self.stats_var, font=("Arial", 9))
        stats_label.grid(row=2, column=0, sticky=tk.W, pady=(5, 0))
        
        # Section 5: Log/Output
        log_frame = ttk.LabelFrame(main_frame, text="Nhật ký (Log)", padding="10")
        log_frame.grid(row=5, column=0, columnspan=3, sticky=(tk.W, tk.E, tk.N, tk.S), pady=5)
        log_frame.columnconfigure(0, weight=1)
        log_frame.rowconfigure(0, weight=1)
        main_frame.rowconfigure(5, weight=1)
        
        self.log_text = scrolledtext.ScrolledText(
            log_frame,
            height=15,
            width=80,
            wrap=tk.WORD,
            font=("Consolas", 9)
        )
        self.log_text.grid(row=0, column=0, sticky=(tk.W, tk.E, tk.N, tk.S))
        
        # Nút xóa log
        ttk.Button(
            log_frame,
            text="Xóa log",
            command=self.clear_log
        ).grid(row=1, column=0, pady=5)
        
        # Status bar
        self.status_var = tk.StringVar(value="Sẵn sàng")
        status_bar = ttk.Label(
            main_frame,
            textvariable=self.status_var,
            relief=tk.SUNKEN,
            anchor=tk.W
        )
        status_bar.grid(row=6, column=0, columnspan=3, sticky=(tk.W, tk.E), pady=5)
    
    def load_config(self):
        """Load config đã lưu từ file"""
        try:
            if self.config_file.exists():
                with open(self.config_file, 'r', encoding='utf-8') as f:
                    config = json.load(f)
                    # Lưu vào biến để dùng làm initialdir
                    self.last_input_dir = config.get('last_input_dir', '')
                    self.last_output_dir = config.get('last_output_dir', '')
            else:
                self.last_input_dir = ''
                self.last_output_dir = ''
        except Exception as e:
            # Nếu lỗi, khởi tạo mặc định
            self.last_input_dir = ''
            self.last_output_dir = ''
    
    def save_config(self):
        """Lưu config vào file"""
        try:
            config = {
                'last_input_dir': self.last_input_dir,
                'last_output_dir': self.last_output_dir
            }
            with open(self.config_file, 'w', encoding='utf-8') as f:
                json.dump(config, f, ensure_ascii=False, indent=2)
        except Exception as e:
            # Không hiển thị lỗi, chỉ log
            pass
    
    def browse_input(self):
        """Chọn file hoặc thư mục input"""
        mode = self.mode_var.get()
        
        # Lấy thư mục khởi tạo từ config hoặc từ path hiện tại
        initial_dir = self.last_input_dir
        if not initial_dir or not Path(initial_dir).exists():
            # Nếu có path hiện tại, dùng parent directory
            current_path = self.input_path.get().strip()
            if current_path:
                path_obj = Path(current_path)
                if path_obj.exists():
                    initial_dir = str(path_obj.parent if path_obj.is_file() else path_obj)
        
        if mode == "file":
            path = filedialog.askopenfilename(
                title="Chọn file PDF",
                filetypes=[("PDF files", "*.pdf"), ("All files", "*.*")],
                initialdir=initial_dir if initial_dir and Path(initial_dir).exists() else None
            )
            if path:
                self.input_path.set(path)
                # Lưu thư mục đã chọn
                path_obj = Path(path)
                self.last_input_dir = str(path_obj.parent)
                self.save_config()
                self.log(f"Đã chọn file: {path}")
        elif mode == "multiple":
            # Chọn nhiều file
            paths = filedialog.askopenfilenames(
                title="Chọn nhiều file PDF",
                filetypes=[("PDF files", "*.pdf"), ("All files", "*.*")],
                initialdir=initial_dir if initial_dir and Path(initial_dir).exists() else None
            )
            if paths:
                self.selected_files = list(paths)
                # Hiển thị số lượng file và tên file đầu tiên
                if len(self.selected_files) == 1:
                    display_text = self.selected_files[0]
                else:
                    display_text = f"{len(self.selected_files)} file đã chọn: {Path(self.selected_files[0]).name}"
                    if len(self.selected_files) > 1:
                        display_text += f", ..."
                self.input_path.set(display_text)
                # Lưu thư mục đã chọn (từ file đầu tiên)
                if self.selected_files:
                    path_obj = Path(self.selected_files[0])
                    self.last_input_dir = str(path_obj.parent)
                    self.save_config()
                self.log(f"Đã chọn {len(self.selected_files)} file PDF")
        else:  # mode == "folder"
            path = filedialog.askdirectory(
                title="Chọn thư mục chứa PDF",
                initialdir=initial_dir if initial_dir and Path(initial_dir).exists() else None
            )
            if path:
                self.input_path.set(path)
                # Lưu thư mục đã chọn
                self.last_input_dir = str(Path(path))
                self.save_config()
                self.log(f"Đã chọn thư mục: {path}")
    
    def on_mode_change(self, *args):
        """Callback khi chuyển đổi chế độ - reset input path và danh sách file"""
        self.input_path.set("")
        self.selected_files = []
    
    def browse_output(self):
        """Chọn thư mục output"""
        # Lấy thư mục khởi tạo từ config hoặc từ path hiện tại
        initial_dir = self.last_output_dir
        if not initial_dir or not Path(initial_dir).exists():
            # Nếu có path hiện tại, dùng nó
            current_path = self.output_path.get().strip()
            if current_path and Path(current_path).exists():
                initial_dir = current_path
        
        path = filedialog.askdirectory(
            title="Chọn thư mục output",
            initialdir=initial_dir if initial_dir and Path(initial_dir).exists() else None
        )
        if path:
            self.output_path.set(path)
            # Lưu thư mục đã chọn
            self.last_output_dir = str(Path(path))
            self.save_config()
            self.log(f"Đã chọn output: {path}")
    
    def log(self, message):
        """Thêm message vào log"""
        timestamp = datetime.now().strftime("%H:%M:%S")
        self.log_text.insert(tk.END, f"[{timestamp}] {message}\n")
        self.log_text.see(tk.END)
        self.root.update_idletasks()
    
    def clear_log(self):
        """Xóa log"""
        self.log_text.delete(1.0, tk.END)
    
    def update_progress(self, current, total, filename=""):
        """Cập nhật thanh tiến trình"""
        if total > 0:
            percent = int((current / total) * 100)
            self.progress_bar['value'] = percent
            self.percent_var.set(f"{percent}%")
            
            if filename:
                self.progress_var.set(f"Đang xử lý [{current}/{total}]: {filename}")
            else:
                self.progress_var.set(f"Đang xử lý [{current}/{total}]")
            
            self.stats_var.set(f"Đã xử lý: {current}/{total} file")
        else:
            self.progress_bar['value'] = 0
            self.percent_var.set("0%")
    
    def validate_inputs(self):
        """Kiểm tra input hợp lệ"""
        mode = self.mode_var.get()
        
        if mode == "multiple":
            # Kiểm tra danh sách file đã chọn
            if not self.selected_files:
                messagebox.showerror("Lỗi", "Vui lòng chọn ít nhất một file PDF!")
                return False
            # Kiểm tra tất cả file có tồn tại không
            for file_path in self.selected_files:
                if not Path(file_path).exists():
                    messagebox.showerror("Lỗi", f"File không tồn tại: {file_path}")
                    return False
                if not Path(file_path).is_file():
                    messagebox.showerror("Lỗi", f"Đường dẫn không phải file: {file_path}")
                    return False
        else:
            input_path = self.input_path.get().strip()
            if not input_path:
                messagebox.showerror("Lỗi", "Vui lòng chọn file/thư mục PDF!")
                return False
            
            path = Path(input_path)
            if not path.exists():
                messagebox.showerror("Lỗi", f"Đường dẫn không tồn tại: {input_path}")
                return False
            
            if mode == "file" and not path.is_file():
                messagebox.showerror("Lỗi", "Vui lòng chọn file PDF!")
                return False
            elif mode == "folder" and not path.is_dir():
                messagebox.showerror("Lỗi", "Vui lòng chọn thư mục!")
                return False
        
        # Kiểm tra output (không bắt buộc)
        output_path = self.output_path.get().strip()
        if output_path:
            output_dir = Path(output_path)
            if not output_dir.exists():
                try:
                    output_dir.mkdir(parents=True, exist_ok=True)
                    self.log(f"Đã tạo thư mục output: {output_path}")
                except Exception as e:
                    messagebox.showerror("Lỗi", f"Không thể tạo thư mục output: {e}")
                    return False
        
        return True
    
    def start_conversion(self):
        """Bắt đầu chuyển đổi"""
        if not self.validate_inputs():
            return
        
        if self.is_processing:
            messagebox.showwarning("Cảnh báo", "Đang xử lý, vui lòng đợi...")
            return
        
        # Reset
        self.stop_requested = False
        self.current_file_index = 0
        self.total_files = 0
        
        # Disable controls
        self.is_processing = True
        self.start_button.config(state=tk.DISABLED)
        self.stop_button.config(state=tk.NORMAL)
        self.progress_bar['value'] = 0
        self.percent_var.set("0%")
        self.status_var.set("Đang xử lý...")
        
        # Clear log
        self.clear_log()
        self.log("Bắt đầu chuyển đổi...")
        
        # Chạy trong thread riêng để không block GUI
        thread = threading.Thread(target=self.convert_thread, daemon=True)
        thread.start()
    
    def stop_conversion(self):
        """Dừng chuyển đổi"""
        if messagebox.askyesno("Xác nhận", "Bạn có chắc muốn dừng chuyển đổi?"):
            self.stop_requested = True
            self.is_processing = False
            self.log("Đã dừng chuyển đổi bởi người dùng")
            self.finish_conversion()
    
    def convert_thread(self):
        """Thread xử lý chuyển đổi"""
        success_count = 0
        error_count = 0
        
        try:
            input_path = self.input_path.get().strip()
            output_path = self.output_path.get().strip() or None
            recursive = self.recursive_var.get()
            mode = self.mode_var.get()
            
            # Tìm file PDF
            if mode == "file":
                # Xử lý file đơn
                pdf_files = [input_path]
                self.log(f"Xử lý file đơn: {Path(input_path).name}")
            elif mode == "multiple":
                # Xử lý nhiều file đã chọn
                pdf_files = self.selected_files
                self.log(f"Xử lý {len(pdf_files)} file đã chọn")
            else:  # mode == "folder"
                # Batch processing
                self.log(f"Đang tìm file PDF trong: {input_path}")
                pdf_files = find_pdf_files(input_path, recursive)
                self.log(f"Tìm thấy {len(pdf_files)} file PDF")
            
            if not pdf_files:
                self.root.after(0, lambda: messagebox.showerror(
                    "Lỗi", 
                    "Không tìm thấy file PDF nào!"
                ))
                self.root.after(0, self.finish_conversion)
                return
            
            # Cập nhật tổng số file
            self.total_files = len(pdf_files)
            self.root.after(0, lambda: self.update_progress(0, self.total_files))
            
            # Khởi tạo converter
            converter = PDFToMarkdownConverter(max_memory_pages=100)
            if output_path:
                converter._output_dir = output_path
            
            # Xử lý từng file
            for idx, pdf_file in enumerate(pdf_files, 1):
                if self.stop_requested or not self.is_processing:
                    self.log("Đã dừng xử lý")
                    break
                
                self.current_file_index = idx
                file_name = Path(pdf_file).name
                
                # Cập nhật progress
                self.root.after(0, lambda i=idx, t=self.total_files, f=file_name: 
                    self.update_progress(i-1, t, f)
                )
                
                try:
                    self.log(f"\n{'='*60}")
                    self.log(f"[{idx}/{self.total_files}] Xử lý: {file_name}")
                    self.log(f"{'='*60}")
                    
                    # Chuyển đổi
                    markdown, stats = converter.convert(
                        pdf_path=str(pdf_file),
                        output_path=None  # Sẽ tự động tạo trong output_dir
                    )
                    
                    success_count += 1
                    self.log(f"✓ Thành công!")
                    self.log(f"  - Output: {Path(stats['output_file']).name}")
                    self.log(f"  - Trang: {stats['pages']}, Blocks: {stats['blocks']}")
                    self.log(f"  - Đã khôi phục: {stats['restored_texts']} văn bản")
                    self.log(f"  - Kích thước: {stats['output_size']:,} ký tự")
                    
                except Exception as e:
                    error_count += 1
                    error_msg = str(e)
                    self.log(f"✗ Lỗi: {error_msg}")
                    import traceback
                    self.log(f"  Chi tiết: {traceback.format_exc()}")
                    continue
                
                # Cập nhật progress sau khi xử lý xong
                self.root.after(0, lambda i=idx, t=self.total_files, f=file_name: 
                    self.update_progress(i, t, f)
                )
            
            # Kết thúc
            self.root.after(0, lambda: self.finish_conversion_with_stats(
                success_count, error_count, self.total_files
            ))
            
        except Exception as e:
            self.log(f"Lỗi nghiêm trọng: {str(e)}")
            import traceback
            self.log(traceback.format_exc())
            self.root.after(0, lambda: messagebox.showerror("Lỗi", f"Lỗi: {str(e)}"))
            self.root.after(0, self.finish_conversion)
    
    def finish_conversion_with_stats(self, success, failed, total):
        """Kết thúc và hiển thị thống kê"""
        # Cập nhật progress 100%
        self.update_progress(total, total, "Hoàn thành")
        
        self.finish_conversion()
        
        self.log("\n" + "="*60)
        self.log("TỔNG KẾT")
        self.log("="*60)
        self.log(f"Tổng số file: {total}")
        self.log(f"Thành công: {success} ({success/total*100:.1f}%)" if total > 0 else "Thành công: 0")
        self.log(f"Thất bại: {failed} ({failed/total*100:.1f}%)" if total > 0 else "Thất bại: 0")
        self.log("="*60)
        
        messagebox.showinfo(
            "Hoàn thành",
            f"Đã xử lý xong!\n\n"
            f"Tổng số file: {total}\n"
            f"Thành công: {success}\n"
            f"Thất bại: {failed}"
        )
    
    def finish_conversion(self):
        """Kết thúc chuyển đổi"""
        self.is_processing = False
        self.start_button.config(state=tk.NORMAL)
        self.stop_button.config(state=tk.DISABLED)
        self.status_var.set("Hoàn thành")
    
    def on_closing(self):
        """Xử lý khi đóng cửa sổ"""
        if self.is_processing:
            if not messagebox.askyesno("Xác nhận", "Đang xử lý. Bạn có chắc muốn thoát?"):
                return
        
        self.root.destroy()


class GUIHandler(logging.Handler):
    """Custom logging handler để hiển thị log trong GUI"""
    
    def __init__(self, gui):
        super().__init__()
        self.gui = gui
    
    def emit(self, record):
        """Emit log message"""
        try:
            msg = self.format(record)
            self.gui.root.after(0, lambda m=msg: self.gui.log(m))
        except Exception:
            pass


def main():
    """Main function"""
    # Kiểm tra dependencies
    try:
        import fitz
    except ImportError:
        # Tạo root tạm để hiển thị messagebox
        root_temp = tk.Tk()
        root_temp.withdraw()  # Ẩn window chính
        messagebox.showerror(
            "Lỗi", 
            "Thiếu thư viện PyMuPDF!\n\n"
            "Vui lòng chạy: pip install -r requirements.txt"
        )
        root_temp.destroy()
        return
    
    root = tk.Tk()
    app = PDFConverterGUI(root)
    root.protocol("WM_DELETE_WINDOW", app.on_closing)
    root.mainloop()


if __name__ == "__main__":
    main()

