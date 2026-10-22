#!/usr/bin/env python3
"""Run ECJ and parse diagnostics into structured records."""
import glob
import os
import re
import subprocess

HARNESS = os.path.expanduser("~/.ecj-harness")
JAVA = os.path.join(HARNESS, "jdk4py-extract/jdk4py/java-runtime/bin/java")
ECJ = os.path.join(HARNESS, "ecj.jar")
MOD = os.path.join(HARNESS, "modcopy")
STUBS = os.path.join(HARNESS, "stubs")
OUT = os.path.join(HARNESS, "out")


def run():
    sources = sorted(glob.glob(os.path.join(MOD, "**", "*.java"), recursive=True))
    stubs = sorted(glob.glob(os.path.join(STUBS, "**", "*.java"), recursive=True))
    os.makedirs(OUT, exist_ok=True)
    args = [JAVA, "-jar", ECJ, "-source", "17", "-target", "17", "-nowarn",
            "-proc:none", "-d", OUT, "-cp", OUT] + sources + stubs
    p = subprocess.run(args, capture_output=True, text=True)
    return p.stderr + p.stdout


def parse(raw):
    blocks = [b.strip() for b in raw.split("----------") if b.strip()]
    diags = []
    for b in blocks:
        lines = b.splitlines()
        m = re.search(r"ERROR in (.+?) \(at line (\d+)\)", lines[0]) if lines else None
        if not m:
            continue
        diags.append((m.group(1), int(m.group(2)), lines[-1].strip(), b))
    return diags


if __name__ == "__main__":
    diags = parse(run())
    mod = [d for d in diags if "/modcopy/" in d[0]]
    stub = [d for d in diags if "/stubs/" in d[0]]
    print(f"TOTAL={len(diags)} MOD={len(mod)} STUBS={len(stub)}")
