"""
File finder utilities - Tìm file PDF trong thư mục
"""

import logging
from pathlib import Path
from typing import List

logger = logging.getLogger(__name__)


def find_pdf_files(input_path: str, recursive: bool = True) -> List[str]:
    """
    Tìm tất cả file PDF trong thư mục
    
    Args:
        input_path: Đường dẫn file hoặc thư mục
        recursive: Tìm đệ quy trong subdirectories
    
    Returns:
        List đường dẫn file PDF
    """
    pdf_files = []
    path = Path(input_path)
    
    if path.is_file():
        if path.suffix.lower() == '.pdf':
            pdf_files.append(str(path))
        else:
            logger.warning(f"File không phải PDF: {input_path}")
    elif path.is_dir():
        pattern = "**/*.pdf" if recursive else "*.pdf"
        pdf_files = [str(p) for p in path.glob(pattern) if p.is_file()]
        logger.info(f"Tìm thấy {len(pdf_files)} file PDF trong {input_path}")
    else:
        logger.error(f"Đường dẫn không hợp lệ: {input_path}")
    
    return sorted(pdf_files)

