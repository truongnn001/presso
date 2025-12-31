"""
PressO Python Engine - PDF Handler
===================================

RESPONSIBILITY:
- Merge multiple PDFs into one
- Split PDF into individual pages or ranges
- Rotate PDF pages

ARCHITECTURAL ROLE:
- Stateless document processing
- NO business logic
- NO database access
- NO external network calls
- NO OCR (separate handler if needed)

Reference: PROJECT_DOCUMENTATION.md Section 4.3
"""

import os
import sys
import logging
import tempfile
import uuid
from datetime import datetime
from pathlib import Path
from typing import Optional, Dict, Any, List, Union

# pypdf for PDF processing
try:
    from pypdf import PdfReader, PdfWriter
    PYPDF_AVAILABLE = True
except ImportError:
    PYPDF_AVAILABLE = False

logger = logging.getLogger(__name__)


class PdfHandler:
    """
    Handles PDF document operations.
    
    Stateless handler - each request is independent.
    No business logic - just PDF manipulation.
    """
    
    # Default output directory (system temp)
    OUTPUT_DIR = Path(tempfile.gettempdir()) / "presso" / "pdf"
    
    def __init__(self):
        """Initialize PDF handler."""
        if not PYPDF_AVAILABLE:
            logger.error("pypdf not installed - PDF processing unavailable")
        
        # Ensure output directory exists
        self.OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
        
        logger.debug(f"PdfHandler initialized, output_dir={self.OUTPUT_DIR}")
    
    # =========================================================================
    # PDF Merge
    # =========================================================================
    
    def handle_pdf_merge(self, msg_id: Optional[str], params: Dict[str, Any]) -> Dict:
        """
        Handle PDF_MERGE request.
        
        Expected params:
        {
            "input_files": ["/path/to/file1.pdf", "/path/to/file2.pdf", ...],
            "output_filename": "merged.pdf" (optional)
        }
        
        Returns:
        {
            "id": "...",
            "success": true,
            "result": {
                "file_path": "/path/to/merged.pdf",
                "file_size": 12345,
                "page_count": 10
            }
        }
        """
        if not PYPDF_AVAILABLE:
            return self._make_error(msg_id, "DEPENDENCY_MISSING", 
                "pypdf library not installed")
        
        try:
            input_files = params.get("input_files", [])
            output_filename = params.get("output_filename")
            
            # Validate input
            if not input_files or len(input_files) < 2:
                return self._make_error(msg_id, "INVALID_INPUT",
                    "At least 2 input files are required for merge")
            
            # Validate files exist
            for file_path in input_files:
                if not Path(file_path).exists():
                    return self._make_error(msg_id, "FILE_NOT_FOUND",
                        f"Input file not found: {file_path}")
            
            logger.info(f"Merging {len(input_files)} PDF files")
            
            # Generate output filename if not provided
            if not output_filename:
                timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
                unique_id = str(uuid.uuid4())[:8]
                output_filename = f"merged_{timestamp}_{unique_id}.pdf"
            
            if not output_filename.endswith('.pdf'):
                output_filename += '.pdf'
            
            output_path = self.OUTPUT_DIR / output_filename
            
            # Merge PDFs
            writer = PdfWriter()
            total_pages = 0
            
            for file_path in input_files:
                reader = PdfReader(file_path)
                for page in reader.pages:
                    writer.add_page(page)
                    total_pages += 1
                logger.debug(f"Added {len(reader.pages)} pages from {file_path}")
            
            # Write output
            with open(output_path, 'wb') as output_file:
                writer.write(output_file)
            
            file_size = output_path.stat().st_size
            
            logger.info(f"PDF merge complete: {output_path} ({total_pages} pages, {file_size} bytes)")
            
            return {
                "id": msg_id,
                "success": True,
                "result": {
                    "file_path": str(output_path),
                    "file_name": output_filename,
                    "file_size": file_size,
                    "page_count": total_pages,
                    "input_count": len(input_files),
                    "timestamp": int(datetime.now().timestamp() * 1000)
                }
            }
            
        except Exception as e:
            logger.error(f"PDF merge failed: {e}")
            import traceback
            traceback.print_exc(file=sys.stderr)
            return self._make_error(msg_id, "MERGE_ERROR", str(e))
    
    # =========================================================================
    # PDF Split
    # =========================================================================
    
    def handle_pdf_split(self, msg_id: Optional[str], params: Dict[str, Any]) -> Dict:
        """
        Handle PDF_SPLIT request.
        
        Expected params:
        {
            "input_file": "/path/to/file.pdf",
            "split_mode": "all" | "range" | "single",
            "pages": [1, 2, 3] (for "range" or "single" mode),
            "output_prefix": "page_" (optional)
        }
        
        split_mode:
        - "all": Split into individual pages
        - "range": Extract specified pages into one file
        - "single": Extract each specified page into separate files
        
        Returns:
        {
            "id": "...",
            "success": true,
            "result": {
                "output_files": ["/path/to/page_1.pdf", ...],
                "page_count": 10
            }
        }
        """
        if not PYPDF_AVAILABLE:
            return self._make_error(msg_id, "DEPENDENCY_MISSING",
                "pypdf library not installed")
        
        try:
            input_file = params.get("input_file")
            split_mode = params.get("split_mode", "all")
            pages = params.get("pages", [])
            output_prefix = params.get("output_prefix", "page_")
            
            # Validate input
            if not input_file:
                return self._make_error(msg_id, "INVALID_INPUT",
                    "input_file is required")
            
            input_path = Path(input_file)
            if not input_path.exists():
                return self._make_error(msg_id, "FILE_NOT_FOUND",
                    f"Input file not found: {input_file}")
            
            logger.info(f"Splitting PDF: {input_file}, mode={split_mode}")
            
            reader = PdfReader(input_file)
            total_pages = len(reader.pages)
            
            # Generate unique session ID for output files
            session_id = str(uuid.uuid4())[:8]
            output_files = []
            
            if split_mode == "all":
                # Split into individual pages
                for i, page in enumerate(reader.pages):
                    writer = PdfWriter()
                    writer.add_page(page)
                    
                    output_filename = f"{output_prefix}{i+1}_{session_id}.pdf"
                    output_path = self.OUTPUT_DIR / output_filename
                    
                    with open(output_path, 'wb') as f:
                        writer.write(f)
                    
                    output_files.append(str(output_path))
                
                logger.debug(f"Split all: created {len(output_files)} files")
                
            elif split_mode == "range":
                # Extract specified pages into one file
                if not pages:
                    return self._make_error(msg_id, "INVALID_INPUT",
                        "pages list required for range mode")
                
                writer = PdfWriter()
                for page_num in pages:
                    if 1 <= page_num <= total_pages:
                        writer.add_page(reader.pages[page_num - 1])
                    else:
                        logger.warning(f"Page {page_num} out of range (1-{total_pages})")
                
                output_filename = f"{output_prefix}range_{session_id}.pdf"
                output_path = self.OUTPUT_DIR / output_filename
                
                with open(output_path, 'wb') as f:
                    writer.write(f)
                
                output_files.append(str(output_path))
                logger.debug(f"Split range: extracted {len(pages)} pages")
                
            elif split_mode == "single":
                # Extract each specified page into separate files
                if not pages:
                    return self._make_error(msg_id, "INVALID_INPUT",
                        "pages list required for single mode")
                
                for page_num in pages:
                    if 1 <= page_num <= total_pages:
                        writer = PdfWriter()
                        writer.add_page(reader.pages[page_num - 1])
                        
                        output_filename = f"{output_prefix}{page_num}_{session_id}.pdf"
                        output_path = self.OUTPUT_DIR / output_filename
                        
                        with open(output_path, 'wb') as f:
                            writer.write(f)
                        
                        output_files.append(str(output_path))
                    else:
                        logger.warning(f"Page {page_num} out of range (1-{total_pages})")
                
                logger.debug(f"Split single: created {len(output_files)} files")
            
            else:
                return self._make_error(msg_id, "INVALID_INPUT",
                    f"Unknown split_mode: {split_mode}")
            
            logger.info(f"PDF split complete: {len(output_files)} output files")
            
            return {
                "id": msg_id,
                "success": True,
                "result": {
                    "output_files": output_files,
                    "output_count": len(output_files),
                    "source_page_count": total_pages,
                    "split_mode": split_mode,
                    "timestamp": int(datetime.now().timestamp() * 1000)
                }
            }
            
        except Exception as e:
            logger.error(f"PDF split failed: {e}")
            import traceback
            traceback.print_exc(file=sys.stderr)
            return self._make_error(msg_id, "SPLIT_ERROR", str(e))
    
    # =========================================================================
    # PDF Rotate
    # =========================================================================
    
    def handle_pdf_rotate(self, msg_id: Optional[str], params: Dict[str, Any]) -> Dict:
        """
        Handle PDF_ROTATE request.
        
        Expected params:
        {
            "input_file": "/path/to/file.pdf",
            "rotation": 90 | 180 | 270 | -90,
            "pages": [1, 2, 3] (optional, all pages if not specified),
            "output_filename": "rotated.pdf" (optional)
        }
        
        Returns:
        {
            "id": "...",
            "success": true,
            "result": {
                "file_path": "/path/to/rotated.pdf",
                "file_size": 12345,
                "page_count": 10,
                "rotated_pages": [1, 2, 3]
            }
        }
        """
        if not PYPDF_AVAILABLE:
            return self._make_error(msg_id, "DEPENDENCY_MISSING",
                "pypdf library not installed")
        
        try:
            input_file = params.get("input_file")
            rotation = params.get("rotation", 90)
            pages = params.get("pages")  # None means all pages
            output_filename = params.get("output_filename")
            
            # Validate input
            if not input_file:
                return self._make_error(msg_id, "INVALID_INPUT",
                    "input_file is required")
            
            input_path = Path(input_file)
            if not input_path.exists():
                return self._make_error(msg_id, "FILE_NOT_FOUND",
                    f"Input file not found: {input_file}")
            
            # Validate rotation
            valid_rotations = [90, 180, 270, -90, -180, -270]
            if rotation not in valid_rotations:
                return self._make_error(msg_id, "INVALID_INPUT",
                    f"Rotation must be one of: {valid_rotations}")
            
            logger.info(f"Rotating PDF: {input_file}, rotation={rotation}")
            
            # Generate output filename if not provided
            if not output_filename:
                timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
                unique_id = str(uuid.uuid4())[:8]
                output_filename = f"rotated_{timestamp}_{unique_id}.pdf"
            
            if not output_filename.endswith('.pdf'):
                output_filename += '.pdf'
            
            output_path = self.OUTPUT_DIR / output_filename
            
            # Process PDF
            reader = PdfReader(input_file)
            writer = PdfWriter()
            total_pages = len(reader.pages)
            
            # Determine which pages to rotate
            if pages is None:
                pages_to_rotate = set(range(1, total_pages + 1))
            else:
                pages_to_rotate = set(pages)
            
            rotated_pages = []
            
            for i, page in enumerate(reader.pages):
                page_num = i + 1
                
                if page_num in pages_to_rotate:
                    page.rotate(rotation)
                    rotated_pages.append(page_num)
                
                writer.add_page(page)
            
            # Write output
            with open(output_path, 'wb') as f:
                writer.write(f)
            
            file_size = output_path.stat().st_size
            
            logger.info(f"PDF rotate complete: {output_path} (rotated {len(rotated_pages)} pages)")
            
            return {
                "id": msg_id,
                "success": True,
                "result": {
                    "file_path": str(output_path),
                    "file_name": output_filename,
                    "file_size": file_size,
                    "page_count": total_pages,
                    "rotation": rotation,
                    "rotated_pages": rotated_pages,
                    "timestamp": int(datetime.now().timestamp() * 1000)
                }
            }
            
        except Exception as e:
            logger.error(f"PDF rotate failed: {e}")
            import traceback
            traceback.print_exc(file=sys.stderr)
            return self._make_error(msg_id, "ROTATE_ERROR", str(e))
    
    # =========================================================================
    # Helpers
    # =========================================================================
    
    def _make_error(self, msg_id: Optional[str], code: str, message: str) -> Dict:
        """Create an error response."""
        return {
            "id": msg_id,
            "success": False,
            "error": {
                "code": code,
                "message": message
            }
        }
    
    @staticmethod
    def is_available() -> bool:
        """Check if PDF processing is available."""
        return PYPDF_AVAILABLE

