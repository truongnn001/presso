"""
PressO Python Engine - Template Handler
========================================

RESPONSIBILITY:
- Handle IPC requests for template operations
- LIST_TEMPLATES, LOAD_TEMPLATE operations
- Bridge between IPC and TemplateManager

ARCHITECTURAL ROLE:
- Stateless template management
- NO business logic
- NO database access
- NO external network calls

Reference: PROJECT_DOCUMENTATION.md Section 4.3
"""

import sys
import logging
from datetime import datetime
from pathlib import Path
from typing import Optional, Dict, Any

# Import template manager
try:
    from template_manager import TemplateManager
    TEMPLATE_MANAGER_AVAILABLE = True
except ImportError as e:
    TEMPLATE_MANAGER_AVAILABLE = False

logger = logging.getLogger(__name__)


class TemplateHandler:
    """
    Handles template management IPC requests.
    
    Stateless handler - each request is independent.
    No business logic - just template resource management.
    """
    
    def __init__(self):
        """Initialize template handler."""
        if not TEMPLATE_MANAGER_AVAILABLE:
            logger.error("TemplateManager not available")
            self.template_manager = None
        else:
            self.template_manager = TemplateManager()
            logger.info("TemplateHandler initialized")
    
    # =========================================================================
    # IPC Handlers
    # =========================================================================
    
    def handle_list_templates(self, msg_id: Optional[str], params: Dict[str, Any]) -> Dict:
        """
        Handle LIST_TEMPLATES request.
        
        Expected params:
        {
            "template_type": "excel" | "pdf" | "image" (optional)
        }
        
        Returns:
        {
            "id": "...",
            "success": true,
            "result": {
                "templates": [
                    {
                        "id": "template_name",
                        "name": "template_name.xlsx",
                        "type": "excel",
                        "path": "/path/to/template.xlsx",
                        "filename": "template_name.xlsx",
                        "size": 12345,
                        "version": "1.0",
                        "description": null,
                        "valid": true,
                        "error": null
                    },
                    ...
                ],
                "count": 5
            }
        }
        """
        if not self.template_manager:
            return self._make_error(msg_id, "TEMPLATE_MANAGER_UNAVAILABLE",
                "Template manager is not available")
        
        try:
            template_type = params.get("template_type")
            
            logger.info(f"Listing templates (type={template_type})")
            
            templates_metadata = self.template_manager.list_templates_metadata(template_type)
            
            return {
                "id": msg_id,
                "success": True,
                "result": {
                    "templates": templates_metadata,
                    "count": len(templates_metadata),
                    "template_type": template_type,
                    "timestamp": int(datetime.now().timestamp() * 1000)
                }
            }
            
        except Exception as e:
            logger.error(f"List templates failed: {e}")
            import traceback
            traceback.print_exc(file=sys.stderr)
            return self._make_error(msg_id, "LIST_TEMPLATES_ERROR", str(e))
    
    def handle_load_template(self, msg_id: Optional[str], params: Dict[str, Any]) -> Dict:
        """
        Handle LOAD_TEMPLATE request.
        
        Expected params:
        {
            "template_id": "template_name",
            "template_type": "excel" | "pdf" | "image" (optional)
        }
        
        Returns:
        {
            "id": "...",
            "success": true,
            "result": {
                "id": "template_name",
                "name": "template_name.xlsx",
                "type": "excel",
                "path": "/path/to/template.xlsx",
                "filename": "template_name.xlsx",
                "size": 12345,
                "version": "1.0",
                "description": null,
                "valid": true,
                "error": null
            }
        }
        """
        if not self.template_manager:
            return self._make_error(msg_id, "TEMPLATE_MANAGER_UNAVAILABLE",
                "Template manager is not available")
        
        try:
            template_id = params.get("template_id")
            template_type = params.get("template_type")
            
            if not template_id:
                return self._make_error(msg_id, "INVALID_INPUT",
                    "template_id is required")
            
            logger.info(f"Loading template: {template_id} (type={template_type})")
            
            metadata = self.template_manager.get_template_metadata(template_id, template_type)
            
            if not metadata:
                return self._make_error(msg_id, "TEMPLATE_NOT_FOUND",
                    f"Template not found: {template_id} (type={template_type})")
            
            if not metadata.get("valid"):
                return self._make_error(msg_id, "TEMPLATE_INVALID",
                    f"Template validation failed: {metadata.get('error')}")
            
            return {
                "id": msg_id,
                "success": True,
                "result": metadata
            }
            
        except Exception as e:
            logger.error(f"Load template failed: {e}")
            import traceback
            traceback.print_exc(file=sys.stderr)
            return self._make_error(msg_id, "LOAD_TEMPLATE_ERROR", str(e))
    
    def handle_get_template_path(self, msg_id: Optional[str], params: Dict[str, Any]) -> Dict:
        """
        Handle GET_TEMPLATE_PATH request.
        
        Convenience method for handlers that just need the file path.
        
        Expected params:
        {
            "template_id": "template_name",
            "template_type": "excel" | "pdf" | "image" (optional)
        }
        
        Returns:
        {
            "id": "...",
            "success": true,
            "result": {
                "path": "/path/to/template.xlsx",
                "template_id": "template_name",
                "template_type": "excel"
            }
        }
        """
        if not self.template_manager:
            return self._make_error(msg_id, "TEMPLATE_MANAGER_UNAVAILABLE",
                "Template manager is not available")
        
        try:
            template_id = params.get("template_id")
            template_type = params.get("template_type")
            
            if not template_id:
                return self._make_error(msg_id, "INVALID_INPUT",
                    "template_id is required")
            
            logger.info(f"Getting template path: {template_id} (type={template_type})")
            
            template_path = self.template_manager.get_template_path(template_id, template_type)
            
            if not template_path:
                return self._make_error(msg_id, "TEMPLATE_NOT_FOUND",
                    f"Template not found: {template_id} (type={template_type})")
            
            # Get template info for type
            template_info = self.template_manager.load_template(template_id, template_type)
            template_type_actual = template_info.type if template_info else template_type or "unknown"
            
            return {
                "id": msg_id,
                "success": True,
                "result": {
                    "path": str(template_path),
                    "template_id": template_id,
                    "template_type": template_type_actual,
                    "timestamp": int(datetime.now().timestamp() * 1000)
                }
            }
            
        except Exception as e:
            logger.error(f"Get template path failed: {e}")
            import traceback
            traceback.print_exc(file=sys.stderr)
            return self._make_error(msg_id, "GET_TEMPLATE_PATH_ERROR", str(e))
    
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
        """Check if template management is available."""
        return TEMPLATE_MANAGER_AVAILABLE

