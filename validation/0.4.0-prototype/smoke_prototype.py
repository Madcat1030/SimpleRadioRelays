import json,os,shutil,subprocess,time
from pathlib import Path
from zipfile import ZipFile
ROOT=Path('/workspace/scratch/b97ec851c650')
PROJECT=ROOT/'radio-card-controls/work/simple-radio-repeaters'
TASK=ROOT/'radio-prototype-distribution'
CACHE=ROOT/'toolchain/gradle-cache/caches/modules-2/files-2.1'
SERVER=TASK/'analysis/production-server'
SERVER.mkdir(parents=True,exist_ok=True)
if (SERVER/'prototype-smoke').exists(): shutil.rmtree(SERVER/'prototype-smoke')
def jar(group,artifact,version=None):
    folder=CACHE/group/artifact
    if version:folder/=version
    found=[p for p in folder.rglob('*.jar') if not any(x in p.name for x in ['-sources','-javadoc'])]
    assert len(found)==1,(group,artifact,found)
    return found[0]
loader=jar('net.fabricmc','fabric-loader','0.19.3')
with ZipFile(loader) as z: installer=json.loads(z.read('fabric-installer.json'))
classpath=[loader]
for lib in installer['libraries']['common']:
    group,artifact,version=lib['name'].split(':');classpath.append(jar(group,artifact,version))
classpath.append(jar('net.fabricmc','intermediary','1.20.1'))
classpath.append(ROOT/'toolchain/gradle-cache/caches/fabric-loom/1.20.1/minecraft-server.jar')
mods=SERVER/'mods';mods.mkdir(exist_ok=True)
for group,artifact in [('maven.modrinth','simple-radio'),('maven.modrinth','lexiconfig'),('maven.modrinth','simple-voice-chat'),('net.fabricmc.fabric-api','fabric-api')]:
    p=jar(group,artifact);shutil.copyfile(p,mods/p.name)
prototype=PROJECT/'build/libs/simple-radio-repeaters-fabric-1.20.1-0.4.0-prototype.jar'
shutil.copyfile(prototype,mods/prototype.name)
(SERVER/'eula.txt').write_text('eula=true\n')
(SERVER/'server.properties').write_text('''server-ip=127.0.0.1
server-port=25574
online-mode=false
enforce-secure-profile=false
level-name=prototype-smoke
level-type=minecraft:flat
generate-structures=false
view-distance=2
simulation-distance=2
spawn-protection=0
max-players=1
''')
command=[str(ROOT/'toolchain/jdk-21.0.2/bin/java'),'-Xmx2G','-Xverify:all','-Djava.awt.headless=true',
         '-Dfabric.development=false','-cp',os.pathsep.join(map(str,classpath)),
         'net.fabricmc.loader.impl.launch.knot.KnotServer','nogui']
log=TASK/'analysis/prototype-server.log'
with log.open('w') as out:
    process=subprocess.Popen(command,cwd=SERVER,stdin=subprocess.PIPE,stdout=out,stderr=subprocess.STDOUT,text=True)
    def wait_for(text,timeout=120,start=0):
        end=time.monotonic()+timeout
        while time.monotonic()<end:
            content=log.read_text()
            if text in content[start:]: return content
            if process.poll() is not None: raise RuntimeError('Production server exited early; see prototype-server.log')
            time.sleep(.2)
        raise RuntimeError('Timed out waiting for '+text)
    def send(commands):
        mark=len(log.read_text());process.stdin.write('\n'.join(commands)+'\n');process.stdin.flush();return mark
    try:
        wait_for('Done (')
        assert 'Simple Radio Repeaters ready' in log.read_text()
        mark=send(['forceload add 0 0','setblock 0 80 0 minecraft:iron_block','setblock 0 81 0 simpleradio:antenna',
            'setblock 0 80 1 simpleradio_repeaters:relay[facing=south]','setblock 0 81 1 simpleradio_repeaters:channel_bank[facing=south]',
            'data merge block 0 80 1 {Catalyst:{id:"minecraft:gilded_blackstone",Count:1b}}',
            'data merge block 0 81 1 {Items:[{Slot:0b,id:"simpleradio_repeaters:frequency_card",Count:1b,tag:{frequency:"000.01",modulation:"AM"}}]}'])
        time.sleep(2)
        mark=send(['radiorepeaters list']);wait_for('1 ready (',timeout=25,start=mark)
        assert '| bank |' in log.read_text()[mark:]
        mark=send(['setblock 0 81 1 simpleradio_repeaters:transponder[facing=south]',
            'data merge block 0 81 1 {Duplex:1b,Items:[{Slot:0b,id:"simpleradio_repeaters:frequency_card",Count:1b,tag:{frequency:"000.01",modulation:"AM"}},{Slot:1b,id:"simpleradio_repeaters:frequency_card",Count:1b,tag:{frequency:"000.02",modulation:"AM"}}]}',
            'data merge block 0 80 1 {Attachment:{id:"simpleradio_repeaters:relay_amplifier",Count:1b}}'])
        time.sleep(2)
        mark=send(['radiorepeaters list']);wait_for('| AMPLIFIER | duplex |',timeout=25,start=mark)
        assert '1 ready (' in log.read_text()[mark:]
        mark=send(['data merge block 0 80 1 {Attachment:{id:"simpleradio_repeaters:end_focus",Count:1b}}','radiorepeaters refresh'])
        time.sleep(2)
        mark=send(['radiorepeaters list']);wait_for('| END_FOCUS | duplex |',timeout=25,start=mark)
        send(['stop']);code=process.wait(timeout=35)
        assert code==0,code
        content=log.read_text()
        for failure in ['NoSuchMethodError','NoClassDefFoundError','VerifyError','MixinApplyError','Exception in thread']:
            assert failure not in content,failure
        result={'passed':True,'exit_code':code,'environment':'Production Fabric server, development=false, JVM -Xverify:all',
                'checks':['Exact prototype JAR loaded from mods folder','Relay/Bank verified a supported antenna and native catalyst',
                          'Transponder duplex pair, amplifier and End focus loaded and published','No missing methods/classes or mixin/verification errors','Clean shutdown'],
                'scope':'No client join or microphone playback; retained bytecode checked separately.'}
        (TASK/'analysis/production-smoke-result.json').write_text(json.dumps(result,indent=2)+'\n')
        print(json.dumps(result,indent=2),flush=True)
    finally:
        if process.poll() is None:
            try:send(['stop']);process.wait(timeout=20)
            except (BrokenPipeError,subprocess.TimeoutExpired):process.terminate()
