#!/usr/bin/env bash
# Reproducible ECJ type-check harness for environments without a JDK/Gradle.
# Downloads a Temurin 17 JDK (via the jdk4py wheel) and Eclipse ECJ, generates
# permissive Minecraft/Forge stubs, then iteratively drives mod errors to zero.
# All heavy artifacts stay OUTSIDE the repo under $HOME/.ecj-harness.
set -euo pipefail

HARNESS="$HOME/.ecj-harness"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ECJ_BLOB_SHA="a8f827f350d0e9a47af6ef8e262af3dcc7e0c5f6"
JDK4PY_URL="https://files.pythonhosted.org/packages/ab/91/7e856a437beabdcf100ceec6754ffa84f505c2e147b1dbdc2d0330ab39bd/jdk4py-17.0.9.2-0-py3-none-manylinux1_x86_64.whl"

mkdir -p "$HARNESS"
cd "$HARNESS"

# --- JDK 17 -----------------------------------------------------------------
JAVA="$HARNESS/jdk4py-extract/jdk4py/java-runtime/bin/java"
if [ ! -x "$JAVA" ]; then
  echo "[bootstrap] fetching Temurin 17 JDK (jdk4py wheel)..."
  curl -sL --max-time 300 -o jdk4py.whl "$JDK4PY_URL"
  rm -rf jdk4py-extract
  python3 -m zipfile -e jdk4py.whl jdk4py-extract
  chmod +x jdk4py-extract/jdk4py/java-runtime/bin/* || true
fi
"$JAVA" -version

# --- ECJ --------------------------------------------------------------------
if [ ! -f ecj.jar ]; then
  echo "[bootstrap] fetching ECJ via GitHub blob API..."
  python3 - "$ECJ_BLOB_SHA" <<'PY'
import base64, json, sys, urllib.request
sha = sys.argv[1]
H = {"User-Agent": "arena", "Accept": "application/vnd.github+json"}
req = urllib.request.Request(
    f"https://api.github.com/repos/luckyx7/Teedy/git/blobs/{sha}", headers=H)
with urllib.request.urlopen(req, timeout=120) as r:
    blob = json.load(r)
open("ecj.jar", "wb").write(base64.b64decode(blob["content"]))
print("[bootstrap] ecj.jar written")
PY
fi

# --- modcopy + stubs --------------------------------------------------------
rm -rf modcopy out stubs
mkdir -p modcopy stubs
cp -r "$SCRIPT_DIR/../../src/main/java/com" modcopy/
cp "$SCRIPT_DIR"/gen_stubs.py "$SCRIPT_DIR"/fix_stubs.py "$SCRIPT_DIR"/analyze.py "$SCRIPT_DIR"/run_check.py .
python3 gen_stubs.py

# --- converge ---------------------------------------------------------------
for i in $(seq 1 "${1:-18}"); do
  echo "--- iteration $i ---"
  python3 fix_stubs.py | tail -1
done
python3 run_check.py
