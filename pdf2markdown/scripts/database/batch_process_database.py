"""
Batch processing script để xử lý tất cả PDF trong thư mục Database
Xử lý từng file một cách cẩn thận, không bỏ sót
"""

import os
import sys
from pathlib import Path
import time
import logging
from pdf2markdown import PDFToMarkdownConverter

# Setup logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('batch_database_processing.log', encoding='utf-8'),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger(__name__)


def process_database_pdfs():
    """Xử lý tất cả PDF trong thư mục Database"""
    
    # Đường dẫn thư mục nguồn và đích
    source_dir = Path(r"D:\OneDrive\Bao Ngan Ltd\PressUp Agency\Source-code-websites\Tai lieu Pressup\Nghien cuu\Database")
    output_dir = Path(r"D:\Document\Database")
    
    # Kiểm tra thư mục nguồn
    if not source_dir.exists():
        logger.error(f"Thư mục nguồn không tồn tại: {source_dir}")
        return
    
    # Tạo thư mục đích nếu chưa có
    output_dir.mkdir(parents=True, exist_ok=True)
    logger.info(f"Thư mục đích: {output_dir}")
    
    # Tìm tất cả file PDF
    pdf_files = list(source_dir.glob("*.pdf"))
    
    if not pdf_files:
        logger.warning(f"Không tìm thấy file PDF nào trong {source_dir}")
        return
    
    logger.info(f"Tìm thấy {len(pdf_files)} file PDF cần xử lý")
    
    # Khởi tạo converter
    converter = PDFToMarkdownConverter(max_memory_pages=50)  # Giảm memory để an toàn hơn
    converter._output_dir = str(output_dir)  # Set output directory
    
    # Xử lý từng file
    success_count = 0
    error_count = 0
    processed_files = []
    
    for idx, pdf_file in enumerate(pdf_files, 1):
        logger.info("=" * 80)
        logger.info(f"[{idx}/{len(pdf_files)}] Bắt đầu xử lý: {pdf_file.name}")
        logger.info("=" * 80)
        
        try:
            # Xử lý file
            start_time = time.time()
            
            markdown, stats = converter.convert(str(pdf_file))
            
            elapsed_time = time.time() - start_time
            
            logger.info(f"✓ Hoàn thành: {pdf_file.name}")
            logger.info(f"  - Thời gian: {elapsed_time:.2f} giây")
            logger.info(f"  - Số trang: {stats['pages']}")
            logger.info(f"  - Blocks: {stats['blocks']}")
            logger.info(f"  - File output: {stats['output_file']}")
            logger.info(f"  - Kích thước: {stats['output_size']:,} ký tự")
            
            success_count += 1
            processed_files.append({
                'input': pdf_file.name,
                'output': Path(stats['output_file']).name,
                'status': 'success',
                'pages': stats['pages']
            })
            
            # Nghỉ một chút giữa các file để tránh quá tải
            if idx < len(pdf_files):
                logger.info("Nghỉ 2 giây trước khi xử lý file tiếp theo...")
                time.sleep(2)
            
        except Exception as e:
            error_count += 1
            logger.error(f"✗ LỖI khi xử lý {pdf_file.name}: {e}")
            logger.error(f"  Chi tiết lỗi: {str(e)}")
            
            processed_files.append({
                'input': pdf_file.name,
                'output': None,
                'status': 'error',
                'error': str(e)
            })
            
            # Tiếp tục với file tiếp theo
            continue
    
    # Tóm tắt kết quả
    logger.info("=" * 80)
    logger.info("TÓM TẮT KẾT QUẢ")
    logger.info("=" * 80)
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
    
    logger.info("=" * 80)
    logger.info("Hoàn thành xử lý batch!")


if __name__ == "__main__":
    try:
        process_database_pdfs()
    except KeyboardInterrupt:
        logger.warning("Người dùng dừng chương trình")
        sys.exit(1)
    except Exception as e:
        logger.error(f"Lỗi nghiêm trọng: {e}")
        import traceback
        logger.error(traceback.format_exc())
        sys.exit(1)

