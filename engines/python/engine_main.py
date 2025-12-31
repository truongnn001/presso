#!/usr/bin/env python3
"""
PressO Desktop - Python Engine
==============================

RESPONSIBILITY:
- Document processing (PDF, Excel, Image)
- OCR and text extraction (Phase 2+)
- AI/LLM integration (Phase 6)
- Stateless message processing

ARCHITECTURAL ROLE:
- Subprocess spawned by Java Kernel
- Communicates via stdin/stdout (JSON)
- NO direct database access
- NO direct filesystem access beyond temp and provided paths
- NO external network calls (routes through Go API Hub)

COMMUNICATION PROTOCOL:
- Read JSON messages from stdin (one per line)
- Write JSON responses to stdout (one per line)
- stderr for logging only

Reference: PROJECT_DOCUMENTATION.md Section 4.3
"""

import sys
import json
import logging
import traceback
from datetime import datetime
from typing import Optional, Any, Dict

# Configure logging to stderr (stdout is for IPC)
logging.basicConfig(
    level=logging.DEBUG,
    format='[Python] %(levelname)s: %(message)s',
    stream=sys.stderr
)
logger = logging.getLogger(__name__)

# Import handlers
EXCEL_AVAILABLE = False
PDF_AVAILABLE = False
IMAGE_AVAILABLE = False
TEMPLATE_AVAILABLE = False

try:
    from handlers import ExcelHandler
    EXCEL_AVAILABLE = True
    logger.info("Excel handler loaded")
except ImportError as e:
    logger.warning(f"Excel handler not available: {e}")

try:
    from handlers import PdfHandler
    PDF_AVAILABLE = PdfHandler.is_available()
    if PDF_AVAILABLE:
        logger.info("PDF handler loaded")
    else:
        logger.warning("PDF handler loaded but pypdf not installed")
except ImportError as e:
    logger.warning(f"PDF handler not available: {e}")

try:
    from handlers import ImageHandler
    IMAGE_AVAILABLE = ImageHandler.is_available()
    if IMAGE_AVAILABLE:
        logger.info("Image handler loaded")
    else:
        logger.warning("Image handler loaded but Pillow not installed")
except ImportError as e:
    logger.warning(f"Image handler not available: {e}")

try:
    from handlers import TemplateHandler
    TEMPLATE_AVAILABLE = TemplateHandler.is_available()
    if TEMPLATE_AVAILABLE:
        logger.info("Template handler loaded")
    else:
        logger.warning("Template handler loaded but TemplateManager not available")
except ImportError as e:
    logger.warning(f"Template handler not available: {e}")


class PythonEngine:
    """
    Main Python Engine class.
    Handles message processing loop and command dispatch.
    """
    
    def __init__(self):
        self.running = True
        self.version = "0.5.0"  # Updated for Phase 2 Step 5
        self.start_time = datetime.now()
        
        # Initialize handlers
        self.excel_handler = ExcelHandler() if EXCEL_AVAILABLE else None
        self.pdf_handler = PdfHandler() if PDF_AVAILABLE else None
        self.image_handler = ImageHandler() if IMAGE_AVAILABLE else None
        self.template_handler = TemplateHandler() if TEMPLATE_AVAILABLE else None
        
        logger.info("Python Engine initialized")
    
    def run(self):
        """Main processing loop - read messages from stdin, process, respond."""
        logger.info("Python Engine starting...")
        
        # Signal ready to kernel
        self._send_ready()
        
        # Main message loop
        while self.running:
            try:
                # Read line from stdin (blocking)
                line = sys.stdin.readline()
                
                if not line:
                    # EOF - kernel closed stdin
                    logger.info("stdin closed, shutting down")
                    break
                
                line = line.strip()
                if not line:
                    continue
                
                # Process message
                self._process_message(line)
                
            except KeyboardInterrupt:
                logger.info("Keyboard interrupt, shutting down")
                break
            except Exception as e:
                logger.error(f"Error in main loop: {e}")
                traceback.print_exc(file=sys.stderr)
        
        logger.info("Python Engine stopped")
    
    def _send_ready(self):
        """Send READY signal to kernel."""
        capabilities = ["PING", "SHUTDOWN", "HEALTH_CHECK", "GET_STATUS"]
        
        if EXCEL_AVAILABLE:
            capabilities.append("EXPORT_EXCEL")
        
        if PDF_AVAILABLE:
            capabilities.extend(["PDF_MERGE", "PDF_SPLIT", "PDF_ROTATE"])
        
        if IMAGE_AVAILABLE:
            capabilities.extend(["IMAGE_CONVERT", "IMAGE_COMPRESS", "IMAGE_RESIZE"])
        
        if TEMPLATE_AVAILABLE:
            capabilities.extend(["LIST_TEMPLATES", "LOAD_TEMPLATE", "GET_TEMPLATE_PATH"])
        
        ready_msg = {
            "type": "READY",
            "engine": "python",
            "version": self.version,
            "capabilities": capabilities,
            "timestamp": int(datetime.now().timestamp() * 1000)
        }
        self._send_response(ready_msg)
        logger.info(f"READY signal sent, capabilities: {capabilities}")
    
    def _process_message(self, line: str):
        """Process a single message line."""
        logger.debug(f"Received: {line}")
        
        try:
            message = json.loads(line)
        except json.JSONDecodeError as e:
            logger.error(f"Invalid JSON: {e}")
            self._send_error(None, "PARSE_ERROR", f"Invalid JSON: {e}")
            return
        
        # Extract message fields
        msg_id = message.get("id")
        msg_type = message.get("type") or message.get("method")
        params = message.get("params") or message.get("payload") or {}
        
        if not msg_type:
            self._send_error(msg_id, "MISSING_TYPE", "Message type not specified")
            return
        
        # Dispatch to handler
        try:
            response = self._dispatch(msg_type, params, msg_id)
            
            if response is not None:
                self._send_response(response)
                
        except Exception as e:
            logger.error(f"Error processing {msg_type}: {e}")
            traceback.print_exc(file=sys.stderr)
            self._send_error(msg_id, "PROCESSING_ERROR", str(e))
    
    def _dispatch(self, msg_type: str, params: dict, msg_id: Optional[str]) -> Optional[dict]:
        """Dispatch message to appropriate handler."""
        msg_type = msg_type.upper()
        
        # Built-in handlers
        handlers = {
            "PING": self._handle_ping,
            "SHUTDOWN": self._handle_shutdown,
            "HEALTH_CHECK": self._handle_health_check,
            "GET_STATUS": self._handle_status,
        }
        
        # Check built-in handlers first
        handler = handlers.get(msg_type)
        if handler:
            return handler(msg_id, params)
        
        # Excel handlers
        if msg_type == "EXPORT_EXCEL":
            return self._handle_export_excel(msg_id, params)
        
        # PDF handlers
        if msg_type == "PDF_MERGE":
            return self._handle_pdf_merge(msg_id, params)
        
        if msg_type == "PDF_SPLIT":
            return self._handle_pdf_split(msg_id, params)
        
        if msg_type == "PDF_ROTATE":
            return self._handle_pdf_rotate(msg_id, params)
        
        # Image handlers
        if msg_type == "IMAGE_CONVERT":
            return self._handle_image_convert(msg_id, params)
        
        if msg_type == "IMAGE_COMPRESS":
            return self._handle_image_compress(msg_id, params)
        
        if msg_type == "IMAGE_RESIZE":
            return self._handle_image_resize(msg_id, params)
        
        # Template handlers
        if msg_type == "LIST_TEMPLATES":
            return self._handle_list_templates(msg_id, params)
        
        if msg_type == "LOAD_TEMPLATE":
            return self._handle_load_template(msg_id, params)
        
        if msg_type == "GET_TEMPLATE_PATH":
            return self._handle_get_template_path(msg_id, params)
        
        # TODO (Phase 2+): Add more handlers
        # - EXPORT_PDF
        # - PDF_WATERMARK
        
        # Unknown message type
        logger.warning(f"Unknown message type: {msg_type}")
        return self._make_error_response(msg_id, "UNKNOWN_TYPE", 
            f"Unknown message type: {msg_type}")
    
    # =========================================================================
    # Built-in Handlers
    # =========================================================================
    
    def _handle_ping(self, msg_id: Optional[str], params: dict) -> dict:
        """Handle PING message - respond with PONG."""
        logger.info("PING received, sending PONG")
        
        return {
            "id": msg_id,
            "success": True,
            "result": {
                "message": "PONG",
                "engine": "Python Engine",
                "version": self.version,
                "python": f"{sys.version_info.major}.{sys.version_info.minor}.{sys.version_info.micro}",
                "excel_available": EXCEL_AVAILABLE,
                "pdf_available": PDF_AVAILABLE,
                "image_available": IMAGE_AVAILABLE,
                "template_available": TEMPLATE_AVAILABLE,
                "timestamp": int(datetime.now().timestamp() * 1000)
            }
        }
    
    def _handle_shutdown(self, msg_id: Optional[str], params: dict) -> dict:
        """Handle SHUTDOWN message - initiate graceful shutdown."""
        logger.info("SHUTDOWN received, stopping engine")
        self.running = False
        
        return {
            "id": msg_id,
            "success": True,
            "result": {
                "message": "Shutdown initiated",
                "timestamp": int(datetime.now().timestamp() * 1000)
            }
        }
    
    def _handle_health_check(self, msg_id: Optional[str], params: dict) -> dict:
        """Handle HEALTH_CHECK message."""
        uptime = (datetime.now() - self.start_time).total_seconds()
        
        return {
            "id": msg_id,
            "success": True,
            "result": {
                "healthy": True,
                "uptime_seconds": uptime,
                "version": self.version,
                "excel_available": EXCEL_AVAILABLE,
                "pdf_available": PDF_AVAILABLE,
                "image_available": IMAGE_AVAILABLE
            }
        }
    
    def _handle_status(self, msg_id: Optional[str], params: dict) -> dict:
        """Handle GET_STATUS message."""
        uptime = (datetime.now() - self.start_time).total_seconds()
        
        return {
            "id": msg_id,
            "success": True,
            "result": {
                "status": "running",
                "engine": "python",
                "version": self.version,
                "python_version": f"{sys.version_info.major}.{sys.version_info.minor}.{sys.version_info.micro}",
                "uptime_seconds": uptime,
                "capabilities": {
                    "excel": EXCEL_AVAILABLE,
                    "pdf": PDF_AVAILABLE,
                    "image": IMAGE_AVAILABLE,
                    "template": TEMPLATE_AVAILABLE,
                    "ocr": False,    # TODO: Phase 2+
                },
                "timestamp": int(datetime.now().timestamp() * 1000)
            }
        }
    
    # =========================================================================
    # Excel Handlers
    # =========================================================================
    
    def _handle_export_excel(self, msg_id: Optional[str], params: dict) -> dict:
        """Handle EXPORT_EXCEL message."""
        if not EXCEL_AVAILABLE or self.excel_handler is None:
            return self._make_error_response(msg_id, "EXCEL_UNAVAILABLE",
                "Excel generation is not available (openpyxl not installed)")
        
        logger.info("EXPORT_EXCEL request received")
        return self.excel_handler.handle_export_excel(msg_id, params)
    
    # =========================================================================
    # PDF Handlers
    # =========================================================================
    
    def _handle_pdf_merge(self, msg_id: Optional[str], params: dict) -> dict:
        """Handle PDF_MERGE message."""
        if not PDF_AVAILABLE or self.pdf_handler is None:
            return self._make_error_response(msg_id, "PDF_UNAVAILABLE",
                "PDF processing is not available (pypdf not installed)")
        
        logger.info("PDF_MERGE request received")
        return self.pdf_handler.handle_pdf_merge(msg_id, params)
    
    def _handle_pdf_split(self, msg_id: Optional[str], params: dict) -> dict:
        """Handle PDF_SPLIT message."""
        if not PDF_AVAILABLE or self.pdf_handler is None:
            return self._make_error_response(msg_id, "PDF_UNAVAILABLE",
                "PDF processing is not available (pypdf not installed)")
        
        logger.info("PDF_SPLIT request received")
        return self.pdf_handler.handle_pdf_split(msg_id, params)
    
    def _handle_pdf_rotate(self, msg_id: Optional[str], params: dict) -> dict:
        """Handle PDF_ROTATE message."""
        if not PDF_AVAILABLE or self.pdf_handler is None:
            return self._make_error_response(msg_id, "PDF_UNAVAILABLE",
                "PDF processing is not available (pypdf not installed)")
        
        logger.info("PDF_ROTATE request received")
        return self.pdf_handler.handle_pdf_rotate(msg_id, params)
    
    # =========================================================================
    # Image Handlers
    # =========================================================================
    
    def _handle_image_convert(self, msg_id: Optional[str], params: dict) -> dict:
        """Handle IMAGE_CONVERT message."""
        if not IMAGE_AVAILABLE or self.image_handler is None:
            return self._make_error_response(msg_id, "IMAGE_UNAVAILABLE",
                "Image processing is not available (Pillow not installed)")
        
        logger.info("IMAGE_CONVERT request received")
        return self.image_handler.handle_image_convert(msg_id, params)
    
    def _handle_image_compress(self, msg_id: Optional[str], params: dict) -> dict:
        """Handle IMAGE_COMPRESS message."""
        if not IMAGE_AVAILABLE or self.image_handler is None:
            return self._make_error_response(msg_id, "IMAGE_UNAVAILABLE",
                "Image processing is not available (Pillow not installed)")
        
        logger.info("IMAGE_COMPRESS request received")
        return self.image_handler.handle_image_compress(msg_id, params)
    
    def _handle_image_resize(self, msg_id: Optional[str], params: dict) -> dict:
        """Handle IMAGE_RESIZE message."""
        if not IMAGE_AVAILABLE or self.image_handler is None:
            return self._make_error_response(msg_id, "IMAGE_UNAVAILABLE",
                "Image processing is not available (Pillow not installed)")
        
        logger.info("IMAGE_RESIZE request received")
        return self.image_handler.handle_image_resize(msg_id, params)
    
    # =========================================================================
    # Template Handlers
    # =========================================================================
    
    def _handle_list_templates(self, msg_id: Optional[str], params: dict) -> dict:
        """Handle LIST_TEMPLATES message."""
        if not TEMPLATE_AVAILABLE or self.template_handler is None:
            return self._make_error_response(msg_id, "TEMPLATE_UNAVAILABLE",
                "Template management is not available")
        
        logger.info("LIST_TEMPLATES request received")
        return self.template_handler.handle_list_templates(msg_id, params)
    
    def _handle_load_template(self, msg_id: Optional[str], params: dict) -> dict:
        """Handle LOAD_TEMPLATE message."""
        if not TEMPLATE_AVAILABLE or self.template_handler is None:
            return self._make_error_response(msg_id, "TEMPLATE_UNAVAILABLE",
                "Template management is not available")
        
        logger.info("LOAD_TEMPLATE request received")
        return self.template_handler.handle_load_template(msg_id, params)
    
    def _handle_get_template_path(self, msg_id: Optional[str], params: dict) -> dict:
        """Handle GET_TEMPLATE_PATH message."""
        if not TEMPLATE_AVAILABLE or self.template_handler is None:
            return self._make_error_response(msg_id, "TEMPLATE_UNAVAILABLE",
                "Template management is not available")
        
        logger.info("GET_TEMPLATE_PATH request received")
        return self.template_handler.handle_get_template_path(msg_id, params)
    
    # =========================================================================
    # Response Helpers
    # =========================================================================
    
    def _send_response(self, response: dict):
        """Send a response to stdout."""
        line = json.dumps(response, ensure_ascii=False)
        print(line, flush=True)
        logger.debug(f"Sent: {line}")
    
    def _send_error(self, msg_id: Optional[str], code: str, message: str):
        """Send an error response."""
        self._send_response(self._make_error_response(msg_id, code, message))
    
    def _make_error_response(self, msg_id: Optional[str], code: str, message: str) -> dict:
        """Create an error response object."""
        return {
            "id": msg_id,
            "success": False,
            "error": {
                "code": code,
                "message": message
            }
        }


def main():
    """Entry point."""
    logger.info("=" * 50)
    logger.info("PressO Python Engine v0.5.0")
    logger.info(f"Python {sys.version}")
    logger.info(f"Excel support: {EXCEL_AVAILABLE}")
    logger.info(f"PDF support: {PDF_AVAILABLE}")
    logger.info(f"Image support: {IMAGE_AVAILABLE}")
    logger.info(f"Template support: {TEMPLATE_AVAILABLE}")
    logger.info("=" * 50)
    
    engine = PythonEngine()
    engine.run()
    
    logger.info("Python Engine exit")
    sys.exit(0)


if __name__ == "__main__":
    main()
