#!/usr/bin/env python3
"""
Slow script for timeout testing
"""
import time
import json
import sys

def main(args):
    time.sleep(5)
    result = {
        "success": True,
        "data": {"message": "Slow script completed"},
        "message": "Test completed"
    }
    print(json.dumps(result, indent=2))
    return 0

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Slow script for timeout testing")
    args = parser.parse_args()
    sys.exit(main(args))
