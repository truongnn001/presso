"""
Ví dụ sử dụng PDF to Markdown Converter
"""

from pdf2markdown import PDFToMarkdownConverter
import logging

# Setup logging để xem chi tiết
logging.basicConfig(level=logging.INFO)

# Khởi tạo converter
converter = PDFToMarkdownConverter(max_memory_pages=100)

# Ví dụ 1: Chuyển đổi 1 file
print("Ví dụ 1: Chuyển đổi 1 file PDF")
try:
    markdown, stats = converter.convert(
        pdf_path="example.pdf",
        output_path="example_output.md"
    )
    print(f"✓ Thành công!")
    print(f"  - Trang: {stats['pages']}")
    print(f"  - Blocks: {stats['blocks']}")
    print(f"  - Đã khôi phục: {stats['restored_texts']} văn bản")
    print(f"  - Output: {stats['output_file']}")
except FileNotFoundError:
    print("⚠ File example.pdf không tồn tại")
except Exception as e:
    print(f"✗ Lỗi: {e}")

# Ví dụ 2: Chuyển đổi với output tự động
print("\nVí dụ 2: Chuyển đổi với output tự động")
try:
    markdown, stats = converter.convert(
        pdf_path="example.pdf"
        # output_path=None -> tự động tạo trong markdown_output/
    )
    print(f"✓ Output tự động: {stats['output_file']}")
except FileNotFoundError:
    print("⚠ File example.pdf không tồn tại")

# Ví dụ 3: Chỉ lấy markdown content (không lưu file)
print("\nVí dụ 3: Chỉ lấy content")
try:
    markdown, stats = converter.convert(
        pdf_path="example.pdf",
        output_path=None  # Không lưu file
    )
    print(f"✓ Đã lấy {len(markdown)} ký tự markdown")
    print(f"  Preview (100 ký tự đầu): {markdown[:100]}...")
except FileNotFoundError:
    print("⚠ File example.pdf không tồn tại")

print("\n" + "="*60)
print("Để chuyển đổi batch, sử dụng:")
print("  python -m pdf2markdown.cli <input_path>")
print("  hoặc: python main.py <input_path>")
print("="*60)

