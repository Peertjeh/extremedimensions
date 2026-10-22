"""The v2 golden file the mod's Java test reads must be what spec v2 and the simulation give now."""
import os
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def test_the_v2_golden_file_is_up_to_date():
    r = subprocess.run([sys.executable, os.path.join(ROOT, "tools", "make_golden2.py"), "--check"], capture_output=True, text=True)
    assert r.returncode == 0, r.stdout + r.stderr
