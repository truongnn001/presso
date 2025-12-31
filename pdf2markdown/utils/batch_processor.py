"""
Batch processor - Xử lý nhiều file PDF cùng lúc
"""

import logging
import json
import sys
import gc
from pathlib import Path
from typing import Dict
from datetime import datetime
from tqdm import tqdm

from pdf2markdown.core.converter import PDFToMarkdownConverter
from pdf2markdown.utils.file_finder import find_pdf_files

logger = logging.getLogger(__name__)


def batch_convert(
    input_path: str,
    output_dir: str = None,
    recursive: bool = True,
    continue_on_error: bool = True,
    max_memory_pages: int = 100
) -> Dict:
    """
    Chuyển đổi batch PDF sang Markdown
    
    Args:
        input_path: Đường dẫn file PDF hoặc thư mục chứa PDF
        output_dir: Thư mục output (None = tự động tạo)
        recursive: Tìm đệ quy trong subdirectories
        continue_on_error: Tiếp tục khi gặp lỗi
        max_memory_pages: Số trang tối đa xử lý cùng lúc
    
    Returns:
        Dict thống kê kết quả
    """
    # Tìm tất cả file PDF
    pdf_files = find_pdf_files(input_path, recursive)
    
    if not pdf_files:
        logger.error("Không tìm thấy file PDF nào!")
        return {
            'total': 0,
            'success': 0,
            'failed': 0,
            'results': []
        }
    
    logger.info(f"Bắt đầu chuyển đổi {len(pdf_files)} file PDF...")
    
    # Tạo output directory
    if output_dir is None:
        input_path_obj = Path(input_path)
        if input_path_obj.is_file():
            output_dir = str(input_path_obj.parent / "markdown_output")
        else:
            output_dir = str(input_path_obj / "markdown_output")
    
    output_path_obj = Path(output_dir)
    output_path_obj.mkdir(parents=True, exist_ok=True)
    logger.info(f"Thư mục output: {output_dir}")
    
    # Khởi tạo converter
    converter = PDFToMarkdownConverter(max_memory_pages=max_memory_pages)
    converter._output_dir = str(output_path_obj)
    
    # Thống kê
    stats = {
        'total': len(pdf_files),
        'success': 0,
        'failed': 0,
        'results': [],
        'start_time': datetime.now().isoformat(),
        'errors': []
    }
    
    # Xử lý từng file tuần tự
    for pdf_file in tqdm(pdf_files, desc="Chuyển đổi PDF", unit="file"):
        try:
            logger.info(f"\n{'='*60}")
            logger.info(f"Đang xử lý: {pdf_file}")
            logger.info(f"{'='*60}")
            
            # Chuyển đổi (converter sẽ tự động tạo output path)
            markdown, file_stats = converter.convert(
                pdf_path=str(pdf_file),
                output_path=None  # Tự động tạo
            )
            
            # Cập nhật thống kê
            stats['success'] += 1
            stats['results'].append({
                'input': pdf_file,
                'output': file_stats['output_file'],
                'status': 'success',
                'stats': file_stats
            })
            
            logger.info(f"✓ Thành công: {file_stats['output_file']}")
            logger.info(f"  - Trang: {file_stats['pages']}")
            logger.info(f"  - Blocks: {file_stats['blocks']}")
            logger.info(f"  - Đã khôi phục: {file_stats['restored_texts']} văn bản")
            logger.info(f"  - Kích thước: {file_stats['output_size']} ký tự")
            
            # Giải phóng memory sau mỗi file
            gc.collect()
        
        except Exception as e:
            error_msg = f"Lỗi khi xử lý {pdf_file}: {str(e)}"
            logger.error(error_msg)
            logger.error(f"Traceback: {sys.exc_info()[2]}")
            
            stats['failed'] += 1
            stats['errors'].append({
                'file': pdf_file,
                'error': str(e),
                'type': type(e).__name__
            })
            
            stats['results'].append({
                'input': pdf_file,
                'output': None,
                'status': 'failed',
                'error': str(e)
            })
            
            if not continue_on_error:
                logger.error("Dừng xử lý do lỗi (continue_on_error=False)")
                break
    
    # Kết thúc
    stats['end_time'] = datetime.now().isoformat()
    stats['duration'] = str(datetime.fromisoformat(stats['end_time']) - 
                           datetime.fromisoformat(stats['start_time']))
    
    # Lưu thống kê
    stats_file = output_path_obj / "conversion_stats.json"
    with open(stats_file, 'w', encoding='utf-8') as f:
        json.dump(stats, f, ensure_ascii=False, indent=2)
    
    # In tổng kết
    logger.info(f"\n{'='*60}")
    logger.info("TỔNG KẾT")
    logger.info(f"{'='*60}")
    logger.info(f"Tổng số file: {stats['total']}")
    if stats['total'] > 0:
        logger.info(f"Thành công: {stats['success']} ({stats['success']/stats['total']*100:.1f}%)")
        logger.info(f"Thất bại: {stats['failed']} ({stats['failed']/stats['total']*100:.1f}%)")
    logger.info(f"Thời gian: {stats['duration']}")
    logger.info(f"Thống kê chi tiết: {stats_file}")
    
    if stats['errors']:
        logger.warning(f"\nCác file lỗi:")
        for err in stats['errors']:
            logger.warning(f"  - {err['file']}: {err['error']}")
    
    return stats

