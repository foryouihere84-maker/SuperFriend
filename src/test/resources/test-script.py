#!/usr/bin/env python3
"""
Test script for unit testing
"""
import argparse
import json
import sys

def main(args):
    result = {
        "success": True,
        "data": {"message": "Test script executed successfully"},
        "message": "Test completed"
    }
    print(json.dumps(result, indent=2))
    return 0

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Test script for unit testing")
    parser.add_argument("--test", help="Test parameter")
    
    args = parser.parse_args()
    sys.exit(main(args))
