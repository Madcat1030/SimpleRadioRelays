"""Collect the completed native client run from Minecraft's own log and raw PNGs."""
from pathlib import Path
import hashlib
import json
import shutil
import xml.etree.ElementTree as ET

HERE=Path(__file__).resolve().parent
PROJECT=HERE.parents[2]
NAMES=['Parabolic-Receiver-0.2.5-Native-Preview.png','Wide-Angle-Receiver-0.2.5-Native-Preview.png',
       'Directional-Receivers-0.2.5-Shared-Mounts.png','Directional-Receivers-0.2.5-Aiming.png',
       'Parabolic-Receiver-0.2.5-Mesh-Alternate.png','Parabolic-Receiver-0.2.5-Octagonal-Alternate.png',
       'Parabolic-Receiver-0.2.5-Refined-Alternate.png']


def collect():
    native_log=PROJECT/'directional/run/logs/latest.log'
    log=native_log.read_text()
    assert 'MOUNT_NATIVE_PREVIEW verified seven aims for both receiver types' in log
    assert all(f'ALTERNATE_PACK verified native resource-pack reload: {style}_parabolic' in log for style in ['mesh','octagonal','refined'])
    assert 'Stopping!' in log
    report=PROJECT/'directional/run/gametest/reports/gametest-report.xml'
    tests=ET.parse(report).getroot().findall('.//testcase')
    assert len(tests)==2 and all('shared_mount_' in t.attrib['name'] and t.find('failure') is None and t.find('error') is None for t in tests)
    shutil.copyfile(report,HERE/'shared-mount-gametests.xml')
    shutil.copyfile(native_log,HERE/'minecraft-client.log')
    results=[]
    for name in NAMES:
        source=PROJECT/'directional/run/screenshots'/name
        assert source.is_file() and f'Saved screenshot as {name}' in log
        shutil.copyfile(source,PROJECT/'directional/art'/name)
        results.append({'name':name,'sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'bytes':source.stat().st_size})
    result={'passed':True,'renderer':'Minecraft Java 1.20.1 native baked block renderer',
            'capture':'Screenshot.grab of the real client framebuffer; PNGs are unretouched',
            'checks':['Both receiver types baked at all seven aims','Shared state removes exactly one ring pair',
                      'Native mast rendered with opposite receiver supports','All three actual alternate packs reloaded',
                      'Two focused native shared-mount GameTests passed'],
            'screenshots':results,'scope':'Native model sheets and focused server checks; no live microphone playback'}
    (HERE/'native-capture.json').write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps(result,indent=2),flush=True)


if __name__=='__main__':collect()
