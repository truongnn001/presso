"""
PDF to Markdown Converter Module
Module chuyển đổi PDF sang Markdown với độ chính xác cao (>95%)

Cách sử dụng:
    from pdf2markdown import PDFToMarkdownConverter
    
    converter = PDFToMarkdownConverter()
    markdown, stats = converter.convert("input.pdf", "output.md")
"""

__version__ = "1.0.0"
__author__ = "PressO Team"

from pdf2markdown.core.converter import PDFToMarkdownConverter
from pdf2markdown.core.models import TextBlock
from pdf2markdown.utils.file_finder import find_pdf_files
from pdf2markdown.utils.batch_processor import batch_convert

__all__ = [
    'PDFToMarkdownConverter',
    'TextBlock',
    'find_pdf_files',
    'batch_convert',
]

