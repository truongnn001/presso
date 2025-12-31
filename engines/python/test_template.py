#!/usr/bin/env python3
"""
Test script for template management.

Run this directly to test template operations without the full IPC stack:
    python test_template.py

This will test template discovery, loading, and validation.
"""

import json
import sys
import os
import tempfile
from pathlib import Path

# Add parent to path for imports
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from handlers.template_handler import TemplateHandler
from template_manager import TemplateManager


def create_sample_template(template_dir: Path, name: str, content: str = "Sample template"):
    """Create a sample template file."""
    template_path = template_dir / name
    template_path.write_text(content)
    print(f"  Created: {template_path}")
    return template_path


def test_list_templates_all():
    """Test listing all templates."""
    print("\n" + "=" * 60)
    print("Testing List Templates (all)")
    print("=" * 60)
    
    handler = TemplateHandler()
    
    # Create sample templates
    template_base = Path(__file__).parent / "templates"
    excel_dir = template_base / "excel"
    pdf_dir = template_base / "pdf"
    image_dir = template_base / "image"
    
    excel_dir.mkdir(parents=True, exist_ok=True)
    pdf_dir.mkdir(parents=True, exist_ok=True)
    image_dir.mkdir(parents=True, exist_ok=True)
    
    print("\nCreating sample templates:")
    create_sample_template(excel_dir, "test_template.xlsx", "Excel template content")
    create_sample_template(pdf_dir, "test_template.pdf", "PDF template content")
    create_sample_template(image_dir, "test_template.png", "Image template content")
    
    # Test list all
    params = {}
    
    print("\nListing all templates...")
    result = handler.handle_list_templates("test-list-001", params)
    
    print(json.dumps(result, indent=2))
    
    if result.get("success"):
        print(f"\n[OK] List templates successful!")
        print(f"  Found {result['result']['count']} templates")
        for template in result['result']['templates']:
            print(f"    - {template['type']}/{template['name']} ({template['id']})")
        return True
    else:
        print(f"\n[FAIL] List templates failed: {result['error']['message']}")
        return False


def test_list_templates_filtered():
    """Test listing templates filtered by type."""
    print("\n" + "=" * 60)
    print("Testing List Templates (filtered by type)")
    print("=" * 60)
    
    handler = TemplateHandler()
    
    # Test list Excel templates only
    params = {
        "template_type": "excel"
    }
    
    print("\nListing Excel templates only...")
    result = handler.handle_list_templates("test-list-002", params)
    
    print(json.dumps(result, indent=2))
    
    if result.get("success"):
        print(f"\n[OK] List Excel templates successful!")
        print(f"  Found {result['result']['count']} Excel templates")
        return True
    else:
        print(f"\n[FAIL] List templates failed: {result['error']['message']}")
        return False


def test_load_template():
    """Test loading a template."""
    print("\n" + "=" * 60)
    print("Testing Load Template")
    print("=" * 60)
    
    handler = TemplateHandler()
    
    # Test load template
    params = {
        "template_id": "test_template",
        "template_type": "excel"
    }
    
    print("\nLoading template: test_template (excel)...")
    result = handler.handle_load_template("test-load-001", params)
    
    print(json.dumps(result, indent=2))
    
    if result.get("success"):
        print(f"\n[OK] Load template successful!")
        template = result['result']
        print(f"  ID: {template['id']}")
        print(f"  Type: {template['type']}")
        print(f"  Path: {template['path']}")
        print(f"  Valid: {template['valid']}")
        return True
    else:
        print(f"\n[FAIL] Load template failed: {result['error']['message']}")
        return False


def test_load_template_not_found():
    """Test loading a non-existent template."""
    print("\n" + "=" * 60)
    print("Testing Load Template (not found)")
    print("=" * 60)
    
    handler = TemplateHandler()
    
    # Test load non-existent template
    params = {
        "template_id": "nonexistent_template",
        "template_type": "excel"
    }
    
    print("\nLoading non-existent template...")
    result = handler.handle_load_template("test-load-002", params)
    
    print(json.dumps(result, indent=2))
    
    if not result.get("success"):
        print(f"\n[OK] Correctly returned error for non-existent template")
        print(f"  Error: {result['error']['message']}")
        return True
    else:
        print(f"\n[FAIL] Should have failed for non-existent template")
        return False


def test_get_template_path():
    """Test getting template path."""
    print("\n" + "=" * 60)
    print("Testing Get Template Path")
    print("=" * 60)
    
    handler = TemplateHandler()
    
    # Test get template path
    params = {
        "template_id": "test_template",
        "template_type": "excel"
    }
    
    print("\nGetting template path: test_template (excel)...")
    result = handler.handle_get_template_path("test-path-001", params)
    
    print(json.dumps(result, indent=2))
    
    if result.get("success"):
        print(f"\n[OK] Get template path successful!")
        print(f"  Path: {result['result']['path']}")
        return True
    else:
        print(f"\n[FAIL] Get template path failed: {result['error']['message']}")
        return False


def test_template_manager_direct():
    """Test TemplateManager directly."""
    print("\n" + "=" * 60)
    print("Testing TemplateManager (direct)")
    print("=" * 60)
    
    manager = TemplateManager()
    
    # Test list templates
    print("\nListing templates via TemplateManager...")
    templates = manager.list_templates()
    print(f"Found {len(templates)} templates")
    for template in templates:
        print(f"  - {template.type}/{template.name} (ID: {template.id})")
    
    # Test load template
    print("\nLoading template via TemplateManager...")
    template_info = manager.load_template("test_template", "excel")
    if template_info:
        print(f"Loaded: {template_info.name}")
        print(f"  Path: {template_info.path}")
        print(f"  Size: {template_info.size} bytes")
        
        # Test validation
        is_valid, error = manager.validate_template(template_info)
        print(f"  Valid: {is_valid}")
        if error:
            print(f"  Error: {error}")
        return True
    else:
        print("Template not found")
        return False


if __name__ == "__main__":
    print("=" * 60)
    print("PressO Python Engine - Template Management Test")
    print("=" * 60)
    
    tests = [
        test_list_templates_all,
        test_list_templates_filtered,
        test_load_template,
        test_load_template_not_found,
        test_get_template_path,
        test_template_manager_direct,
    ]
    
    passed = 0
    failed = 0
    
    for test in tests:
        try:
            if test():
                passed += 1
            else:
                failed += 1
        except Exception as e:
            print(f"[FAIL] Exception: {e}")
            import traceback
            traceback.print_exc()
            failed += 1
    
    print()
    print("=" * 60)
    print(f"Results: {passed} passed, {failed} failed")
    print("=" * 60)
    
    sys.exit(0 if failed == 0 else 1)

