"""The v3 golden files the mod's Java tests read must be what spec v3 and the simulation give now."""
import os
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def test_the_v3_golden_files_are_up_to_date():
    r = subprocess.run([sys.executable, os.path.join(ROOT, "tools", "make_golden3.py"), "--check"], capture_output=True, text=True)
    assert r.returncode == 0, r.stdout + r.stderr
