"""Rebuild receiver assets in dependency order, then finish their shared mounts/surfaces."""
from pathlib import Path
import subprocess
import sys

root = Path(__file__).resolve().parent
for script in ['make_wide_array.py', 'make_parabolic_receiver.py', 'make_blocky_parabolic.py',
               'make_mesh_parabolic.py', 'make_solid_parabolic.py', 'finish_receiver_models.py']:
    subprocess.run([sys.executable, str(root/script)], check=True)
