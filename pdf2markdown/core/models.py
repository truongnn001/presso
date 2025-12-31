"""
Data models cho PDF to Markdown Converter
"""

from dataclasses import dataclass


@dataclass
class TextBlock:
    """Lưu trữ block văn bản với metadata đầy đủ"""
    text: str
    x0: float
    y0: float
    x1: float
    y1: float
    font_size: float
    font_name: str
    is_bold: bool
    is_italic: bool
    block_type: str  # 'paragraph', 'heading', 'list', 'table', 'code'
    page_num: int

