"""Package the cleaned receivers, native verification evidence and reproducible source."""
from pathlib import Path
import hashlib
import json
import shutil
import xml.etree.ElementTree as ET
import zipfile

HERE=Path(__file__).resolve().parent
MODULE=HERE.parents[1]
PROJECT=MODULE.parent
WORKSPACE=PROJECT.parents[2]
DELIVERY=WORKSPACE/'delivery'
DELIVERY.mkdir(exist_ok=True)


def sha(data): return hashlib.sha256(data).hexdigest()


baseline={}
with zipfile.ZipFile(WORKSPACE/'Simple-Radio-Directional-Antenna-0.2.4-Source.zip') as old:
    prefix='simple-radio-addons/directional/'
    for name in old.namelist():
        if name.startswith(prefix+'src/main/java/') and name.endswith('.java'):
            baseline[name[len(prefix):]]=sha(old.read(name))
    for module in ['core','foci']:
        for name in old.namelist():
            if name.startswith('simple-radio-addons/'+module+'/src/') and not name.endswith('/'):
                assert (PROJECT/Path(name).relative_to('simple-radio-addons')).read_bytes()==old.read(name)
changed=sorted(name for name,h in baseline.items() if sha((MODULE/name).read_bytes())!=h)
expected=sorted('src/main/java/dev/paul/simpleradiodirectional/'+name for name in [
    'block/DishBlockEntity.java','block/DishGeometry.java','block/ParabolicReceiverBlock.java','gametest/DirectionalGameTests.java'])
assert changed==expected,changed
(HERE/'baseline-source-sha256.json').write_text(json.dumps(baseline,indent=2)+'\n')
results=[ET.parse(p).getroot() for p in (MODULE/'build/test-results/test').glob('TEST-*.xml')]
assert results and all(int(t.attrib['failures'])==int(t.attrib['errors'])==0 for t in results)
unit_count=sum(int(t.attrib['tests']) for t in results);assert unit_count==18
tests=ET.parse(HERE/'shared-mount-gametests.xml').getroot().findall('.//testcase')
assert len(tests)==2 and all(t.find('failure') is None and t.find('error') is None for t in tests)
scope={'passed':True,'runtime_source_files_compared':len(baseline),'changed_sources':changed,
       'radio_reception_scanning_and_optional_integration_sources_unchanged':True,
       'core_and_foci_sources_unchanged':True,'existing_unit_tests_passed':unit_count,'focused_native_gametests_passed':2,
       'shared_mount_uses_baked_blockstates':True,'no_new_runtime_renderer_or_per_frame_world_lookup':True}
(HERE/'change-scope.json').write_text(json.dumps(scope,indent=2)+'\n')
for p in (MODULE/'build/test-results/test').glob('TEST-*.xml'):shutil.copy2(p,HERE/p.name)

jar=MODULE/'build/libs/simple-radio-directional-antenna-fabric-1.20.1-0.2.5-prototype.jar'
assert json.loads((HERE/'prototype-verification.json').read_text())['sha256']==sha(jar.read_bytes())
shutil.copy2(jar,DELIVERY/jar.name)
captures=json.loads((HERE/'native-capture.json').read_text())
assert captures['passed'] and len(captures['screenshots'])==7
images=[]
for record in captures['screenshots']:
    p=MODULE/'art'/record['name'];assert sha(p.read_bytes())==record['sha256']
    shutil.copy2(p,DELIVERY/p.name);images.append(p)

kit=DELIVERY/'Directional-Receivers-0.2.5-Model-Kit.zip'
with zipfile.ZipFile(kit,'w',zipfile.ZIP_DEFLATED) as z:
    root=Path('Directional-Receivers-0.2.5-Model-Kit')
    for p in sorted((MODULE/'art/model-kit').rglob('*')):
        if p.is_file():z.write(p,root/p.relative_to(MODULE/'art/model-kit'))
    for directory in ['assets/simpleradio_directional/models','assets/simpleradio_directional/textures','resourcepacks']:
        for p in sorted((MODULE/'src/main/resources'/directory).rglob('*')):
            if p.is_file():z.write(p,root/'java-resources'/p.relative_to(MODULE/'src/main/resources'))
    for p in images:z.write(p,root/'native-captures'/p.name)

source=DELIVERY/'Simple-Radio-Directional-Antenna-0.2.5-Source.zip'
excluded={'build','.gradle','run','classes','__pycache__','.git','.codex'}
with zipfile.ZipFile(source,'w',zipfile.ZIP_DEFLATED) as z:
    for p in sorted(PROJECT.rglob('*')):
        rel=p.relative_to(PROJECT)
        if p.is_file() and not set(rel.parts)&excluded and p.suffix not in {'.pyc','.class'}:
            z.write(p,Path('simple-radio-addons')/rel)
for p in [jar,kit,source]:
    with zipfile.ZipFile(p) as z:assert z.testzip() is None
manifest=[{'name':p.name,'bytes':p.stat().st_size,'sha256':sha(p.read_bytes())} for p in [jar,kit,source]+images]
(DELIVERY/'Directional-Receivers-0.2.5-Deliverables.json').write_text(json.dumps(manifest,indent=2)+'\n')
print(json.dumps({'scope':scope,'deliverables':manifest},indent=2))
