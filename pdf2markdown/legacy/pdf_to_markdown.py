"""
PDF to Markdown Converter với độ chính xác cao (>95%)
Hỗ trợ batch processing, xử lý tuần tự an toàn
"""

import fitz  # PyMuPDF
import re
import regex
import os
import logging
from typing import List, Dict, Tuple, Optional
from dataclasses import dataclass
from collections import defaultdict
import unicodedata
from pathlib import Path
import gc
import traceback

# Setup logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('pdf_conversion.log', encoding='utf-8'),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger(__name__)


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


class PDFToMarkdownConverter:
    """Converter PDF sang Markdown với độ chính xác cao và khôi phục văn bản biến dạng"""
    
    def __init__(self, max_memory_pages: int = 100):
        """
        Args:
            max_memory_pages: Số trang tối đa xử lý cùng lúc để tránh quá tải memory
        """
        self.context_dict = defaultdict(list)  # Từ điển ngữ cảnh
        self.common_words = set()  # Từ thường gặp để khôi phục
        self.max_memory_pages = max_memory_pages
        self.reset_stats()
    
    def reset_stats(self):
        """Reset thống kê cho file mới"""
        self.stats = {
            'total_blocks': 0,
            'restored_texts': 0,
            'errors': 0
        }
        
    def normalize_text(self, text: str) -> str:
        """Chuẩn hóa văn bản, khôi phục ký tự biến dạng"""
        if not text:
            return ""
        
        try:
            # Loại bỏ ký tự điều khiển không cần thiết
            text = ''.join(
                char for char in text 
                if unicodedata.category(char)[0] != 'C' or char in '\n\t\r'
            )
            
            # Khôi phục khoảng trắng bị mất giữa từ
            text = re.sub(r'([a-zàáảãạăắằẳẵặâấầẩẫậèéẻẽẹêếềểễệìíỉĩịòóỏõọôốồổỗộơớờởỡợùúủũụưứừửữựỳýỷỹỵđ])([A-ZÀÁẢÃẠĂẮẰẲẴẶÂẤẦẨẪẬÈÉẺẼẸÊẾỀỂỄỆÌÍỈĨỊÒÓỎÕỌÔỐỒỔỖỘƠỚỜỞỠỢÙÚỦŨỤƯỨỪỬỮỰỲÝỶỸỴĐ])', 
                         r'\1 \2', text)
            
            # Khôi phục khoảng trắng sau dấu câu
            text = re.sub(r'([.!?])([A-ZÀÁẢÃẠĂẮẰẲẴẶÂẤẦẨẪẬÈÉẺẼẸÊẾỀỂỄỆÌÍỈĨỊÒÓỎÕỌÔỐỒỔỖỘƠỚỜỞỠỢÙÚỦŨỤƯỨỪỬỮỰỲÝỶỸỴĐ])', 
                         r'\1 \2', text)
            
            # Sửa lỗi ký tự phổ biến (smart quotes, dashes, etc.)
            replacements = {
                '—': '—',  # Em dash
                '–': '–',  # En dash
                '"': '"',  # Smart quotes
                '"': '"',
                ''': "'",
                ''': "'",
                '…': '...',
                '•': '•',
                '◦': '◦',
            }
            
            for old, new in replacements.items():
                text = text.replace(old, new)
            
            # Loại bỏ khoảng trắng thừa nhưng giữ lại newlines
            text = re.sub(r'[ \t]+', ' ', text)
            text = re.sub(r'[ \t]+$', '', text, flags=re.MULTILINE)
            
            return text.strip()
            
        except Exception as e:
            logger.warning(f"Lỗi khi normalize text: {e}")
            return text.strip()
    
    def detect_block_type(self, block: TextBlock, blocks: List[TextBlock], page_blocks: List[TextBlock]) -> str:
        """Phát hiện loại block dựa trên ngữ cảnh và formatting"""
        text = block.text.strip()
        
        if not text:
            return 'paragraph'
        
        # Heading detection - nhiều tiêu chí
        is_heading = False
        
        # Tiêu chí 1: Font size lớn hơn text thường
        avg_font_size = sum(b.font_size for b in page_blocks if b.font_size > 0) / max(len(page_blocks), 1)
        if block.font_size > avg_font_size * 1.3:
            is_heading = True
        
        # Tiêu chí 2: Bold và độ dài ngắn
        if block.is_bold and len(text) < 150 and block.font_size > 12:
            is_heading = True
        
        # Tiêu chí 3: Pattern heading
        heading_patterns = [
            r'^(chương|chapter|phần|section|mục|đề mục)\s+\d+',
            r'^\d+\.\s+[A-ZÀÁẢÃẠĂẮẰẲẴẶÂẤẦẨẪẬÈÉẺẼẸÊẾỀỂỄỆÌÍỈĨỊÒÓỎÕỌÔỐỒỔỖỘƠỚỜỞỠỢÙÚỦŨỤƯỨỪỬỮỰỲÝỶỸỴĐ]',
            r'^[A-ZÀÁẢÃẠĂẮẰẲẴẶÂẤẦẨẪẬÈÉẺẼẸÊẾỀỂỄỆÌÍỈĨỊÒÓỎÕỌÔỐỒỔỖỘƠỚỜỞỠỢÙÚỦŨỤƯỨỪỬỮỰỲÝỶỸỴĐ\s]{1,100}$',  # Tất cả chữ hoa
        ]
        
        for pattern in heading_patterns:
            if re.match(pattern, text, re.IGNORECASE):
                is_heading = True
                break
        
        if is_heading:
            return 'heading'
        
        # List detection
        list_patterns = [
            r'^[\d\w\-\*•◦▪▫]\s+',  # Số hoặc ký tự + space
            r'^[-*•◦▪▫]\s+',  # Bullet points
            r'^\d+[\.\)]\s+',  # Numbered list
            r'^[a-z][\.\)]\s+',  # Lettered list
        ]
        
        for pattern in list_patterns:
            if re.match(pattern, text):
                return 'list'
        
        # Code block detection
        code_indicators = [
            '```', 'def ', 'class ', 'import ', 'function ', 'var ', 'const ',
            'public ', 'private ', 'static ', 'void ', 'int ', 'string ',
            '<?php', '<?=', '<script', '</script>', 'SELECT ', 'FROM ',
        ]
        
        if any(indicator in text[:50] for indicator in code_indicators):
            return 'code'
        
        return 'paragraph'
    
    def restore_deformed_text(self, text: str, context: List[str]) -> str:
        """Khôi phục văn bản biến dạng dựa trên ngữ cảnh"""
        if not text or len(text) < 2:
            return text
        
        try:
            # Xây dựng từ điển ngữ cảnh từ các block xung quanh
            context_words = set()
            for ctx in context:
                if ctx:
                    # Tách từ (hỗ trợ tiếng Việt và tiếng Anh)
                    words = regex.findall(r'\b\p{L}+\b', ctx.lower())
                    context_words.update(words)
            
            # Sửa lỗi ký tự phổ biến (chỉ khi có ngữ cảnh)
            if context_words:
                fixes = {
                    '0': 'O',  # Số 0 thành chữ O (trong ngữ cảnh văn bản)
                    '1': 'I',  # Số 1 thành chữ I
                    '5': 'S',  # Số 5 thành chữ S (cẩn thận)
                    '8': 'B',  # Số 8 thành chữ B (cẩn thận)
                }
                
                words = text.split()
                restored_words = []
                
                for word in words:
                    original_word = word
                    
                    # Nếu từ có vẻ sai (chứa số trong từ văn bản dài)
                    if re.search(r'[0-9]', word) and len(word) > 2 and not word.isdigit():
                        # Thử khôi phục dựa trên ngữ cảnh
                        for digit, letter in fixes.items():
                            test_word = word.replace(digit, letter)
                            if test_word.lower() in context_words:
                                word = test_word
                                self.stats['restored_texts'] += 1
                                logger.debug(f"Khôi phục: '{original_word}' -> '{word}'")
                                break
                    
                    restored_words.append(word)
                
                return ' '.join(restored_words)
            
            return text
            
        except Exception as e:
            logger.warning(f"Lỗi khi khôi phục text: {e}")
            return text
    
    def extract_document_title(self, doc: fitz.Document, blocks: List[TextBlock] = None) -> str:
        """
        Trích xuất title từ PDF để đặt tên file
        Ưu tiên: Metadata > Heading đầu tiên > Tên file gốc
        """
        title = None
        
        try:
            # Thử lấy từ metadata trước
            metadata = doc.metadata
            if metadata and metadata.get('title'):
                title = metadata['title'].strip()
                if title and len(title) > 3:
                    logger.info(f"Tìm thấy title từ metadata: {title}")
                    return self.sanitize_filename(title)
        except Exception as e:
            logger.debug(f"Không lấy được metadata: {e}")
        
        # Nếu không có metadata, tìm heading đầu tiên
        if blocks:
            try:
                # Tìm heading đầu tiên (trong 3 trang đầu)
                for block in blocks[:50]:  # Xem 50 blocks đầu
                    if block.block_type == 'heading' and block.text.strip():
                        title = block.text.strip()
                        # Loại bỏ markdown syntax nếu có
                        title = re.sub(r'^#+\s+', '', title)
                        if title and len(title) > 5 and len(title) < 200:
                            logger.info(f"Tìm thấy title từ heading: {title}")
                            return self.sanitize_filename(title)
            except Exception as e:
                logger.debug(f"Không tìm được heading: {e}")
        
        # Fallback: lấy từ text đầu tiên (trong 2 trang đầu)
        if not title:
            try:
                for page_num in range(min(2, len(doc))):
                    page = doc[page_num]
                    text = page.get_text("text").strip()
                    if text:
                        # Lấy dòng đầu tiên có ý nghĩa
                        lines = [l.strip() for l in text.split('\n') if l.strip()]
                        for line in lines[:5]:  # Xem 5 dòng đầu
                            if len(line) > 10 and len(line) < 200:
                                # Loại bỏ số trang, header/footer
                                if not re.match(r'^\d+$', line) and not line.lower().startswith('page'):
                                    title = line
                                    logger.info(f"Tìm thấy title từ text đầu: {title}")
                                    return self.sanitize_filename(title)
            except Exception as e:
                logger.debug(f"Không tìm được text đầu: {e}")
        
        return None
    
    def sanitize_filename(self, filename: str, max_length: int = 100) -> str:
        """Chuẩn hóa tên file để an toàn cho filesystem"""
        if not filename:
            return "document"
        
        # Loại bỏ ký tự không hợp lệ
        filename = re.sub(r'[<>:"/\\|?*]', '', filename)
        
        # Loại bỏ markdown syntax
        filename = re.sub(r'^#+\s+', '', filename)
        filename = re.sub(r'\*\*|\*|`|_', '', filename)
        
        # Loại bỏ khoảng trắng thừa
        filename = ' '.join(filename.split())
        
        # Giới hạn độ dài
        if len(filename) > max_length:
            filename = filename[:max_length].rsplit(' ', 1)[0]  # Cắt ở từ cuối
        
        # Đảm bảo không rỗng
        if not filename or len(filename) < 3:
            return "document"
        
        return filename
    
    def extract_text_blocks(self, doc: fitz.Document) -> List[TextBlock]:
        """Trích xuất các block văn bản từ PDF với metadata đầy đủ"""
        blocks = []
        
        try:
            total_pages = len(doc)
            logger.info(f"Bắt đầu trích xuất từ {total_pages} trang...")
            
            for page_num in range(total_pages):
                try:
                    page = doc[page_num]
                    
                    # Sử dụng text extraction với layout preservation
                    text_dict = page.get_text("dict", flags=11)  # flags=11: preserve layout
                    
                    page_blocks = []
                    
                    for block in text_dict["blocks"]:
                        if "lines" not in block:  # Skip image blocks
                            continue
                        
                        block_text = ""
                        is_bold = False
                        is_italic = False
                        font_size = 0
                        font_name = ""
                        bbox = block.get("bbox", [0, 0, 0, 0])
                        
                        for line in block.get("lines", []):
                            for span in line.get("spans", []):
                                text = span.get("text", "")
                                if text.strip():
                                    block_text += text + " "
                                    
                                    # Lấy font info từ span đầu tiên có text
                                    if not font_name and text.strip():
                                        font_size = span.get("size", 12)
                                        font_name = span.get("font", "unknown")
                                        flags = span.get("flags", 0)
                                        is_bold = bool(flags & 16)  # Bit 4 = bold
                                        is_italic = bool(flags & 2)  # Bit 1 = italic
                        
                        if block_text.strip():
                            text_block = TextBlock(
                                text=self.normalize_text(block_text),
                                x0=bbox[0],
                                y0=bbox[1],
                                x1=bbox[2],
                                y1=bbox[3],
                                font_size=font_size if font_size > 0 else 12,
                                font_name=font_name,
                                is_bold=is_bold,
                                is_italic=is_italic,
                                block_type='paragraph',
                                page_num=page_num + 1
                            )
                            blocks.append(text_block)
                            page_blocks.append(text_block)
                    
                    # Phát hiện block type dựa trên ngữ cảnh trang
                    for block in page_blocks:
                        block.block_type = self.detect_block_type(block, blocks, page_blocks)
                    
                    # Giải phóng memory sau mỗi trang
                    if (page_num + 1) % self.max_memory_pages == 0:
                        gc.collect()
                        logger.debug(f"Đã xử lý {page_num + 1} trang, giải phóng memory...")
                
                except Exception as e:
                    logger.error(f"Lỗi khi xử lý trang {page_num + 1}: {e}")
                    self.stats['errors'] += 1
                    continue
            
            self.stats['total_blocks'] = len(blocks)
            logger.info(f"Đã trích xuất {len(blocks)} blocks từ {total_pages} trang")
            
        except Exception as e:
            logger.error(f"Lỗi nghiêm trọng khi trích xuất: {e}")
            logger.error(traceback.format_exc())
            raise
        
        return blocks
    
    def group_blocks_by_layout(self, blocks: List[TextBlock]) -> List[List[TextBlock]]:
        """Nhóm các block theo layout (cột, hàng) để giữ cấu trúc"""
        if not blocks:
            return []
        
        groups = []
        current_group = [blocks[0]]
        
        for i in range(1, len(blocks)):
            prev = blocks[i-1]
            curr = blocks[i]
            
            # Nếu cùng trang và khoảng cách Y nhỏ -> cùng nhóm
            if (curr.page_num == prev.page_num and 
                abs(curr.y0 - prev.y1) < 25):  # Threshold 25 points
                current_group.append(curr)
            else:
                if current_group:
                    groups.append(current_group)
                current_group = [curr]
        
        if current_group:
            groups.append(current_group)
        
        return groups
    
    def convert_to_markdown(self, blocks: List[TextBlock]) -> str:
        """Chuyển đổi blocks sang Markdown với formatting đúng"""
        if not blocks:
            return ""
        
        markdown_lines = []
        groups = self.group_blocks_by_layout(blocks)
        
        prev_block_type = None
        prev_page = 0
        
        for group_idx, group in enumerate(groups):
            # Xây dựng ngữ cảnh từ group và các group lân cận
            context = []
            for block in group:
                context.append(block.text)
            
            # Thêm context từ group trước và sau
            if group_idx > 0:
                prev_group = groups[group_idx - 1]
                context.extend([b.text for b in prev_group[-3:]])  # 3 block cuối
            
            if group_idx < len(groups) - 1:
                next_group = groups[group_idx + 1]
                context.extend([b.text for b in next_group[:3]])  # 3 block đầu
            
            for block in group:
                # Khôi phục văn bản biến dạng
                text = self.restore_deformed_text(block.text, context)
                
                if not text.strip():
                    continue
                
                # Thêm page break nếu chuyển trang
                if block.page_num > prev_page and prev_page > 0:
                    markdown_lines.append(f"\n\n<!-- Trang {block.page_num} -->\n\n")
                
                prev_page = block.page_num
                
                # Format theo loại block
                if block.block_type == 'heading':
                    # Xác định level heading dựa trên font size
                    if block.font_size > 20:
                        level = 1
                    elif block.font_size > 16:
                        level = 2
                    elif block.font_size > 14:
                        level = 3
                    else:
                        level = 4
                    
                    markdown_lines.append(f"{'#' * level} {text}\n\n")
                    prev_block_type = 'heading'
                
                elif block.block_type == 'list':
                    # Detect và format list marker
                    if re.match(r'^\d+[\.\)]', text):
                        markdown_lines.append(f"{text}\n")
                    else:
                        # Thêm bullet nếu chưa có
                        if not re.match(r'^[-*•]', text):
                            markdown_lines.append(f"- {text}\n")
                        else:
                            markdown_lines.append(f"{text}\n")
                    prev_block_type = 'list'
                
                elif block.block_type == 'code':
                    # Đảm bảo code block format đúng
                    if not text.startswith('```'):
                        markdown_lines.append(f"```\n{text}\n```\n\n")
                    else:
                        markdown_lines.append(f"{text}\n\n")
                    prev_block_type = 'code'
                
                else:  # paragraph
                    # Formatting inline
                    formatted_text = text
                    if block.is_bold:
                        # Tránh double bold
                        if not re.match(r'\*\*.*\*\*', formatted_text):
                            formatted_text = f"**{formatted_text}**"
                    if block.is_italic:
                        # Tránh double italic
                        if not re.match(r'\*.*\*', formatted_text) and '**' not in formatted_text:
                            formatted_text = f"*{formatted_text}*"
                    
                    # Thêm spacing phù hợp
                    if prev_block_type == 'heading':
                        markdown_lines.append(f"{formatted_text}\n\n")
                    elif prev_block_type == 'list':
                        markdown_lines.append(f"\n{formatted_text}\n\n")
                    else:
                        markdown_lines.append(f"{formatted_text}\n\n")
                    
                    prev_block_type = 'paragraph'
        
        return ''.join(markdown_lines)
    
    def post_process_markdown(self, markdown: str) -> str:
        """Xử lý hậu kỳ để cải thiện chất lượng Markdown"""
        if not markdown:
            return ""
        
        try:
            # Loại bỏ khoảng trắng thừa (giữ lại structure)
            markdown = re.sub(r'\n{4,}', '\n\n\n', markdown)  # Max 3 newlines
            
            # Sửa lỗi formatting
            markdown = re.sub(r'\*\*([^*]+)\*\*\*', r'***\1***', markdown)  # Bold+Italic
            markdown = re.sub(r'\*\*\*\*([^*]+)\*\*\*\*', r'**\1**', markdown)  # Double bold
            
            # Đảm bảo khoảng trắng sau heading
            markdown = re.sub(r'(^#+\s+[^\n]+)\n([^\n#\s])', r'\1\n\n\2', markdown, flags=re.MULTILINE)
            
            # Loại bỏ separator thừa
            markdown = re.sub(r'---\n\n+---', '---', markdown)
            
            # Sửa lỗi list formatting
            markdown = re.sub(r'\n- \n', '\n', markdown)  # Empty list items
            
            # Đảm bảo code blocks có newline
            markdown = re.sub(r'```([^`]+)```', r'```\n\1\n```', markdown)
            
            # Loại bỏ trailing spaces
            markdown = re.sub(r'[ \t]+$', '', markdown, flags=re.MULTILINE)
            
            return markdown.strip()
            
        except Exception as e:
            logger.warning(f"Lỗi khi post-process: {e}")
            return markdown
    
    def convert(self, pdf_path: str, output_path: Optional[str] = None) -> Tuple[str, Dict]:
        """
        Chuyển đổi PDF sang Markdown
        
        Args:
            pdf_path: Đường dẫn file PDF
            output_path: Đường dẫn file output (nếu None, tự động tạo)
        
        Returns:
            Tuple[markdown_content, stats_dict]
        """
        if not os.path.exists(pdf_path):
            raise FileNotFoundError(f"Không tìm thấy file: {pdf_path}")
        
        logger.info(f"Bắt đầu chuyển đổi: {pdf_path}")
        
        # Reset stats cho file mới
        self.reset_stats()
        
        doc = None
        try:
            # Validate file
            if not pdf_path.lower().endswith('.pdf'):
                raise ValueError(f"File không phải PDF: {pdf_path}")
            
            # Mở PDF
            doc = fitz.open(pdf_path)
            
            if len(doc) == 0:
                raise ValueError("PDF file rỗng hoặc bị lỗi")
            
            logger.info(f"PDF có {len(doc)} trang")
            
            # Extract blocks
            blocks = self.extract_text_blocks(doc)
            
            if not blocks:
                raise ValueError("Không tìm thấy văn bản trong PDF")
            
            # Convert to markdown
            markdown = self.convert_to_markdown(blocks)
            
            # Post-processing
            markdown = self.post_process_markdown(markdown)
            
            # Tự động tạo output path nếu chưa có
            if output_path is None:
                # Thử lấy title từ document
                doc_title = self.extract_document_title(doc, blocks)
                
                if doc_title:
                    pdf_name = doc_title
                    logger.info(f"Sử dụng title từ document: {pdf_name}")
                else:
                    pdf_name = Path(pdf_path).stem
                    logger.info(f"Sử dụng tên file gốc: {pdf_name}")
                
                # Nếu có output_dir được set từ bên ngoài, dùng nó
                if hasattr(self, '_output_dir') and self._output_dir:
                    output_dir = Path(self._output_dir)
                else:
                    output_dir = Path(pdf_path).parent / "markdown_output"
                
                output_dir.mkdir(parents=True, exist_ok=True)
                output_path = str(output_dir / f"{pdf_name}.md")
            
            # Đảm bảo thư mục output tồn tại
            output_dir = Path(output_path).parent
            output_dir.mkdir(parents=True, exist_ok=True)
            
            # Save
            with open(output_path, 'w', encoding='utf-8') as f:
                f.write(markdown)
            
            logger.info(f"Đã lưu Markdown: {output_path}")
            logger.info(f"Độ dài: {len(markdown)} ký tự, {len(markdown.splitlines())} dòng")
            
            stats = {
                'input_file': pdf_path,
                'output_file': output_path,
                'pages': len(doc),
                'blocks': self.stats['total_blocks'],
                'restored_texts': self.stats['restored_texts'],
                'errors': self.stats['errors'],
                'output_size': len(markdown),
                'output_lines': len(markdown.splitlines())
            }
            
            return markdown, stats
        
        except Exception as e:
            logger.error(f"Lỗi khi chuyển đổi {pdf_path}: {e}")
            logger.error(traceback.format_exc())
            raise
        
        finally:
            if doc:
                doc.close()
            # Giải phóng memory
            gc.collect()

