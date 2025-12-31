"""
CLI Entry Point - Command line interface cho PDF to Markdown Converter
"""

import os
import sys
import argparse
import logging
from pathlib import Path

from pdf2markdown.utils.batch_processor import batch_convert

# Setup logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('batch_conversion.log', encoding='utf-8'),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger(__name__)


def main():
    """Main function"""
    parser = argparse.ArgumentParser(
        description='Batch PDF to Markdown Converter - Chuyển đổi PDF sang Markdown với độ chính xác cao',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Ví dụ sử dụng:
  # Chuyển đổi 1 file
  python -m pdf2markdown.cli input.pdf
  
  # Chuyển đổi tất cả PDF trong thư mục
  python -m pdf2markdown.cli ./pdf_folder
  
  # Chuyển đổi với output directory tùy chỉnh
  python -m pdf2markdown.cli ./pdf_folder -o ./output
  
  # Chuyển đổi không đệ quy (chỉ thư mục hiện tại)
  python -m pdf2markdown.cli ./pdf_folder --no-recursive
  
  # Dừng khi gặp lỗi
  python -m pdf2markdown.cli ./pdf_folder --stop-on-error
        """
    )
    
    parser.add_argument(
        'input',
        type=str,
        help='Đường dẫn file PDF hoặc thư mục chứa PDF'
    )
    
    parser.add_argument(
        '-o', '--output',
        type=str,
        default=None,
        help='Thư mục output (mặc định: markdown_output trong cùng thư mục)'
    )
    
    parser.add_argument(
        '--no-recursive',
        action='store_true',
        help='Không tìm đệ quy trong subdirectories'
    )
    
    parser.add_argument(
        '--stop-on-error',
        action='store_true',
        help='Dừng xử lý khi gặp lỗi (mặc định: tiếp tục)'
    )
    
    parser.add_argument(
        '--max-memory-pages',
        type=int,
        default=100,
        help='Số trang tối đa xử lý cùng lúc để tránh quá tải memory (mặc định: 100)'
    )
    
    args = parser.parse_args()
    
    # Kiểm tra input path
    if not os.path.exists(args.input):
        logger.error(f"Đường dẫn không tồn tại: {args.input}")
        sys.exit(1)
    
    try:
        # Chạy batch conversion
        stats = batch_convert(
            input_path=args.input,
            output_dir=args.output,
            recursive=not args.no_recursive,
            continue_on_error=not args.stop_on_error,
            max_memory_pages=args.max_memory_pages
        )
        
        # Exit code
        if stats['failed'] > 0:
            sys.exit(1)
        else:
            sys.exit(0)
    
    except KeyboardInterrupt:
        logger.warning("\nĐã dừng bởi người dùng (Ctrl+C)")
        sys.exit(130)
    except Exception as e:
        logger.error(f"Lỗi nghiêm trọng: {e}")
        import traceback
        logger.error(traceback.format_exc())
        sys.exit(1)


if __name__ == "__main__":
    main()

