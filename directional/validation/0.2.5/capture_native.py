"""Launch an unmodified Minecraft renderer with QA-only capture code and save raw screenshots."""
from pathlib import Path
import os
import socket
import subprocess
import time
from collect_native import NAMES, collect

HERE = Path(__file__).resolve().parent
PROJECT = HERE.parents[2]
ROOT = PROJECT.parents[2]
TOOLCHAIN = ROOT / 'toolchain'
env = os.environ.copy()
env.update(DISPLAY='127.0.0.1:96', LIBGL_ALWAYS_SOFTWARE='1', GALLIUM_DRIVER='llvmpipe',
           LP_NUM_THREADS='4', ALSOFT_DRIVERS='null',
           LD_LIBRARY_PATH=str(TOOLCHAIN / 'xvfb/usr/lib/x86_64-linux-gnu'))

names = NAMES
screenshots = PROJECT / 'directional/run/screenshots'
for name in names: (screenshots / name).unlink(missing_ok=True)
with (HERE / 'xvfb.log').open('w') as display_log, (HERE / 'native-client.log').open('w') as client_log:
    display = subprocess.Popen([str(TOOLCHAIN / 'xvfb/usr/bin/Xvfb'), ':96', '-screen', '0', '1600x1000x24',
                                '-ac', '-nolisten', 'unix', '-listen', 'tcp'], env=env,
                               stdout=display_log, stderr=subprocess.STDOUT)
    client = None
    try:
        for attempt in range(50):
            if display.poll() is not None: raise RuntimeError('Native display stopped; inspect xvfb.log')
            try:
                with socket.create_connection(('127.0.0.1', 6096), .2): break
            except OSError: time.sleep(.2)
        else: raise RuntimeError('Native display did not become ready')
        client = subprocess.Popen(['python3', str(TOOLCHAIN / 'run_gradle.py'), ':directional:runGameTest', ':directional:runClient',
                                   '-I', str(HERE / 'server-check.gradle'), '-I', str(HERE / 'client-preview.gradle')], cwd=PROJECT, env=env,
                                  stdout=client_log, stderr=subprocess.STDOUT)
        for interval in range(120):
            try:
                code = client.wait(timeout=10)
                break
            except subprocess.TimeoutExpired:
                print('Native Minecraft capture running...', flush=True)
        else:
            raise RuntimeError('Native capture timed out; inspect native-client.log')
        if code: raise RuntimeError('Native client failed; inspect native-client.log')
        collect()
    finally:
        if client is not None and client.poll() is None:
            client.terminate()
        if display.poll() is None:
            display.terminate(); display.wait(timeout=10)
        (PROJECT / 'directional/run/mods/mesh-receiver-native-preview.jar').unlink(missing_ok=True)
        (PROJECT / 'directional/run/gametest/mods/shared-mount-checks.jar').unlink(missing_ok=True)
