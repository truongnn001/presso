"""
Script chuyển đổi batch PDF trong thư mục Database
Xử lý từng file, chậm nhưng chắc chắn
Tự động đặt tên file markdown theo ngữ cảnh tài liệu

Cách chạy:
    python convert_database.py
"""

import os
import sys
import logging
import time
import gc
from pathlib import Path
from pdf2markdown import PDFToMarkdownConverter

# Setup logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('database_conversion.log', encoding='utf-8'),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger(__name__)

# Đường dẫn
INPUT_DIR = r"D:\OneDrive\Bao Ngan Ltd\PressUp Agency\Source-code-websites\Tai lieu Pressup\Nghien cuu\Database"
OUTPUT_DIR = r"D:\Document\Database"

def main():
    """Chạy batch conversion cho thư mục Database"""
    
    # Kiểm tra thư mục input
    if not os.path.exists(INPUT_DIR):
        logger.error(f"Thư mục input không tồn tại: {INPUT_DIR}")
        sys.exit(1)
    
    # Tạo thư mục output nếu chưa có
    output_path = Path(OUTPUT_DIR)
    output_path.mkdir(parents=True, exist_ok=True)
    logger.info(f"Thư mục output: {OUTPUT_DIR}")
    
    # Tìm tất cả file PDF
    input_path = Path(INPUT_DIR)
    pdf_files = list(input_path.glob("*.pdf"))
    
    if not pdf_files:
        logger.warning(f"Không tìm thấy file PDF nào trong {INPUT_DIR}")
        return 0
    
    logger.info("="*80)
    logger.info("BẮT ĐẦU CHUYỂN ĐỔI BATCH PDF -> MARKDOWN")
    logger.info("="*80)
    logger.info(f"Input: {INPUT_DIR}")
    logger.info(f"Output: {OUTPUT_DIR}")
    logger.info(f"Tìm thấy {len(pdf_files)} file PDF")
    logger.info("="*80)
    
    # Khởi tạo converter
    converter = PDFToMarkdownConverter(max_memory_pages=50)  # Giảm memory để an toàn
    converter._output_dir = str(output_path)  # Set output directory
    
    success_count = 0
    error_count = 0
    processed_files = []
    
    try:
        # Xử lý từng file một cách cẩn thận
        for idx, pdf_file in enumerate(pdf_files, 1):
            logger.info("")
            logger.info("="*80)
            logger.info(f"[{idx}/{len(pdf_files)}] Đang xử lý: {pdf_file.name}")
            logger.info("="*80)
            
            try:
                start_time = time.time()
                
                # Chuyển đổi - converter sẽ tự động extract title và đặt tên file
                markdown, stats = converter.convert(str(pdf_file))
                
                elapsed_time = time.time() - start_time
                
                logger.info(f"✓ Thành công: {pdf_file.name}")
                logger.info(f"  - Thời gian: {elapsed_time:.2f} giây")
                logger.info(f"  - Số trang: {stats['pages']}")
                logger.info(f"  - Blocks: {stats['blocks']}")
                logger.info(f"  - File output: {Path(stats['output_file']).name}")
                logger.info(f"  - Kích thước: {stats['output_size']:,} ký tự")
                logger.info(f"  - Đã khôi phục: {stats['restored_texts']} văn bản")
                
                success_count += 1
                processed_files.append({
                    'input': pdf_file.name,
                    'output': Path(stats['output_file']).name,
                    'status': 'success',
                    'pages': stats['pages']
                })
                
                # Giải phóng memory sau mỗi file
                gc.collect()
                
                # Nghỉ một chút giữa các file để tránh quá tải
                if idx < len(pdf_files):
                    logger.info("Nghỉ 2 giây trước khi xử lý file tiếp theo...")
                    time.sleep(2)
                
            except Exception as e:
                error_count += 1
                logger.error(f"✗ LỖI khi xử lý {pdf_file.name}: {e}")
                import traceback
                logger.error(f"Chi tiết lỗi:\n{traceback.format_exc()}")
                
                processed_files.append({
                    'input': pdf_file.name,
                    'output': None,
                    'status': 'error',
                    'error': str(e)
                })
                
                # Tiếp tục với file tiếp theo
                gc.collect()
                continue
        
        # Tóm tắt kết quả
        logger.info("")
        logger.info("="*80)
        logger.info("TÓM TẮT KẾT QUẢ")
        logger.info("="*80)
        logger.info(f"Tổng số file: {len(pdf_files)}")
        logger.info(f"Thành công: {success_count}")
        logger.info(f"Lỗi: {error_count}")
        logger.info("")
        logger.info("Chi tiết từng file:")
        for item in processed_files:
            if item['status'] == 'success':
                logger.info(f"  ✓ {item['input']} -> {item['output']} ({item['pages']} trang)")
            else:
                logger.info(f"  ✗ {item['input']} -> LỖI: {item.get('error', 'Unknown')}")
        logger.info("="*80)
        
        return 0 if error_count == 0 else 1
        
    except KeyboardInterrupt:
        logger.warning("\nĐã dừng bởi người dùng (Ctrl+C)")
        return 130
    except Exception as e:
        logger.error(f"Lỗi nghiêm trọng: {e}")
        import traceback
        logger.error(traceback.format_exc())
        return 1

if __name__ == "__main__":
    sys.exit(main())

