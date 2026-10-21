"""Check that test-free packaging preserves every runtime entry, including Loom's manifest."""
from pathlib import Path
import base64
import hashlib
import json
import re
import zipfile

ROOT=Path(__file__).resolve().parents[1]
LIBS=ROOT/'build/libs'
version=re.search(r"version = '([^']+)'", (ROOT/'build.gradle').read_text()).group(1)
prefix='simple-radio-directional-antenna-fabric-1.20.1-'+version
full=LIBS/(prefix+'.jar')
prototype=LIBS/(prefix+'-prototype.jar')
with zipfile.ZipFile(full) as a,zipfile.ZipFile(prototype) as b:
    original={n:a.read(n) for n in a.namelist() if not n.endswith('/')}
    runtime={n:b.read(n) for n in b.namelist() if not n.endswith('/')}
    removed={n for n in original if n.startswith('dev/paul/simpleradiodirectional/gametest/')}
    assert removed and all(n not in runtime for n in removed)
    assert set(runtime)==set(original)-removed
    assert len(b.namelist())==len(set(b.namelist())), 'Duplicate ZIP paths'
    for n in runtime:
        if n!='fabric.mod.json':assert runtime[n]==original[n],n
    metadata=json.loads(original['fabric.mod.json']); metadata['entrypoints'].pop('fabric-gametest')
    assert json.loads(runtime['fabric.mod.json'])==metadata
    assert 'simpleradio_repeaters' not in metadata['depends']
    assert all(not n.startswith('dev/paul/simpleradiorepeaters/') for n in runtime)
    assert not any('preview/' in n or 'NoUnixSockets' in n or 'task/buildhost' in n for n in runtime)

kit=ROOT/'art/model-kit'
for path in kit.glob('*.bbmodel'):
    model=json.loads(path.read_text())
    assert model['meta']['model_format']=='java_block'
    assert model['elements'] and model['textures']
    for texture in model['textures']:
        assert base64.b64decode(texture['source'].split(',',1)[1]).startswith(b'\x89PNG\r\n\x1a\n')
    for element in model['elements']:
        assert all(-16<=v<=32 for v in element['from']+element['to'])
        for face in element['faces'].values():
            assert face['texture'] is None or 0<=face['texture']<len(model['textures'])
assert len(list(kit.glob('Native-Antenna-*.bbmodel')))==2
assert not list(kit.rglob('*top*.json'))
assert (kit/'Wide-Angle-Receiver.bbmodel').is_file()
assert len(list(kit.glob('*.bbmodel')))==4
model=json.loads((ROOT/'src/main/resources/assets/simpleradio_directional/models/block/parabolic_receiver.json').read_text())
assert all(-16<=v<=32 for e in model['elements'] for v in e['from']+e['to'])

for tag in ['mineable/pickaxe','needs_stone_tool']:
    values=json.loads((ROOT/f'src/main/resources/data/minecraft/tags/blocks/{tag}.json').read_text())['values']
    assert 'simpleradio_directional:wide_angle_receiver' in values and 'simpleradio_directional:wide_angle_part' in values

report={'passed':True,'jar':prototype.name,'bytes':prototype.stat().st_size,
        'sha256':hashlib.sha256(prototype.read_bytes()).hexdigest(),
        'runtime_classes':sum(n.endswith('.class') for n in runtime),'removed_fixture_classes':len(removed),
        'retained_runtime_entries_byte_identical':len(runtime)-1,'loom_manifest_preserved':True,
        'no_obfuscation':True,'optional_repeaters_not_bundled_or_required':True,
        'blockbench_models':4,'embedded_texture_validation':True,'top_references_excluded':True,
        'wide_array_mining_tags_valid':True,'capture_helpers_not_bundled':True}
output=ROOT/f'validation/{version}/prototype-verification.json'
output.parent.mkdir(parents=True,exist_ok=True);output.write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))
