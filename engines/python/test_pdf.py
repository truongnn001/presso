#!/usr/bin/env python3
"""
Test script for PDF processing.

Run this directly to test PDF operations without the full IPC stack:
    python test_pdf.py

This will create sample PDFs and test merge, split, and rotate operations.
"""

import json
import sys
import os
import tempfile
from pathlib import Path

# Add parent to path for imports
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

# Check for pypdf first
try:
    from pypdf import PdfWriter
    PYPDF_AVAILABLE = True
except ImportError:
    PYPDF_AVAILABLE = False
    print("ERROR: pypdf not installed!")
    print("Run: pip install pypdf")
    sys.exit(1)

from handlers.pdf_handler import PdfHandler


def create_sample_pdf(path: Path, num_pages: int = 3, title: str = "Sample"):
    """Create a simple PDF for testing."""
    from pypdf import PdfWriter
    from pypdf.generic import NameObject, DictionaryObject, ArrayObject, NumberObject
    
    writer = PdfWriter()
    
    for i in range(num_pages):
        # Create a blank page with text
        page = writer.add_blank_page(width=612, height=792)  # Letter size
    
    writer.write(path)
    print(f"  Created: {path} ({num_pages} pages)")
    return path


def test_pdf_merge():
    """Test PDF merge operation."""
    print("\n" + "=" * 60)
    print("Testing PDF Merge")
    print("=" * 60)
    
    handler = PdfHandler()
    
    # Create sample PDFs
    temp_dir = Path(tempfile.gettempdir()) / "presso_test"
    temp_dir.mkdir(parents=True, exist_ok=True)
    
    file1 = temp_dir / "test1.pdf"
    file2 = temp_dir / "test2.pdf"
    file3 = temp_dir / "test3.pdf"
    
    print("\nCreating sample PDFs:")
    create_sample_pdf(file1, num_pages=2, title="Doc 1")
    create_sample_pdf(file2, num_pages=3, title="Doc 2")
    create_sample_pdf(file3, num_pages=1, title="Doc 3")
    
    # Test merge
    params = {
        "input_files": [str(file1), str(file2), str(file3)],
        "output_filename": "test_merged.pdf"
    }
    
    print("\nMerging PDFs...")
    result = handler.handle_pdf_merge("test-merge-001", params)
    
    print(json.dumps(result, indent=2))
    
    if result.get("success"):
        print(f"\n[OK] Merge successful!")
        print(f"  Output: {result['result']['file_path']}")
        print(f"  Total pages: {result['result']['page_count']}")
        return True
    else:
        print(f"\n[FAIL] Merge failed: {result['error']['message']}")
        return False


def test_pdf_split_all():
    """Test PDF split (all pages)."""
    print("\n" + "=" * 60)
    print("Testing PDF Split (all pages)")
    print("=" * 60)
    
    handler = PdfHandler()
    
    # Create sample PDF
    temp_dir = Path(tempfile.gettempdir()) / "presso_test"
    temp_dir.mkdir(parents=True, exist_ok=True)
    
    source_file = temp_dir / "split_source.pdf"
    
    print("\nCreating sample PDF:")
    create_sample_pdf(source_file, num_pages=5, title="Split Test")
    
    # Test split all
    params = {
        "input_file": str(source_file),
        "split_mode": "all",
        "output_prefix": "split_page_"
    }
    
    print("\nSplitting PDF (all pages)...")
    result = handler.handle_pdf_split("test-split-001", params)
    
    print(json.dumps(result, indent=2))
    
    if result.get("success"):
        print(f"\n[OK] Split successful!")
        print(f"  Output files: {result['result']['output_count']}")
        return True
    else:
        print(f"\n[FAIL] Split failed: {result['error']['message']}")
        return False


def test_pdf_split_range():
    """Test PDF split (page range)."""
    print("\n" + "=" * 60)
    print("Testing PDF Split (page range)")
    print("=" * 60)
    
    handler = PdfHandler()
    
    # Create sample PDF
    temp_dir = Path(tempfile.gettempdir()) / "presso_test"
    source_file = temp_dir / "split_source.pdf"
    
    if not source_file.exists():
        create_sample_pdf(source_file, num_pages=5, title="Split Test")
    
    # Test split range
    params = {
        "input_file": str(source_file),
        "split_mode": "range",
        "pages": [2, 3, 4],
        "output_prefix": "range_"
    }
    
    print("\nSplitting PDF (pages 2-4)...")
    result = handler.handle_pdf_split("test-split-002", params)
    
    print(json.dumps(result, indent=2))
    
    if result.get("success"):
        print(f"\n[OK] Split range successful!")
        return True
    else:
        print(f"\n[FAIL] Split range failed: {result['error']['message']}")
        return False


def test_pdf_rotate():
    """Test PDF rotate operation."""
    print("\n" + "=" * 60)
    print("Testing PDF Rotate")
    print("=" * 60)
    
    handler = PdfHandler()
    
    # Create sample PDF
    temp_dir = Path(tempfile.gettempdir()) / "presso_test"
    temp_dir.mkdir(parents=True, exist_ok=True)
    
    source_file = temp_dir / "rotate_source.pdf"
    
    print("\nCreating sample PDF:")
    create_sample_pdf(source_file, num_pages=3, title="Rotate Test")
    
    # Test rotate all pages
    params = {
        "input_file": str(source_file),
        "rotation": 90,
        "output_filename": "rotated_90.pdf"
    }
    
    print("\nRotating PDF (90 degrees, all pages)...")
    result = handler.handle_pdf_rotate("test-rotate-001", params)
    
    print(json.dumps(result, indent=2))
    
    if result.get("success"):
        print(f"\n[OK] Rotate successful!")
        print(f"  Rotated pages: {result['result']['rotated_pages']}")
        return True
    else:
        print(f"\n[FAIL] Rotate failed: {result['error']['message']}")
        return False


def test_pdf_rotate_specific_pages():
    """Test PDF rotate specific pages."""
    print("\n" + "=" * 60)
    print("Testing PDF Rotate (specific pages)")
    print("=" * 60)
    
    handler = PdfHandler()
    
    temp_dir = Path(tempfile.gettempdir()) / "presso_test"
    source_file = temp_dir / "rotate_source.pdf"
    
    if not source_file.exists():
        create_sample_pdf(source_file, num_pages=3, title="Rotate Test")
    
    # Test rotate specific pages
    params = {
        "input_file": str(source_file),
        "rotation": 180,
        "pages": [1, 3],  # Only rotate pages 1 and 3
        "output_filename": "rotated_180_pages_1_3.pdf"
    }
    
    print("\nRotating PDF (180 degrees, pages 1 and 3 only)...")
    result = handler.handle_pdf_rotate("test-rotate-002", params)
    
    print(json.dumps(result, indent=2))
    
    if result.get("success"):
        print(f"\n[OK] Rotate specific pages successful!")
        print(f"  Rotated pages: {result['result']['rotated_pages']}")
        return True
    else:
        print(f"\n[FAIL] Rotate failed: {result['error']['message']}")
        return False


if __name__ == "__main__":
    print("=" * 60)
    print("PressO Python Engine - PDF Handler Test")
    print("=" * 60)
    
    try:
        from pypdf import __version__ as pypdf_version
        print(f"pypdf version: {pypdf_version}")
    except ImportError:
        print("ERROR: pypdf not installed!")
        print("Run: pip install pypdf")
        sys.exit(1)
    
    tests = [
        test_pdf_merge,
        test_pdf_split_all,
        test_pdf_split_range,
        test_pdf_rotate,
        test_pdf_rotate_specific_pages,
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
    
    # Cleanup temp files
    temp_dir = Path(tempfile.gettempdir()) / "presso_test"
    if temp_dir.exists():
        print(f"\nTest files are in: {temp_dir}")
    
    sys.exit(0 if failed == 0 else 1)

