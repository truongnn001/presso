"""
PressO Python Engine - Template Manager
========================================

RESPONSIBILITY:
- Template discovery and listing
- Template loading and validation
- Template metadata management
- Generic template interface for handlers

ARCHITECTURAL ROLE:
- Stateless template resource management
- NO business logic
- NO database access
- NO external network calls
- Read-only template access (no editing)

Reference: PROJECT_DOCUMENTATION.md Section 4.3
"""

import os
import sys
import logging
from datetime import datetime
from pathlib import Path
from typing import Optional, Dict, Any, List, Tuple
from dataclasses import dataclass

logger = logging.getLogger(__name__)


@dataclass
class TemplateInfo:
    """Template metadata structure."""
    id: str
    name: str
    type: str  # 'excel', 'pdf', 'image'
    path: Path
    filename: str
    size: int
    version: str = "1.0"  # TODO (Phase 2+): Extract from template metadata
    description: Optional[str] = None  # TODO (Phase 2+): Extract from template metadata


class TemplateManager:
    """
    Manages template discovery, loading, and validation.
    
    Stateless manager - each request is independent.
    No business logic - just template resource management.
    """
    
    # Base template directory (relative to engine)
    BASE_TEMPLATE_DIR = Path(__file__).parent / "templates"
    
    # Template type directories
    TEMPLATE_DIRS = {
        "excel": BASE_TEMPLATE_DIR / "excel",
        "pdf": BASE_TEMPLATE_DIR / "pdf",
        "image": BASE_TEMPLATE_DIR / "image",
    }
    
    # Supported template file extensions by type
    TEMPLATE_EXTENSIONS = {
        "excel": {".xlsx", ".xls"},
        "pdf": {".pdf"},
        "image": {".png", ".jpg", ".jpeg", ".webp"},
    }
    
    def __init__(self):
        """Initialize template manager."""
        # Ensure template directories exist
        for template_type, template_dir in self.TEMPLATE_DIRS.items():
            template_dir.mkdir(parents=True, exist_ok=True)
            logger.debug(f"Template directory for {template_type}: {template_dir}")
        
        logger.info("TemplateManager initialized")
    
    # =========================================================================
    # Template Discovery
    # =========================================================================
    
    def list_templates(self, template_type: Optional[str] = None) -> List[TemplateInfo]:
        """
        List all available templates, optionally filtered by type.
        
        Args:
            template_type: Optional filter ('excel', 'pdf', 'image')
        
        Returns:
            List of TemplateInfo objects
        """
        templates = []
        
        # Determine which types to scan
        types_to_scan = [template_type] if template_type else list(self.TEMPLATE_DIRS.keys())
        
        for ttype in types_to_scan:
            if ttype not in self.TEMPLATE_DIRS:
                logger.warning(f"Unknown template type: {ttype}")
                continue
            
            template_dir = self.TEMPLATE_DIRS[ttype]
            if not template_dir.exists():
                logger.debug(f"Template directory does not exist: {template_dir}")
                continue
            
            # Scan for template files
            extensions = self.TEMPLATE_EXTENSIONS.get(ttype, set())
            
            for file_path in template_dir.iterdir():
                if not file_path.is_file():
                    continue
                
                # Check if file has valid extension
                if file_path.suffix.lower() not in extensions:
                    continue
                
                # Skip hidden/system files
                if file_path.name.startswith('.'):
                    continue
                
                try:
                    template_info = self._create_template_info(file_path, ttype)
                    templates.append(template_info)
                except Exception as e:
                    logger.warning(f"Failed to process template {file_path}: {e}")
        
        logger.debug(f"Found {len(templates)} templates (type={template_type})")
        return templates
    
    def _create_template_info(self, file_path: Path, template_type: str) -> TemplateInfo:
        """Create TemplateInfo from file path."""
        # Generate template ID (filename without extension)
        template_id = file_path.stem
        
        # Get file size
        file_size = file_path.stat().st_size
        
        # Create TemplateInfo
        return TemplateInfo(
            id=template_id,
            name=file_path.name,
            type=template_type,
            path=file_path,
            filename=file_path.name,
            size=file_size,
            version="1.0",  # TODO (Phase 2+): Extract from template metadata
            description=None  # TODO (Phase 2+): Extract from template metadata
        )
    
    # =========================================================================
    # Template Loading
    # =========================================================================
    
    def load_template(self, template_id: str, template_type: Optional[str] = None) -> Optional[TemplateInfo]:
        """
        Load a template by ID.
        
        Args:
            template_id: Template identifier (filename without extension)
            template_type: Optional template type filter
        
        Returns:
            TemplateInfo if found, None otherwise
        """
        # If type is specified, search only that type
        if template_type:
            if template_type not in self.TEMPLATE_DIRS:
                logger.warning(f"Unknown template type: {template_type}")
                return None
            
            template_dir = self.TEMPLATE_DIRS[template_type]
            template_info = self._find_template_in_dir(template_dir, template_id, template_type)
            if template_info:
                return template_info
        
        # Otherwise, search all template directories
        for ttype, template_dir in self.TEMPLATE_DIRS.items():
            template_info = self._find_template_in_dir(template_dir, template_id, ttype)
            if template_info:
                return template_info
        
        logger.warning(f"Template not found: {template_id} (type={template_type})")
        return None
    
    def _find_template_in_dir(self, template_dir: Path, template_id: str, template_type: str) -> Optional[TemplateInfo]:
        """Find a template in a specific directory."""
        if not template_dir.exists():
            return None
        
        extensions = self.TEMPLATE_EXTENSIONS.get(template_type, set())
        
        # Try to find template with any supported extension
        for ext in extensions:
            template_path = template_dir / f"{template_id}{ext}"
            if template_path.exists() and template_path.is_file():
                return self._create_template_info(template_path, template_type)
        
        return None
    
    def load_template_by_path(self, template_path: str) -> Optional[TemplateInfo]:
        """
        Load a template by full file path.
        
        Args:
            template_path: Full path to template file
        
        Returns:
            TemplateInfo if found and valid, None otherwise
        """
        path = Path(template_path)
        
        if not path.exists():
            logger.warning(f"Template path does not exist: {template_path}")
            return None
        
        if not path.is_file():
            logger.warning(f"Template path is not a file: {template_path}")
            return None
        
        # Determine template type from path
        template_type = None
        for ttype, template_dir in self.TEMPLATE_DIRS.items():
            try:
                if path.resolve().is_relative_to(template_dir.resolve()):
                    template_type = ttype
                    break
            except ValueError:
                continue
        
        if not template_type:
            # Try to infer from extension
            ext = path.suffix.lower()
            for ttype, extensions in self.TEMPLATE_EXTENSIONS.items():
                if ext in extensions:
                    template_type = ttype
                    break
        
        if not template_type:
            logger.warning(f"Could not determine template type for: {template_path}")
            return None
        
        return self._create_template_info(path, template_type)
    
    # =========================================================================
    # Template Validation
    # =========================================================================
    
    def validate_template(self, template_info: TemplateInfo) -> Tuple[bool, Optional[str]]:
        """
        Validate a template.
        
        Args:
            template_info: TemplateInfo to validate
        
        Returns:
            Tuple of (is_valid, error_message)
        """
        # Check file exists
        if not template_info.path.exists():
            return False, f"Template file does not exist: {template_info.path}"
        
        # Check file is readable
        if not os.access(template_info.path, os.R_OK):
            return False, f"Template file is not readable: {template_info.path}"
        
        # Check file size > 0
        if template_info.size == 0:
            return False, f"Template file is empty: {template_info.path}"
        
        # Check extension matches type
        ext = template_info.path.suffix.lower()
        expected_extensions = self.TEMPLATE_EXTENSIONS.get(template_info.type, set())
        if ext not in expected_extensions:
            return False, f"Template file extension {ext} does not match type {template_info.type}"
        
        # TODO (Phase 2+): Add format-specific validation
        # - Excel: Try to open with openpyxl
        # - PDF: Try to open with pypdf
        # - Image: Try to open with Pillow
        
        return True, None
    
    def get_template_path(self, template_id: str, template_type: Optional[str] = None) -> Optional[Path]:
        """
        Get template file path by ID.
        
        Convenience method for handlers that just need the path.
        
        Args:
            template_id: Template identifier
            template_type: Optional template type filter
        
        Returns:
            Path to template file, or None if not found
        """
        template_info = self.load_template(template_id, template_type)
        if template_info:
            is_valid, error = self.validate_template(template_info)
            if is_valid:
                return template_info.path
            else:
                logger.warning(f"Template validation failed: {error}")
        
        return None
    
    # =========================================================================
    # Template Metadata
    # =========================================================================
    
    def get_template_metadata(self, template_id: str, template_type: Optional[str] = None) -> Optional[Dict[str, Any]]:
        """
        Get template metadata as dictionary.
        
        Args:
            template_id: Template identifier
            template_type: Optional template type filter
        
        Returns:
            Dictionary with template metadata, or None if not found
        """
        template_info = self.load_template(template_id, template_type)
        if not template_info:
            return None
        
        is_valid, error = self.validate_template(template_info)
        
        return {
            "id": template_info.id,
            "name": template_info.name,
            "type": template_info.type,
            "path": str(template_info.path),
            "filename": template_info.filename,
            "size": template_info.size,
            "version": template_info.version,
            "description": template_info.description,
            "valid": is_valid,
            "error": error
        }
    
    def list_templates_metadata(self, template_type: Optional[str] = None) -> List[Dict[str, Any]]:
        """
        List all templates with metadata.
        
        Args:
            template_type: Optional filter ('excel', 'pdf', 'image')
        
        Returns:
            List of template metadata dictionaries
        """
        templates = self.list_templates(template_type)
        
        metadata_list = []
        for template_info in templates:
            is_valid, error = self.validate_template(template_info)
            
            metadata_list.append({
                "id": template_info.id,
                "name": template_info.name,
                "type": template_info.type,
                "path": str(template_info.path),
                "filename": template_info.filename,
                "size": template_info.size,
                "version": template_info.version,
                "description": template_info.description,
                "valid": is_valid,
                "error": error
            })
        
        return metadata_list

