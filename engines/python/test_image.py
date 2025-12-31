#!/usr/bin/env python3
"""
Test script for image processing.

Run this directly to test image operations without the full IPC stack:
    python test_image.py

This will create sample images and test convert, compress, and resize operations.
"""

import json
import sys
import os
import tempfile
from pathlib import Path

# Add parent to path for imports
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

# Check for Pillow first
try:
    from PIL import Image
    PILLOW_AVAILABLE = True
except ImportError:
    PILLOW_AVAILABLE = False
    print("ERROR: Pillow not installed!")
    print("Run: pip install Pillow")
    sys.exit(1)

from handlers.image_handler import ImageHandler


def create_sample_image(path: Path, width: int = 800, height: int = 600, format: str = "JPEG"):
    """Create a simple test image."""
    # Create a gradient image
    img = Image.new("RGB", (width, height))
    pixels = []
    
    for y in range(height):
        for x in range(width):
            # Simple gradient
            r = int((x / width) * 255)
            g = int((y / height) * 255)
            b = 128
            pixels.append((r, g, b))
    
    img.putdata(pixels)
    
    # Save in requested format
    if format == "PNG":
        img.save(path, format="PNG")
    elif format == "WEBP":
        img.save(path, format="WEBP", quality=95)
    else:
        img.save(path, format="JPEG", quality=95)
    
    file_size = path.stat().st_size
    print(f"  Created: {path} ({width}x{height}, {format}, {file_size} bytes)")
    return path


def test_image_convert():
    """Test image format conversion."""
    print("\n" + "=" * 60)
    print("Testing Image Convert")
    print("=" * 60)
    
    handler = ImageHandler()
    
    # Create sample image
    temp_dir = Path(tempfile.gettempdir()) / "presso_test"
    temp_dir.mkdir(parents=True, exist_ok=True)
    
    source_file = temp_dir / "test_image.jpg"
    
    print("\nCreating sample image:")
    create_sample_image(source_file, width=400, height=300, format="JPEG")
    
    # Test convert JPG -> PNG
    params = {
        "input_file": str(source_file),
        "output_format": "png",
        "output_filename": "converted.png"
    }
    
    print("\nConverting JPG -> PNG...")
    result = handler.handle_image_convert("test-convert-001", params)
    
    print(json.dumps(result, indent=2))
    
    if result.get("success"):
        print(f"\n[OK] Convert successful!")
        print(f"  Output: {result['result']['file_path']}")
        print(f"  Format: {result['result']['format']}")
        return True
    else:
        print(f"\n[FAIL] Convert failed: {result['error']['message']}")
        return False


def test_image_convert_webp():
    """Test image conversion to WEBP."""
    print("\n" + "=" * 60)
    print("Testing Image Convert (to WEBP)")
    print("=" * 60)
    
    handler = ImageHandler()
    
    temp_dir = Path(tempfile.gettempdir()) / "presso_test"
    source_file = temp_dir / "test_image.jpg"
    
    if not source_file.exists():
        create_sample_image(source_file, width=400, height=300, format="JPEG")
    
    # Test convert JPG -> WEBP
    params = {
        "input_file": str(source_file),
        "output_format": "webp",
        "quality": 90
    }
    
    print("\nConverting JPG -> WEBP...")
    result = handler.handle_image_convert("test-convert-002", params)
    
    print(json.dumps(result, indent=2))
    
    if result.get("success"):
        print(f"\n[OK] Convert to WEBP successful!")
        return True
    else:
        print(f"\n[FAIL] Convert failed: {result['error']['message']}")
        return False


def test_image_compress():
    """Test image compression."""
    print("\n" + "=" * 60)
    print("Testing Image Compress")
    print("=" * 60)
    
    handler = ImageHandler()
    
    # Create sample image
    temp_dir = Path(tempfile.gettempdir()) / "presso_test"
    temp_dir.mkdir(parents=True, exist_ok=True)
    
    source_file = temp_dir / "test_large.jpg"
    
    print("\nCreating sample image:")
    create_sample_image(source_file, width=1920, height=1080, format="JPEG")
    
    original_size = source_file.stat().st_size
    
    # Test compress
    params = {
        "input_file": str(source_file),
        "quality": 60,
        "output_filename": "compressed.jpg"
    }
    
    print(f"\nCompressing image (quality=60, original={original_size} bytes)...")
    result = handler.handle_image_compress("test-compress-001", params)
    
    print(json.dumps(result, indent=2))
    
    if result.get("success"):
        print(f"\n[OK] Compress successful!")
        print(f"  Original: {result['result']['original_size']} bytes")
        print(f"  Compressed: {result['result']['file_size']} bytes")
        print(f"  Reduction: {result['result']['size_reduction_percent']}%")
        return True
    else:
        print(f"\n[FAIL] Compress failed: {result['error']['message']}")
        return False


def test_image_resize():
    """Test image resize."""
    print("\n" + "=" * 60)
    print("Testing Image Resize")
    print("=" * 60)
    
    handler = ImageHandler()
    
    # Create sample image
    temp_dir = Path(tempfile.gettempdir()) / "presso_test"
    temp_dir.mkdir(parents=True, exist_ok=True)
    
    source_file = temp_dir / "test_resize.jpg"
    
    print("\nCreating sample image:")
    create_sample_image(source_file, width=1920, height=1080, format="JPEG")
    
    # Test resize (maintain aspect ratio)
    params = {
        "input_file": str(source_file),
        "width": 800,
        "maintain_aspect": True,
        "output_filename": "resized_800w.jpg"
    }
    
    print("\nResizing image (width=800, maintain aspect)...")
    result = handler.handle_image_resize("test-resize-001", params)
    
    print(json.dumps(result, indent=2))
    
    if result.get("success"):
        print(f"\n[OK] Resize successful!")
        print(f"  Original: {result['result']['original_width']}x{result['result']['original_height']}")
        print(f"  Resized: {result['result']['width']}x{result['result']['height']}")
        return True
    else:
        print(f"\n[FAIL] Resize failed: {result['error']['message']}")
        return False


def test_image_resize_fixed():
    """Test image resize with fixed dimensions."""
    print("\n" + "=" * 60)
    print("Testing Image Resize (fixed dimensions)")
    print("=" * 60)
    
    handler = ImageHandler()
    
    temp_dir = Path(tempfile.gettempdir()) / "presso_test"
    source_file = temp_dir / "test_resize.jpg"
    
    if not source_file.exists():
        create_sample_image(source_file, width=1920, height=1080, format="JPEG")
    
    # Test resize (fixed dimensions, maintain aspect)
    params = {
        "input_file": str(source_file),
        "width": 400,
        "height": 300,
        "maintain_aspect": True,
        "output_filename": "resized_400x300.jpg"
    }
    
    print("\nResizing image (400x300, maintain aspect)...")
    result = handler.handle_image_resize("test-resize-002", params)
    
    print(json.dumps(result, indent=2))
    
    if result.get("success"):
        print(f"\n[OK] Resize with fixed dimensions successful!")
        return True
    else:
        print(f"\n[FAIL] Resize failed: {result['error']['message']}")
        return False


def test_image_metadata_strip():
    """Test image metadata stripping."""
    print("\n" + "=" * 60)
    print("Testing Image Metadata Strip")
    print("=" * 60)
    
    handler = ImageHandler()
    
    temp_dir = Path(tempfile.gettempdir()) / "presso_test"
    source_file = temp_dir / "test_image.jpg"
    
    if not source_file.exists():
        create_sample_image(source_file, width=400, height=300, format="JPEG")
    
    # Test convert with metadata strip
    params = {
        "input_file": str(source_file),
        "output_format": "png",
        "strip_metadata": True,
        "output_filename": "no_metadata.png"
    }
    
    print("\nConverting with metadata strip...")
    result = handler.handle_image_convert("test-metadata-001", params)
    
    print(json.dumps(result, indent=2))
    
    if result.get("success"):
        print(f"\n[OK] Metadata strip successful!")
        print(f"  Metadata stripped: {result['result']['metadata_stripped']}")
        return True
    else:
        print(f"\n[FAIL] Metadata strip failed: {result['error']['message']}")
        return False


if __name__ == "__main__":
    print("=" * 60)
    print("PressO Python Engine - Image Handler Test")
    print("=" * 60)
    
    try:
        from PIL import __version__ as pillow_version
        print(f"Pillow version: {pillow_version}")
    except ImportError:
        print("ERROR: Pillow not installed!")
        print("Run: pip install Pillow")
        sys.exit(1)
    
    tests = [
        test_image_convert,
        test_image_convert_webp,
        test_image_compress,
        test_image_resize,
        test_image_resize_fixed,
        test_image_metadata_strip,
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

