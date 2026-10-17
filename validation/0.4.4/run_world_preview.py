import hashlib
import json
import os
import shlex
import shutil
import socket
import subprocess
import time
from pathlib import Path
from zipfile import ZipFile

ROOT = Path('/workspace/scratch/b97ec851c650')
PROJECT = ROOT / 'radio-card-controls/work/simple-radio-repeaters'
TASK = ROOT / 'radio-insert-visuals'
ANALYSIS = TASK / 'analysis'
QA = ANALYSIS / 'world-qa'
SERVER = ANALYSIS / 'production-server'
CACHE = ROOT / 'toolchain/gradle-cache/caches/modules-2/files-2.1'
PROTOTYPE = PROJECT / 'build/libs/simple-radio-repeaters-fabric-1.20.1-0.4.4-prototype.jar'
SERVER.mkdir(exist_ok=True)

def jar(group, artifact, version=None):
    folder = CACHE / group / artifact
    if version:
        folder /= version
    found = [p for p in folder.rglob('*.jar') if not any(s in p.name for s in ['-sources', '-javadoc'])]
    assert len(found) == 1, found
    return found[0]

loader = jar('net.fabricmc', 'fabric-loader', '0.19.3')
with ZipFile(loader) as z:
    libraries = json.loads(z.read('fabric-installer.json'))['libraries']['common']
classpath = [loader] + [jar(*lib['name'].split(':')) for lib in libraries]
classpath += [jar('net.fabricmc', 'intermediary', '1.20.1'), ROOT / 'toolchain/gradle-cache/caches/fabric-loom/1.20.1/minecraft-server.jar']
mods = SERVER / 'mods'
mods.mkdir(exist_ok=True)
for group, artifact in [('maven.modrinth', 'simple-radio'), ('maven.modrinth', 'lexiconfig'),
                        ('maven.modrinth', 'simple-voice-chat'), ('net.fabricmc.fabric-api', 'fabric-api')]:
    dep = jar(group, artifact)
    shutil.copyfile(dep, mods / dep.name)
shutil.copyfile(PROTOTYPE, mods / PROTOTYPE.name)
(SERVER / 'eula.txt').write_text('eula=true\n')
(SERVER / 'server.properties').write_text('''server-ip=127.0.0.1
server-port=25578
online-mode=false
enforce-secure-profile=false
level-name=socket-visual-world
level-type=minecraft:flat
generate-structures=false
view-distance=3
simulation-distance=3
spawn-protection=0
max-players=1
gamemode=creative
force-gamemode=true
difficulty=peaceful
''')
for marker in QA.glob('*.ready'):
    marker.unlink()
(PROJECT / 'run/screenshots/Relay-Sockets-0.4.4-In-World.png').unlink(missing_ok=True)
env = os.environ.copy()
env.update(LD_LIBRARY_PATH=str(ROOT / 'toolchain/xvfb/root/usr/lib/x86_64-linux-gnu'),
           DISPLAY='127.0.0.1:93', LIBGL_ALWAYS_SOFTWARE='1', ALSOFT_DRIVERS='null', REPEATERS_QA_ROOT=str(QA))
server_command = [str(ROOT / 'toolchain/jdk-21.0.2/bin/java'), '-Xmx2G', '-Xverify:all',
                  '-XX:ActiveProcessorCount=4',
                  '-Djava.awt.headless=true', '-Dfabric.development=false', '-cp', os.pathsep.join(map(str, classpath)),
                  'net.fabricmc.loader.impl.launch.knot.KnotServer', 'nogui']
client_command = json.loads((QA / 'launches.json').read_text())['client']['command']
client_command.insert(1, '-Xmx2G')
client_command.insert(1, '-XX:ActiveProcessorCount=4')
client_command += ['--username', 'RelaySocketQA']
# Preview-only mods from an earlier capture must not replace the play connection with their screen.
(PROJECT / 'run/mods/bank-model-qa.jar').unlink(missing_ok=True)
# Recompile only the temporary QA client against the snapshotted native classpath.
argfile = Path(next(a[1:] for a in client_command if a.startswith('@')))
arguments = shlex.split(argfile.read_text())
qa_classpath = arguments[arguments.index('-classpath') + 1]
subprocess.run([str(ROOT / 'toolchain/jdk-21.0.2/bin/javac'), '--release', '17', '-proc:none',
                '-classpath', qa_classpath, '-d', str(QA / 'classes')]
               + [str(p) for p in (QA / 'java').rglob('*.java')], check=True, timeout=60)
with ZipFile(PROJECT / 'run/mods/socket-visual-qa.jar', 'w') as z:
    for folder in ['classes', 'resources']:
        for p in (QA / folder).rglob('*'):
            if p.is_file():
                z.write(p, p.relative_to(QA / folder).as_posix())
with (ANALYSIS / 'world-xvfb.log').open('w') as display_log, \
        (ANALYSIS / 'prototype-server.log').open('w') as server_log, \
        (ANALYSIS / 'world-client.log').open('w') as client_log:
    display = subprocess.Popen([str(ROOT / 'toolchain/xvfb/root/usr/bin/Xvfb'), ':93', '-screen', '0', '1600x1000x24',
                                '-ac', '-nolisten', 'unix', '-listen', 'tcp'], env=env, stdout=display_log, stderr=subprocess.STDOUT)
    server = client = None
    def wait_for(condition, timeout=150):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            if condition():
                return
            if server is not None and server.poll() is not None:
                raise RuntimeError('Server stopped early')
            if client is not None and client.poll() is not None:
                raise RuntimeError('Client stopped before validation completed: exit ' + str(client.returncode))
            time.sleep(.25)
        raise RuntimeError('Timed out waiting for native QA state')
    def send(commands):
        server.stdin.write('\n'.join(commands) + '\n')
        server.stdin.flush()
    try:
        for attempt in range(40):
            try:
                with socket.create_connection(('127.0.0.1', 6093), .2):
                    break
            except OSError:
                time.sleep(.2)
        else:
            raise RuntimeError('Virtual display unavailable')
        server = subprocess.Popen(server_command, cwd=SERVER, stdin=subprocess.PIPE,
                                  stdout=server_log, stderr=subprocess.STDOUT, text=True)
        wait_for(lambda: 'Done (' in (ANALYSIS / 'prototype-server.log').read_text())
        commands = ['forceload add -16 -16 16 16', 'gamerule doDaylightCycle false', 'time set noon',
                    'gamerule doWeatherCycle false', 'weather clear', 'setworldspawn 0 80 -6', 'gamerule spawnRadius 0',
                    'fill -6 79 -8 6 79 4 minecraft:stone']
        for i, x in enumerate([-2, 0, 2]):
            catalyst = 'amethyst_block' if i == 1 else 'gilded_blackstone'
            upgrade = ['nether_focus', 'end_focus', 'relay_amplifier'][i]
            commands += [f'setblock {x} 80 1 minecraft:iron_block', f'fill {x} 81 1 {x} 85 1 simpleradio:antenna',
                         f'setblock {x} 80 0 simpleradio_repeaters:relay[facing=north]',
                         f'setblock {x} 81 0 simpleradio_repeaters:channel_bank[facing=north]',
                         f'data merge block {x} 80 0 {{Catalyst:{{id:"minecraft:{catalyst}",Count:1b}},Attachment:{{id:"simpleradio_repeaters:{upgrade}",Count:1b}}}}']
            cards = ','.join(f'{{Slot:{slot}b,id:"simpleradio_repeaters:frequency_card",Count:1b,tag:{{frequency:"000.0{slot+1}",modulation:"AM"}}}}' for slot in range(6))
            commands += [f'data merge block {x} 81 0 {{Items:[{cards}]}}']
        send(commands)
        time.sleep(2)
        client = subprocess.Popen(client_command, cwd=PROJECT / 'run', env=env, stdout=client_log, stderr=subprocess.STDOUT)
        wait_for(lambda: (QA / 'joined.ready').exists())
        send(['tp RelaySocketQA -1.5 80 -3 0 2', 'item replace entity RelaySocketQA hotbar.0 with minecraft:amethyst_block',
              'item replace entity RelaySocketQA hotbar.1 with minecraft:air'])
        print('Native client joined production prototype server; checking socket light and catalyst replacement.', flush=True)
        wait_for(lambda: (QA / 'amethyst-installed-and-model-resolved.ready').exists())
        send(['tp RelaySocketQA 0.5 80 -6 0 2'])
        wait_for(lambda: (QA / 'pass.ready').exists())
        assert client.wait(timeout=25) == 0
        send(['radiorepeaters list', 'stop'])
        assert server.wait(timeout=35) == 0
        for name in ['world-client.log', 'prototype-server.log']:
            content = (ANALYSIS / name).read_text()
            assert not any(s in content for s in ['NoSuchMethodError', 'NoClassDefFoundError', 'VerifyError', 'MixinApplyError', 'Exception in thread']), name
        assert (PROJECT / 'run/screenshots/Relay-Sockets-0.4.4-In-World.png').is_file()
        result = {'passed': True, 'version': '0.4.4', 'prototype_sha256': hashlib.sha256(PROTOTYPE.read_bytes()).hexdigest(),
                  'environment': 'Production Fabric dedicated server with exact prototype JAR and -Xverify:all; native development client using production renderers',
                  'checks': [p.stem for p in QA.glob('*.ready')],
                  'screenshot': 'Relay-Sockets-0.4.4-In-World.png',
                  'scope': 'Real chunk/update packets, front-cell vs housing light, socket removal and amethyst insertion, native item models; no voice playback.'}
        (ANALYSIS / 'world-render-result.json').write_text(json.dumps(result, indent=2) + '\n')
        print(json.dumps(result, indent=2), flush=True)
    finally:
        if client is not None and client.poll() is None:
            client.terminate()
        if server is not None and server.poll() is None:
            try:
                send(['stop']); server.wait(timeout=30)
            except (BrokenPipeError, subprocess.TimeoutExpired):
                server.terminate()
        display.terminate(); display.wait(timeout=10)
        (PROJECT / 'run/mods/socket-visual-qa.jar').unlink(missing_ok=True)
