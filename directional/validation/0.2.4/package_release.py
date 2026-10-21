"""Collect the visual prototype, editable kit, native captures and reproducible source."""
from pathlib import Path
import hashlib
import json
import shutil
import xml.etree.ElementTree as ET
import zipfile

HERE = Path(__file__).resolve().parent
MODULE = HERE.parents[1]
PROJECT = MODULE.parent
WORKSPACE = PROJECT.parents[2]
DELIVERY = WORKSPACE / 'delivery'
DELIVERY.mkdir(exist_ok=True)


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


baseline = json.loads((HERE / 'baseline-source-sha256.json').read_text())
changed = [name for name, sha in baseline.items() if digest(MODULE / name) != sha]
assert changed == ['src/main/java/dev/paul/simpleradiodirectional/client/DirectionalClient.java']
with zipfile.ZipFile(WORKSPACE / 'Simple-Radio-Directional-Antenna-0.2.3-Source.zip') as previous:
    resources = 'directional/src/main/resources/'
    base = 'assets/simpleradio_directional/models/block/'
    for name in ['parabolic_receiver_octagonal.json', 'parabolic_receiver_refined.json', 'wide_angle_receiver.json']:
        assert (MODULE / 'src/main/resources' / base / name).read_bytes() == previous.read('simple-radio-addons/' + resources + base + name)
    assert (MODULE / 'src/main/resources' / base / 'parabolic_receiver_mesh.json').read_bytes() == previous.read('simple-radio-addons/' + resources + base + 'parabolic_receiver.json')
    for module in ['core', 'foci']:
        for name in previous.namelist():
            if name.startswith(f'simple-radio-addons/{module}/src/') and not name.endswith('/'):
                assert (PROJECT / Path(name).relative_to('simple-radio-addons')).read_bytes() == previous.read(name)

tests = [ET.parse(p).getroot() for p in (MODULE / 'build/test-results/test').glob('TEST-*.xml')]
assert tests and all(int(t.attrib['failures']) == 0 and int(t.attrib['errors']) == 0 for t in tests)
count = sum(int(t.attrib['tests']) for t in tests)
assert count == 18
scope = {'passed': True, 'runtime_source_files_checked': len(baseline), 'changed_runtime_sources': changed,
         'radio_and_server_sources_unchanged': True, 'core_and_foci_sources_unchanged': True,
         'all_three_previous_dish_models_preserved_byte_identically': True,
         'wide_angle_model_preserved_byte_identically': True, 'unit_tests_passed': count}
(HERE / 'change-scope.json').write_text(json.dumps(scope, indent=2) + '\n')
for p in (MODULE / 'build/test-results/test').glob('TEST-*.xml'):
    shutil.copy2(p, HERE / p.name)

jar = MODULE / 'build/libs/simple-radio-directional-antenna-fabric-1.20.1-0.2.4-prototype.jar'
with zipfile.ZipFile(jar) as z:
    assert z.testzip() is None and 'fabric.mod.json' in z.namelist()
shutil.copy2(jar, DELIVERY / jar.name)

images = sorted((MODULE / 'art').glob('Parabolic-Receiver-0.2.4-*.png'))
assert len(images) == 6
for p in images:
    shutil.copy2(p, DELIVERY / p.name)

kit = DELIVERY / 'Parabolic-Receiver-Solid-0.2.4-Model-Kit.zip'
with zipfile.ZipFile(kit, 'w', zipfile.ZIP_DEFLATED) as z:
    for p in sorted((MODULE / 'art/model-kit').rglob('*')):
        if p.is_file():
            z.write(p, Path('Directional-Antenna-0.2.4-Model-Kit') / p.relative_to(MODULE / 'art/model-kit'))
    for directory in ['assets/simpleradio_directional/models', 'assets/simpleradio_directional/textures', 'resourcepacks']:
        for p in sorted((MODULE / 'src/main/resources' / directory).rglob('*')):
            if p.is_file():
                z.write(p, Path('Directional-Antenna-0.2.4-Model-Kit/java-resources') / p.relative_to(MODULE / 'src/main/resources'))
    for p in images:
        z.write(p, Path('Directional-Antenna-0.2.4-Model-Kit/native-captures') / p.name)

source = DELIVERY / 'Simple-Radio-Directional-Antenna-0.2.4-Source.zip'
excluded = {'build', '.gradle', 'run', 'classes', '__pycache__', '.git', '.codex'}
with zipfile.ZipFile(source, 'w', zipfile.ZIP_DEFLATED) as z:
    for p in sorted(PROJECT.rglob('*')):
        rel = p.relative_to(PROJECT)
        if p.is_file() and not set(rel.parts) & excluded and p.suffix not in {'.pyc', '.class'}:
            z.write(p, Path('simple-radio-addons') / rel)
for p in [kit, source]:
    with zipfile.ZipFile(p) as z:
        assert z.testzip() is None
manifest = [{'name': p.name, 'bytes': p.stat().st_size, 'sha256': digest(p)} for p in [jar, kit, source] + images]
(DELIVERY / 'Directional-Antenna-0.2.4-Deliverables.json').write_text(json.dumps(manifest, indent=2) + '\n')
print(json.dumps({'scope': scope, 'deliverables': manifest}, indent=2))
