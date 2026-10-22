#!/usr/bin/env python3
"""Final gate: print mod vs stub error counts and exit non-zero on mod errors."""
import json
import os
import sys

sys.path.insert(0, os.path.expanduser("~/.ecj-harness"))
import analyze  # noqa: E402

HARNESS = os.path.expanduser("~/.ecj-harness")


def main():
    diags = analyze.parse(analyze.run())
    mod = [d for d in diags if "/modcopy/" in d[0]]
    stub = [d for d in diags if "/stubs/" in d[0]]
    print(f"=== MOD ERRORS: {len(mod)}  STUB ERRORS: {len(stub)}")
    for path, line, msg, _ in mod[:60]:
        print(path.replace(HARNESS + "/modcopy/", ""), line, msg[:110])
    json.dump({"mod_errors": len(mod), "stub_errors": len(stub)},
              open(os.path.join(HARNESS, "snapshot.json"), "w"))
    sys.exit(1 if mod else 0)


if __name__ == "__main__":
    main()
