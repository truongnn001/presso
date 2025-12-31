"""
PressO Python Engine - Image Handler
=====================================

RESPONSIBILITY:
- Image format conversion (JPG, PNG, WEBP)
- Image compression (configurable quality)
- Image resize (width/height, maintain aspect ratio)
- Image metadata handling (strip EXIF)

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
from typing import Optional, Dict, Any, Tuple

# Pillow for image processing
try:
    from PIL import Image, ImageOps, ExifTags
    PILLOW_AVAILABLE = True
except ImportError:
    PILLOW_AVAILABLE = False

logger = logging.getLogger(__name__)


class ImageHandler:
    """
    Handles image processing operations.
    
    Stateless handler - each request is independent.
    No business logic - just image manipulation.
    """
    
    # Default output directory (system temp)
    OUTPUT_DIR = Path(tempfile.gettempdir()) / "presso" / "image"
    
    # Supported formats
    SUPPORTED_FORMATS = {"JPEG", "JPG", "PNG", "WEBP"}
    
    def __init__(self):
        """Initialize Image handler."""
        if not PILLOW_AVAILABLE:
            logger.error("Pillow not installed - Image processing unavailable")
        
        # Ensure output directory exists
        self.OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
        
        logger.debug(f"ImageHandler initialized, output_dir={self.OUTPUT_DIR}")
    
    # =========================================================================
    # Image Convert
    # =========================================================================
    
    def handle_image_convert(self, msg_id: Optional[str], params: Dict[str, Any]) -> Dict:
        """
        Handle IMAGE_CONVERT request.
        
        Expected params:
        {
            "input_file": "/path/to/image.jpg",
            "output_format": "png" | "jpg" | "jpeg" | "webp",
            "output_filename": "converted.png" (optional),
            "quality": 95 (optional, for JPEG/WEBP, 1-100),
            "strip_metadata": true (optional, default false)
        }
        
        Returns:
        {
            "id": "...",
            "success": true,
            "result": {
                "file_path": "/path/to/converted.png",
                "file_size": 12345,
                "format": "PNG",
                "width": 1920,
                "height": 1080,
                "original_format": "JPEG"
            }
        }
        """
        if not PILLOW_AVAILABLE:
            return self._make_error(msg_id, "DEPENDENCY_MISSING",
                "Pillow library not installed")
        
        try:
            input_file = params.get("input_file")
            output_format = params.get("output_format", "").upper()
            output_filename = params.get("output_filename")
            quality = params.get("quality", 95)
            strip_metadata = params.get("strip_metadata", False)
            
            # Validate input
            if not input_file:
                return self._make_error(msg_id, "INVALID_INPUT",
                    "input_file is required")
            
            input_path = Path(input_file)
            if not input_path.exists():
                return self._make_error(msg_id, "FILE_NOT_FOUND",
                    f"Input file not found: {input_file}")
            
            # Validate output format
            if not output_format:
                # Try to infer from input file extension
                ext = input_path.suffix[1:].upper()
                if ext in self.SUPPORTED_FORMATS:
                    return self._make_error(msg_id, "INVALID_INPUT",
                        "output_format is required (cannot infer from input)")
                output_format = ext
            
            # Normalize format name
            if output_format == "JPG":
                output_format = "JPEG"
            
            if output_format not in self.SUPPORTED_FORMATS:
                return self._make_error(msg_id, "INVALID_INPUT",
                    f"Unsupported output format: {output_format}. Supported: {', '.join(self.SUPPORTED_FORMATS)}")
            
            # Validate quality
            if not (1 <= quality <= 100):
                return self._make_error(msg_id, "INVALID_INPUT",
                    "quality must be between 1 and 100")
            
            logger.info(f"Converting image: {input_file} -> {output_format}")
            
            # Load image
            try:
                img = Image.open(input_path)
                original_format = img.format or "UNKNOWN"
                original_size = img.size
            except Exception as e:
                return self._make_error(msg_id, "IMAGE_LOAD_ERROR",
                    f"Failed to load image: {e}")
            
            # Convert RGB if needed (for formats that don't support transparency)
            if output_format in ("JPEG", "JPG") and img.mode in ("RGBA", "LA", "P"):
                # Create white background
                background = Image.new("RGB", img.size, (255, 255, 255))
                if img.mode == "P":
                    img = img.convert("RGBA")
                background.paste(img, mask=img.split()[-1] if img.mode in ("RGBA", "LA") else None)
                img = background
            elif img.mode not in ("RGB", "RGBA", "L", "P"):
                img = img.convert("RGB")
            
            # Strip metadata if requested
            if strip_metadata:
                # Remove EXIF data by saving and reloading
                img = ImageOps.exif_transpose(img)
                # Create a new image without metadata
                data = list(img.getdata())
                if img.mode == "RGBA":
                    new_img = Image.new("RGBA", img.size)
                    new_img.putdata(data)
                elif img.mode == "RGB":
                    new_img = Image.new("RGB", img.size)
                    new_img.putdata(data)
                else:
                    new_img = Image.new(img.mode, img.size)
                    new_img.putdata(data)
                img = new_img
            
            # Generate output filename if not provided
            if not output_filename:
                timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
                unique_id = str(uuid.uuid4())[:8]
                base_name = input_path.stem
                output_filename = f"{base_name}_{timestamp}_{unique_id}.{output_format.lower()}"
            
            # Ensure correct extension
            if not output_filename.lower().endswith(('.jpg', '.jpeg', '.png', '.webp')):
                ext = output_format.lower()
                if ext == "jpeg":
                    ext = "jpg"
                output_filename = f"{Path(output_filename).stem}.{ext}"
            
            output_path = self.OUTPUT_DIR / output_filename
            
            # Save with appropriate options
            save_kwargs = {}
            if output_format in ("JPEG", "JPG", "WEBP"):
                save_kwargs["quality"] = quality
                save_kwargs["optimize"] = True
            if output_format == "PNG":
                save_kwargs["optimize"] = True
            
            # Save image
            img.save(output_path, format=output_format, **save_kwargs)
            
            file_size = output_path.stat().st_size
            
            logger.info(f"Image converted: {output_path} ({file_size} bytes, {original_size[0]}x{original_size[1]})")
            
            return {
                "id": msg_id,
                "success": True,
                "result": {
                    "file_path": str(output_path),
                    "file_name": output_filename,
                    "file_size": file_size,
                    "format": output_format,
                    "width": img.size[0],
                    "height": img.size[1],
                    "original_format": original_format,
                    "original_size": original_size,
                    "metadata_stripped": strip_metadata,
                    "timestamp": int(datetime.now().timestamp() * 1000)
                }
            }
            
        except Exception as e:
            logger.error(f"Image convert failed: {e}")
            import traceback
            traceback.print_exc(file=sys.stderr)
            return self._make_error(msg_id, "CONVERT_ERROR", str(e))
    
    # =========================================================================
    # Image Compress
    # =========================================================================
    
    def handle_image_compress(self, msg_id: Optional[str], params: Dict[str, Any]) -> Dict:
        """
        Handle IMAGE_COMPRESS request.
        
        Expected params:
        {
            "input_file": "/path/to/image.jpg",
            "quality": 75 (optional, 1-100, default 85),
            "output_filename": "compressed.jpg" (optional),
            "strip_metadata": true (optional, default false)
        }
        
        Returns:
        {
            "id": "...",
            "success": true,
            "result": {
                "file_path": "/path/to/compressed.jpg",
                "file_size": 12345,
                "original_size": 54321,
                "compression_ratio": 0.77,
                "format": "JPEG",
                "width": 1920,
                "height": 1080
            }
        }
        """
        if not PILLOW_AVAILABLE:
            return self._make_error(msg_id, "DEPENDENCY_MISSING",
                "Pillow library not installed")
        
        try:
            input_file = params.get("input_file")
            quality = params.get("quality", 85)
            output_filename = params.get("output_filename")
            strip_metadata = params.get("strip_metadata", False)
            
            # Validate input
            if not input_file:
                return self._make_error(msg_id, "INVALID_INPUT",
                    "input_file is required")
            
            input_path = Path(input_file)
            if not input_path.exists():
                return self._make_error(msg_id, "FILE_NOT_FOUND",
                    f"Input file not found: {input_file}")
            
            # Validate quality
            if not (1 <= quality <= 100):
                return self._make_error(msg_id, "INVALID_INPUT",
                    "quality must be between 1 and 100")
            
            original_size = input_path.stat().st_size
            
            logger.info(f"Compressing image: {input_file} (quality={quality})")
            
            # Load image
            try:
                img = Image.open(input_path)
                img_format = img.format or "JPEG"
                dimensions = img.size
            except Exception as e:
                return self._make_error(msg_id, "IMAGE_LOAD_ERROR",
                    f"Failed to load image: {e}")
            
            # Ensure RGB mode for JPEG compression
            if img_format in ("JPEG", "JPG") and img.mode not in ("RGB", "L"):
                if img.mode in ("RGBA", "LA", "P"):
                    background = Image.new("RGB", img.size, (255, 255, 255))
                    if img.mode == "P":
                        img = img.convert("RGBA")
                    background.paste(img, mask=img.split()[-1] if img.mode in ("RGBA", "LA") else None)
                    img = background
                else:
                    img = img.convert("RGB")
            
            # Strip metadata if requested
            if strip_metadata:
                img = ImageOps.exif_transpose(img)
                data = list(img.getdata())
                if img.mode == "RGBA":
                    new_img = Image.new("RGBA", img.size)
                    new_img.putdata(data)
                elif img.mode == "RGB":
                    new_img = Image.new("RGB", img.size)
                    new_img.putdata(data)
                else:
                    new_img = Image.new(img.mode, img.size)
                    new_img.putdata(data)
                img = new_img
            
            # Generate output filename if not provided
            if not output_filename:
                timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
                unique_id = str(uuid.uuid4())[:8]
                base_name = input_path.stem
                ext = input_path.suffix or ".jpg"
                output_filename = f"{base_name}_compressed_{timestamp}_{unique_id}{ext}"
            
            output_path = self.OUTPUT_DIR / output_filename
            
            # Save with compression
            save_kwargs = {}
            if img_format in ("JPEG", "JPG", "WEBP"):
                save_kwargs["quality"] = quality
                save_kwargs["optimize"] = True
            elif img_format == "PNG":
                # PNG compression level (0-9, higher = more compression)
                # Map quality (1-100) to PNG compress level (0-9)
                compress_level = max(0, min(9, int((100 - quality) / 11)))
                save_kwargs["compress_level"] = compress_level
                save_kwargs["optimize"] = True
            
            img.save(output_path, format=img_format, **save_kwargs)
            
            new_size = output_path.stat().st_size
            compression_ratio = new_size / original_size if original_size > 0 else 1.0
            
            logger.info(f"Image compressed: {output_path} ({original_size} -> {new_size} bytes, ratio={compression_ratio:.2f})")
            
            return {
                "id": msg_id,
                "success": True,
                "result": {
                    "file_path": str(output_path),
                    "file_name": output_filename,
                    "file_size": new_size,
                    "original_size": original_size,
                    "compression_ratio": round(compression_ratio, 4),
                    "size_reduction_percent": round((1 - compression_ratio) * 100, 2),
                    "format": img_format,
                    "width": dimensions[0],
                    "height": dimensions[1],
                    "quality": quality,
                    "metadata_stripped": strip_metadata,
                    "timestamp": int(datetime.now().timestamp() * 1000)
                }
            }
            
        except Exception as e:
            logger.error(f"Image compress failed: {e}")
            import traceback
            traceback.print_exc(file=sys.stderr)
            return self._make_error(msg_id, "COMPRESS_ERROR", str(e))
    
    # =========================================================================
    # Image Resize
    # =========================================================================
    
    def handle_image_resize(self, msg_id: Optional[str], params: Dict[str, Any]) -> Dict:
        """
        Handle IMAGE_RESIZE request.
        
        Expected params:
        {
            "input_file": "/path/to/image.jpg",
            "width": 800 (optional),
            "height": 600 (optional),
            "maintain_aspect": true (optional, default true),
            "output_filename": "resized.jpg" (optional),
            "quality": 95 (optional, for JPEG/WEBP),
            "strip_metadata": true (optional, default false)
        }
        
        Returns:
        {
            "id": "...",
            "success": true,
            "result": {
                "file_path": "/path/to/resized.jpg",
                "file_size": 12345,
                "width": 800,
                "height": 600,
                "original_width": 1920,
                "original_height": 1080,
                "format": "JPEG"
            }
        }
        """
        if not PILLOW_AVAILABLE:
            return self._make_error(msg_id, "DEPENDENCY_MISSING",
                "Pillow library not installed")
        
        try:
            input_file = params.get("input_file")
            width = params.get("width")
            height = params.get("height")
            maintain_aspect = params.get("maintain_aspect", True)
            output_filename = params.get("output_filename")
            quality = params.get("quality", 95)
            strip_metadata = params.get("strip_metadata", False)
            
            # Validate input
            if not input_file:
                return self._make_error(msg_id, "INVALID_INPUT",
                    "input_file is required")
            
            input_path = Path(input_file)
            if not input_path.exists():
                return self._make_error(msg_id, "FILE_NOT_FOUND",
                    f"Input file not found: {input_file}")
            
            # Validate dimensions
            if width is None and height is None:
                return self._make_error(msg_id, "INVALID_INPUT",
                    "At least one of width or height must be specified")
            
            if width is not None and width <= 0:
                return self._make_error(msg_id, "INVALID_INPUT",
                    "width must be positive")
            
            if height is not None and height <= 0:
                return self._make_error(msg_id, "INVALID_INPUT",
                    "height must be positive")
            
            # Validate quality
            if not (1 <= quality <= 100):
                return self._make_error(msg_id, "INVALID_INPUT",
                    "quality must be between 1 and 100")
            
            logger.info(f"Resizing image: {input_file} -> {width}x{height} (maintain_aspect={maintain_aspect})")
            
            # Load image
            try:
                img = Image.open(input_path)
                original_size = img.size
                original_width, original_height = original_size
                img_format = img.format or "JPEG"
            except Exception as e:
                return self._make_error(msg_id, "IMAGE_LOAD_ERROR",
                    f"Failed to load image: {e}")
            
            # Calculate target dimensions
            if maintain_aspect:
                if width is None:
                    # Calculate width from height
                    aspect_ratio = original_width / original_height
                    width = int(height * aspect_ratio)
                elif height is None:
                    # Calculate height from width
                    aspect_ratio = original_height / original_width
                    height = int(width * aspect_ratio)
                else:
                    # Both specified, maintain aspect ratio (fit within bounds)
                    aspect_ratio = original_width / original_height
                    target_ratio = width / height
                    
                    if aspect_ratio > target_ratio:
                        # Image is wider, fit to width
                        height = int(width / aspect_ratio)
                    else:
                        # Image is taller, fit to height
                        width = int(height * aspect_ratio)
            else:
                # Don't maintain aspect ratio
                if width is None:
                    width = original_width
                if height is None:
                    height = original_height
            
            # Resize image
            resized_img = img.resize((width, height), Image.Resampling.LANCZOS)
            
            # Strip metadata if requested
            if strip_metadata:
                resized_img = ImageOps.exif_transpose(resized_img)
                data = list(resized_img.getdata())
                if resized_img.mode == "RGBA":
                    new_img = Image.new("RGBA", resized_img.size)
                    new_img.putdata(data)
                elif resized_img.mode == "RGB":
                    new_img = Image.new("RGB", resized_img.size)
                    new_img.putdata(data)
                else:
                    new_img = Image.new(resized_img.mode, resized_img.size)
                    new_img.putdata(data)
                resized_img = new_img
            
            # Generate output filename if not provided
            if not output_filename:
                timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
                unique_id = str(uuid.uuid4())[:8]
                base_name = input_path.stem
                ext = input_path.suffix or ".jpg"
                output_filename = f"{base_name}_resized_{width}x{height}_{timestamp}_{unique_id}{ext}"
            
            output_path = self.OUTPUT_DIR / output_filename
            
            # Save with appropriate options
            save_kwargs = {}
            if img_format in ("JPEG", "JPG", "WEBP"):
                save_kwargs["quality"] = quality
                save_kwargs["optimize"] = True
            elif img_format == "PNG":
                save_kwargs["optimize"] = True
            
            resized_img.save(output_path, format=img_format, **save_kwargs)
            
            file_size = output_path.stat().st_size
            
            logger.info(f"Image resized: {output_path} ({original_size[0]}x{original_size[1]} -> {width}x{height}, {file_size} bytes)")
            
            return {
                "id": msg_id,
                "success": True,
                "result": {
                    "file_path": str(output_path),
                    "file_name": output_filename,
                    "file_size": file_size,
                    "width": width,
                    "height": height,
                    "original_width": original_width,
                    "original_height": original_height,
                    "format": img_format,
                    "maintain_aspect": maintain_aspect,
                    "metadata_stripped": strip_metadata,
                    "timestamp": int(datetime.now().timestamp() * 1000)
                }
            }
            
        except Exception as e:
            logger.error(f"Image resize failed: {e}")
            import traceback
            traceback.print_exc(file=sys.stderr)
            return self._make_error(msg_id, "RESIZE_ERROR", str(e))
    
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
        """Check if image processing is available."""
        return PILLOW_AVAILABLE

