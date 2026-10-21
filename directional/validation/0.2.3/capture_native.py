"""Launch an unmodified Minecraft renderer with QA-only capture code and save raw screenshots."""
from pathlib import Path
import hashlib
import json
import os
import shutil
import socket
import subprocess
import time

HERE = Path(__file__).resolve().parent
PROJECT = HERE.parents[2]
ROOT = PROJECT.parents[2]
TOOLCHAIN = ROOT / 'toolchain'
env = os.environ.copy()
env.update(DISPLAY='127.0.0.1:96', LIBGL_ALWAYS_SOFTWARE='1', GALLIUM_DRIVER='llvmpipe',
           LP_NUM_THREADS='4', ALSOFT_DRIVERS='null',
           LD_LIBRARY_PATH=str(TOOLCHAIN / 'xvfb/usr/lib/x86_64-linux-gnu'))

names = ['Parabolic-Receiver-0.2.3-Native-Preview.png', 'Parabolic-Receiver-0.2.3-Comparison.png', 'Parabolic-Receiver-0.2.3-Aiming.png', 'Parabolic-Receiver-0.2.3-Octagonal-Alternate.png', 'Parabolic-Receiver-0.2.3-Refined-Alternate.png']
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
        client = subprocess.Popen(['python3', str(TOOLCHAIN / 'run_gradle.py'), ':directional:runClient',
                                   '-I', str(HERE / 'client-preview.gradle')], cwd=PROJECT, env=env,
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
        log = (HERE / 'native-client.log').read_text()
        if 'MESH_NATIVE_PREVIEW verified seven aimed models' not in log or 'ALTERNATE_PACK verified native resource-pack reload: octagonal_parabolic' not in log or 'ALTERNATE_PACK verified native resource-pack reload: refined_parabolic' not in log:
            raise RuntimeError('Native baked model validation did not complete')
        output = PROJECT / 'directional/art'
        output.mkdir(exist_ok=True)
        results = []
        for name in names:
            path = screenshots / name
            if not path.is_file(): raise RuntimeError('Missing native screenshot: ' + name)
            target = output / name
            shutil.copyfile(path, target)
            results.append({'name': name, 'sha256': hashlib.sha256(path.read_bytes()).hexdigest(), 'bytes': path.stat().st_size})
        result = {'passed': True, 'renderer': 'Minecraft Java 1.20.1 native baked block renderer',
                  'capture': 'Screenshot.grab of the real client framebuffer; PNGs are unretouched',
                  'checks': ['All seven aimed models baked', 'Six real pixel textures resolved, including native mesh', 'Native mast rendered', 'Original mesh reference and both retained alternates rendered', 'Both alternate packs reloaded and actual block model changed'],
                  'screenshots': results, 'scope': 'Native model sheet; no generated imagery and no live microphone playback'}
        (HERE / 'native-capture.json').write_text(json.dumps(result, indent=2) + '\n')
        print(json.dumps(result, indent=2), flush=True)
    finally:
        if client is not None and client.poll() is None:
            client.terminate()
        if display.poll() is None:
            display.terminate(); display.wait(timeout=10)
        (PROJECT / 'directional/run/mods/mesh-receiver-native-preview.jar').unlink(missing_ok=True)
