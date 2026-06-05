#!/usr/bin/env python3
"""
PPTX Reader - Extract text and structure from PowerPoint files
Uses markitdown for text extraction
"""

import sys
import os
import json
import argparse
from pathlib import Path

def main():
    parser = argparse.ArgumentParser(description='Read PowerPoint presentation')
    parser.add_argument('--input', '-i', required=True, help='Input PPTX file path')
    parser.add_argument('--output', '-o', help='Output JSON file path (optional)')
    parser.add_argument('--format', '-f', choices=['text', 'json', 'markdown'], default='text',
                        help='Output format (text, json, markdown)')

    args = parser.parse_args()

    input_path = Path(args.input)
    if not input_path.exists():
        print(f"Error: Input file not found: {args.input}", file=sys.stderr)
        sys.exit(1)

    if not input_path.suffix.lower() == '.pptx':
        print(f"Error: Input file must be a PPTX file: {args.input}", file=sys.stderr)
        sys.exit(1)

    try:
        # Try to use markitdown for extraction
        from markitdown import MarkItDown

        md = MarkItDown()
        result = md.convert(str(input_path))

        if args.format == 'text':
            output = result.text_content
        elif args.format == 'markdown':
            output = result.text_content
        else:
            # JSON format - parse the markdown into structured data
            output = json.dumps({
                'file': str(input_path),
                'content': result.text_content,
                'slides': extract_slides(result.text_content)
            }, indent=2)

        if args.output:
            Path(args.output).write_text(output, encoding='utf-8')
            print(f"Output saved to: {args.output}")
        else:
            print(output)

    except ImportError:
        print("Error: markitdown not installed. Install with: pip install 'markitdown[pptx]'", file=sys.stderr)
        sys.exit(1)
    except Exception as e:
        print(f"Error processing PPTX: {e}", file=sys.stderr)
        sys.exit(1)

def extract_slides(content):
    """Extract slide information from markdown content"""
    slides = []
    lines = content.split('\n')
    current_slide = None

    for line in lines:
        if line.startswith('# ') or line.startswith('## '):
            if current_slide:
                slides.append(current_slide)
            current_slide = {'title': line.strip('# '), 'content': []}
        elif current_slide:
            if line.strip():
                current_slide['content'].append(line)

    if current_slide:
        slides.append(current_slide)

    return slides

if __name__ == '__main__':
    main()