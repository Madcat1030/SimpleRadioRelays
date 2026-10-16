import json
import os
import socket
import subprocess
import time
from pathlib import Path

root = Path('/workspace/scratch/b97ec851c650')
project = root / 'radio-card-controls/work/simple-radio-repeaters'
analysis = root / 'radio-catalyst-hardening/analysis'
qa = analysis / 'qa'
server_dir = project / 'run/server'
server_dir.mkdir(parents=True, exist_ok=True)
(server_dir / 'eula.txt').write_text('eula=true\n')
(server_dir / 'server.properties').write_text('''server-ip=127.0.0.1
server-port=25573
online-mode=false
enforce-secure-profile=false
level-name=catalog-lifecycle-qa-world
level-type=minecraft:flat
generate-structures=false
view-distance=2
simulation-distance=2
spawn-protection=0
max-players=2
initial-enabled-packs=vanilla,fabric,file/catalog-qa
''')
pack = server_dir / 'catalog-lifecycle-qa-world/datapacks/catalog-qa'
pack.mkdir(parents=True, exist_ok=True)
(pack / 'pack.mcmeta').write_text(json.dumps({'pack': {'pack_format': 15, 'description': 'Temporary catalyst lifecycle QA'}}))
custom = pack / 'data/minecraft/radio_catalysts/ender_pearl.json'
custom.unlink(missing_ok=True)
for path in qa.glob('*.ready'):
    path.unlink()
(project / 'run/options.txt').write_text('renderDistance:3\nsimulationDistance:3\nmipmapLevels:0\nmaxFps:30\nonboardAccessibility:false\n')
compiler = Path('/usr/bin/xkbcomp')
if not compiler.exists():
    compiler.symlink_to(root / 'toolchain/xvfb/root/usr/bin/xkbcomp')
env = os.environ.copy()
env.update(LD_LIBRARY_PATH=str(root / 'toolchain/xvfb/root/usr/lib/x86_64-linux-gnu'),
           DISPLAY='127.0.0.1:92', LIBGL_ALWAYS_SOFTWARE='1', ALSOFT_DRIVERS='null', REPEATERS_QA_ROOT=str(qa))
command = ['python3', str(root / 'radio-work/run_gradle.py'), '--offline', '-I', str(qa / 'catalog-lifecycle.gradle')]
with (analysis / 'lifecycle-prepare.log').open('w') as prepare_log:
    subprocess.run(command + ['snapshotCatalogLaunches', '-x', 'downloadAssets'], cwd=project, env=env,
                   stdout=prepare_log, stderr=subprocess.STDOUT, timeout=180, check=True)
launches = json.loads((qa / 'launches.json').read_text())
for side in ['server', 'client']:
    launches[side]['command'].insert(1, '-Xmx2G')
launches['server']['command'].insert(1, '-Djava.awt.headless=true')
with (analysis / 'lifecycle-xvfb.log').open('w') as display_log, \
        (analysis / 'lifecycle-server.log').open('w') as server_log, \
        (analysis / 'lifecycle-client.log').open('w') as client_log:
    display = subprocess.Popen([str(root / 'toolchain/xvfb/root/usr/bin/Xvfb'), ':92', '-screen', '0', '1280x900x24',
                                '-ac', '-nolisten', 'unix', '-listen', 'tcp'], env=env, stdout=display_log, stderr=subprocess.STDOUT)
    server = client = None
    try:
        for _ in range(40):
            if display.poll() is not None:
                raise RuntimeError('Virtual display exited')
            try:
                with socket.create_connection(('127.0.0.1', 6092), .2):
                    break
            except OSError:
                time.sleep(.2)
        server = subprocess.Popen(launches['server']['command'], cwd=server_dir, env=env, stdin=subprocess.PIPE,
                                  stdout=server_log, stderr=subprocess.STDOUT, text=True)
        deadline = time.monotonic() + 300
        while time.monotonic() < deadline:
            if server.poll() is not None:
                raise RuntimeError('Local server exited before startup')
            if 'Done (' in (analysis / 'lifecycle-server.log').read_text():
                break
            time.sleep(.5)
        else:
            raise RuntimeError('Local server startup timed out')
        client = subprocess.Popen(launches['client']['command'], cwd=project / 'run', env=env,
                                  stdout=client_log, stderr=subprocess.STDOUT)
        deadline = time.monotonic() + 300
        reloaded = False
        while time.monotonic() < deadline:
            if client.poll() is not None:
                break
            if (qa / 'join-one.ready').exists() and not reloaded:
                custom.parent.mkdir(parents=True, exist_ok=True)
                custom.write_text(json.dumps({'efficiency': 2.0}))
                server.stdin.write('reload\n'); server.stdin.flush(); reloaded = True
                print('Requested native data-pack reload after the first catalog arrived.', flush=True)
            time.sleep(.5)
        else:
            raise RuntimeError('Local client lifecycle test timed out')
        assert client.returncode == 0 and (qa / 'pass.ready').exists(), 'Client lifecycle assertions did not complete'
        markers = [name for name in ['join-one', 'reload', 'disconnect', 'reconnect-and-stale-callbacks', 'pass']
                   if (qa / (name + '.ready')).exists()]
        result = {'passed': True, 'client_exit': client.returncode, 'checks': markers,
                  'scope': 'Actual local dedicated server and native client: join request/reply, reload push, disconnect/reset, reconnect, stale callbacks. No voice playback.'}
        (analysis / 'catalog-lifecycle-result.json').write_text(json.dumps(result, indent=2) + '\n')
        print(json.dumps(result), flush=True)
    finally:
        if client is not None and client.poll() is None:
            client.terminate()
        if server is not None and server.poll() is None:
            try:
                server.stdin.write('stop\n'); server.stdin.flush(); server.wait(timeout=30)
            except (BrokenPipeError, subprocess.TimeoutExpired):
                server.terminate()
        display.terminate()
        display.wait(timeout=10)
        (project / 'run/mods/catalog-lifecycle-qa.jar').unlink(missing_ok=True)
