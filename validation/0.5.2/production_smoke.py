"""Start the exact core prototype with production mappings and activate all three repaired devices."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import time
from zipfile import ZipFile

parser = argparse.ArgumentParser()
parser.add_argument('--cache', required=True, type=Path)
parser.add_argument('--java', required=True, type=Path)
parser.add_argument('--server-directory', required=True, type=Path)
parser.add_argument('--agent', type=Path)
args = parser.parse_args()
project = Path(__file__).resolve().parents[2]
version = next(line.split('=', 1)[1] for line in (project / 'gradle.properties').read_text().splitlines()
               if line.startswith('mod_version='))
cache = args.cache / 'caches/modules-2/files-2.1'

def jar(group, artifact, version=None):
    folder = cache / group / artifact
    if version:
        folder /= version
    files = [file for file in folder.rglob('*.jar') if '-sources' not in file.name and '-javadoc' not in file.name]
    assert len(files) == 1, (group, artifact, files)
    return files[0]

loader = jar('net.fabricmc', 'fabric-loader', '0.19.3')
with ZipFile(loader) as archive:
    installer = json.loads(archive.read('fabric-installer.json'))
classpath = [loader]
for library in installer['libraries']['common']:
    classpath.append(jar(*library['name'].split(':')))
classpath += [jar('net.fabricmc', 'intermediary', '1.20.1'), args.cache / 'caches/fabric-loom/1.20.1/minecraft-server.jar']
server = args.server_directory
assert not server.exists(), 'Use a new disposable server directory for this check'
mods = server / 'mods'
mods.mkdir(parents=True)
for group, artifact in [('maven.modrinth', 'simple-radio'), ('maven.modrinth', 'lexiconfig'),
                        ('maven.modrinth', 'simple-voice-chat'), ('net.fabricmc.fabric-api', 'fabric-api')]:
    source = jar(group, artifact)
    shutil.copyfile(source, mods / source.name)
prototype = project / f'core/build/libs/simple-radio-repeaters-fabric-1.20.1-{version}-prototype.jar'
shutil.copyfile(prototype, mods / prototype.name)
(server / 'eula.txt').write_text('eula=true\n')
(server / 'server.properties').write_text('''server-ip=127.0.0.1
server-port=25575
online-mode=false
enforce-secure-profile=false
level-name=reload-smoke
level-type=minecraft:flat
generate-structures=false
view-distance=2
simulation-distance=2
spawn-protection=0
max-players=1
''')
command = [str(args.java), '-Xmx2G', '-XX:ActiveProcessorCount=4', '-Xverify:all',
           '-Djava.awt.headless=true', '-Dfabric.development=false']
if args.agent:
    command.append('-javaagent:' + str(args.agent))
command += ['-cp', os.pathsep.join(map(str, classpath)), 'net.fabricmc.loader.impl.launch.knot.KnotServer', 'nogui']
env = os.environ.copy()
env.pop('JAVA_TOOL_OPTIONS', None)
log = Path(__file__).with_name('core-only-production.log')
with log.open('w') as output:
    process = subprocess.Popen(command, cwd=server, env=env, stdin=subprocess.PIPE, stdout=output,
                               stderr=subprocess.STDOUT, text=True)
    def wait_for(text, start=0, timeout=60):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            content = log.read_text()
            if text in content[start:]:
                return content
            if process.poll() is not None:
                raise RuntimeError('Production server exited; inspect core-only-production.log')
            time.sleep(.2)
        raise RuntimeError('Timed out waiting for ' + text)
    def send(commands):
        mark = len(log.read_text())
        process.stdin.write('\n'.join(commands) + '\n')
        process.stdin.flush()
        return mark
    try:
        wait_for('Done (')
        mark = send(['forceload add 3200 0',
                     'setblock 3200 80 0 minecraft:iron_block',
                     'setblock 3200 81 0 simpleradio:antenna',
                     'setblock 3200 80 1 simpleradio_repeaters:relay[facing=south]',
                     'setblock 3200 81 1 simpleradio_repeaters:channel_bank[facing=south]',
                     'data merge block 3200 80 1 {Catalyst:{id:"minecraft:gilded_blackstone",Count:1b}}',
                     'data merge block 3200 81 1 {Items:[{Slot:0b,id:"simpleradio_repeaters:frequency_card",Count:1b,tag:{frequency:"000.01",modulation:"AM"}},{Slot:1b,id:"simpleradio_repeaters:frequency_card",Count:1b,tag:{frequency:"000.02",modulation:"AM"}}]}',
                     'setblock 3203 80 0 simpleradio:radio',
                     'data merge block 3203 80 0 {frequency:"000.02",modulation:"AM"}',
                     'setblock 3204 80 0 simpleradio:receiver',
                     'data merge block 3204 80 0 {frequency:"000.02",modulation:"AM"}',
                     'setblock 3205 80 0 simpleradio:transmitter',
                     'data merge block 3205 80 0 {frequency:"000.01",modulation:"AM"}'])
        for kind in ['radio', 'receiver', 'transmitter']:
            wait_for('Activating ' + kind + ' with reference', start=mark, timeout=25)
        time.sleep(2)
        mark = send(['radiorepeaters list'])
        wait_for('1 ready (', start=mark, timeout=25)
        send(['stop'])
        code = process.wait(timeout=35)
        assert code == 0, code
        content = log.read_text()
        for failure in ['NoSuchMethodError', 'NoClassDefFoundError', 'VerifyError', 'MixinApplyError', 'InvalidMixinException']:
            assert failure not in content, failure
        result = {'passed': True, 'exit_code': code, 'environment': 'Fabric production server; development=false; -Xverify:all',
                  'jar_sha256': hashlib.sha256(prototype.read_bytes()).hexdigest(),
                  'checks': ['Exact core prototype loaded without Foci', 'Relay and bank verified',
                             'Native radio, wired receiver and transmitter activated with the new mixin', 'Clean shutdown'],
                  'limits': 'No client or microphone; separate-registry cleanup, activity notifications and frequency-GC regressions use GameTest.'}
        Path(__file__).with_name('production-smoke.json').write_text(json.dumps(result, indent=2) + '\n')
        print(json.dumps(result), flush=True)
    finally:
        if process.poll() is None:
            try:
                send(['stop'])
                process.wait(timeout=20)
            except (BrokenPipeError, subprocess.TimeoutExpired):
                process.terminate()
