import os
import socket
import subprocess
import time
from pathlib import Path

root = Path('/workspace/scratch/b97ec851c650')
project = root / 'radio-card-controls/work/simple-radio-repeaters'
analysis = root / 'radio-dimension-transponder/analysis'
(project / 'run/screenshots/Relay-Expansion-0.4.0.png').unlink(missing_ok=True)
compiler = Path('/usr/bin/xkbcomp')
if not compiler.exists(): compiler.symlink_to(root / 'toolchain/xvfb/root/usr/bin/xkbcomp')
env = os.environ.copy()
env.update(LD_LIBRARY_PATH=str(root / 'toolchain/xvfb/root/usr/lib/x86_64-linux-gnu'),
           DISPLAY='127.0.0.1:92', LIBGL_ALWAYS_SOFTWARE='1', REPEATERS_QA_ROOT=str(analysis / 'qa'), GRADLE_USER_HOME=str(root / 'toolchain/gradle-cache'))
with (analysis / 'xvfb.log').open('w') as display_log, (analysis / 'client-preview.log').open('w') as client_log:
    display = subprocess.Popen([str(root / 'toolchain/xvfb/root/usr/bin/Xvfb'), ':92', '-screen', '0', '1600x1200x24',
                                '-ac', '-nolisten', 'unix', '-listen', 'tcp'], env=env, stdout=display_log, stderr=subprocess.STDOUT)
    try:
        for attempt in range(30):
            if display.poll() is not None:
                raise RuntimeError('Virtual display exited; see xvfb.log')
            try:
                with socket.create_connection(('127.0.0.1', 6092), .2):
                    break
            except OSError:
                time.sleep(.2)
        else:
            raise RuntimeError('Virtual display did not initialize')
        command = ['python3', str(root / 'radio-work/run_gradle.py'), '--offline', '-I', str(analysis / 'qa/client-preview.gradle'),
                   'runClient', '-x', 'downloadAssets']
        result = subprocess.run(command, cwd=project, env=env, stdout=client_log, stderr=subprocess.STDOUT, timeout=240)
        print('Native preview exit:', result.returncode, flush=True)
        raise SystemExit(result.returncode)
    finally:
        (project / 'run/mods/bank-model-qa.jar').unlink(missing_ok=True)
        display.terminate()
        display.wait(timeout=10)
