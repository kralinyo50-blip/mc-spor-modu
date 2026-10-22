#!/usr/bin/env python3
"""Generate empty shell stubs for every external class the mod imports."""
import os
import sys

HARNESS = os.path.expanduser("~/.ecj-harness")
ROOT = os.path.join(HARNESS, "stubs")
sys.path.insert(0, HARNESS)


def is_jdk(fqn: str) -> bool:
    return fqn.startswith(("java.", "javax.", "sun."))


def main():
    # collect external imports from the copied mod sources
    fqns = set()
    for dp, _, files in os.walk(os.path.join(HARNESS, "modcopy")):
        for fn in files:
            if not fn.endswith(".java"):
                continue
            for line in open(os.path.join(dp, fn)):
                line = line.strip()
                if line.startswith("import "):
                    imp = line[len("import "):].rstrip(";")
                    if imp and not imp.startswith("com.sportcraft") and not imp.endswith(".*"):
                        fqns.add(imp)
    try:
        from fix_stubs import SUPERCLASS
    except Exception:
        SUPERCLASS = {}
    made = 0
    for fqn in sorted(fqns):
        if is_jdk(fqn):
            continue
        pkg, _, simple = fqn.rpartition(".")
        d = os.path.join(ROOT, pkg.replace(".", "/"))
        os.makedirs(d, exist_ok=True)
        path = os.path.join(d, simple + ".java")
        if os.path.exists(path):
            continue
        ext = f" extends {SUPERCLASS[fqn]}" if SUPERCLASS.get(fqn) else ""
        with open(path, "w") as f:
            f.write(f"package {pkg};\n\npublic class {simple}{ext} {{\n}}\n")
        made += 1
    print("shells written:", made)


if __name__ == "__main__":
    main()
