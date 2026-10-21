"""Check that test-free packaging preserves every runtime entry, including Loom's manifest."""
from pathlib import Path
import base64
from copy import deepcopy
import hashlib
import json
import math
import re
import zipfile
from PIL import Image

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
assert (kit/'Parabolic-Receiver.bbmodel').is_file()
assert (kit/'Parabolic-Receiver-Refined.bbmodel').is_file()
assert (kit/'Parabolic-Receiver-Octagonal.bbmodel').is_file()
assert (kit/'Parabolic-Receiver-Mesh.bbmodel').is_file()
assert len(list(kit.glob('*.bbmodel')))==7
model=json.loads((ROOT/'src/main/resources/assets/simpleradio_directional/models/block/parabolic_receiver.json').read_text())
refined=json.loads((ROOT/'src/main/resources/assets/simpleradio_directional/models/block/parabolic_receiver_refined.json').read_text())
octagonal=json.loads((ROOT/'src/main/resources/assets/simpleradio_directional/models/block/parabolic_receiver_octagonal.json').read_text())
mesh=json.loads((ROOT/'src/main/resources/assets/simpleradio_directional/models/block/parabolic_receiver_mesh.json').read_text())
assert all(-16<=v<=32 for e in model['elements'] for v in e['from']+e['to'])
for element in model['elements'] + refined['elements'] + octagonal['elements'] + mesh['elements']:
    x,y,z=[b-a for a,b in zip(element['from'],element['to'])]
    dimensions={'north':(x,y),'south':(x,y),'east':(z,y),'west':(z,y),'up':(x,z),'down':(x,z)}
    assert not element.get('rotation',{}).get('rescale',False)
    for face,value in element['faces'].items():
        u0,v0,u1,v1=value['uv']
        assert math.isclose(abs(u1-u0),dimensions[face][0],abs_tol=1e-8),(element['name'],face)
        assert math.isclose(abs(v1-v0),dimensions[face][1],abs_tol=1e-8),(element['name'],face)
        assert all(0<=v<=16 for v in value['uv']),(element['name'],face)
for texture in {v for v in model['textures'].values() if not v.startswith('#')}:
    namespace,path=texture.split(':',1)
    name=f'assets/{namespace}/textures/{path}.png'
    raw=runtime[name] if namespace=='simpleradio_directional' else (kit/'native-reference'/name).read_bytes()
    assert int.from_bytes(raw[16:20],'big')==16 and int.from_bytes(raw[20:24],'big')==16
array=json.loads((ROOT/'src/main/resources/assets/simpleradio_directional/models/block/wide_angle_receiver.json').read_text())
assert [e for e in refined['elements'] if e['name'].startswith('mount_')]==[e for e in array['elements'] if e['name'].startswith('mount_')]
expected_hardware=deepcopy([e for e in mesh['elements'] if not e['name'].startswith('mesh_panel_')])
for e in expected_hardware:
    if e['name'] in {'mount_pivot_lower','mount_pivot_upper'}:
        e['from'][2]=10
        e['to'][2]+=4
assert [e for e in model['elements'] if e['name'].startswith('mount_')]==[e for e in expected_hardware if e['name'].startswith('mount_')]
for e in model['elements']:
    assert all(v==int(v) for v in e['from']+e['to'])
    assert all(b-a>=1 for a,b in zip(e['from'],e['to']))
    assert not e.get('rotation')
    assert all(v==int(v) for v in e.get('rotation',{}).get('origin',[8,8,8]))
assert mesh['textures']['mesh']=='simpleradio:block/antenna_column'
assert all(v.startswith('simpleradio_directional:') for v in model['textures'].values())
for name in ['solid_reflector','solid_backing','solid_frame']:
    image=Image.open(ROOT/f'src/main/resources/assets/simpleradio_directional/textures/block/{name}.png')
    assert image.size==(16,16) and image.getextrema()[3]==(255,255)
panels=[e for e in mesh['elements'] if e['name'].startswith('mesh_panel_')]
reference=json.loads((kit/'original-reference/Parabolic-Receiver-Original.json').read_text())
original_panels=[e for e in reference['elements'] if e['name'].startswith('mesh_')]
assert len(panels)==len(original_panels)==32
shape=lambda e:tuple(e['from'][:2]+e['to'][:2])
assert {shape(e) for e in panels}=={shape(e) for e in original_panels}
for panel in panels:
    original_panel=next(e for e in original_panels if shape(e)==shape(panel))
    assert panel['from'][2]==original_panel['from'][2]-6
    assert panel['faces']['north']['uv']==original_panel['faces']['north']['uv']
solid_panels=[e for e in model['elements'] if e['name'].startswith('solid_panel_')]
assert len(solid_panels)==32
for solid_panel in solid_panels:
    mesh_panel=next(e for e in panels if shape(e)==shape(solid_panel))
    assert solid_panel['from']==mesh_panel['from']
    assert solid_panel['to'][:2]==mesh_panel['to'][:2]
    assert solid_panel['to'][2]-solid_panel['from'][2]==3
    for adjacent in solid_panels:
        if sum(abs(a-b) for a,b in zip(solid_panel['from'][:2],adjacent['from'][:2]))==8:
            assert max(solid_panel['from'][2],adjacent['from'][2])<=min(solid_panel['to'][2],adjacent['to'][2])
assert [e for e in model['elements'] if not e['name'].startswith('solid_panel_')]==expected_hardware
for style,alternate in [('refined',refined),('octagonal',octagonal),('mesh',mesh)]:
    pack=f'resourcepacks/{style}_parabolic/'
    assert json.loads(runtime[pack+'pack.mcmeta'])['pack']['pack_format']==15
    assert json.loads(runtime[pack+'assets/simpleradio_directional/models/block/parabolic_receiver.json'])==alternate
    for suffix,fixed in [('_mount',True),('_head',False)]:
        packaged=json.loads(runtime[pack+f'assets/simpleradio_directional/models/block/parabolic_receiver{suffix}.json'])
        assert packaged['elements']==[e for e in alternate['elements'] if e['name'].startswith('mount_')==fixed]
for suffix,fixed in [('_mount',True),('_head',False)]:
    packaged=json.loads(runtime[f'assets/simpleradio_directional/models/block/parabolic_receiver{suffix}.json'])
    assert packaged['elements']==[e for e in model['elements'] if e['name'].startswith('mount_')==fixed]

for tag in ['mineable/pickaxe','needs_stone_tool']:
    values=json.loads((ROOT/f'src/main/resources/data/minecraft/tags/blocks/{tag}.json').read_text())['values']
    assert 'simpleradio_directional:wide_angle_receiver' in values and 'simpleradio_directional:wide_angle_part' in values

report={'passed':True,'jar':prototype.name,'bytes':prototype.stat().st_size,
        'sha256':hashlib.sha256(prototype.read_bytes()).hexdigest(),
        'runtime_classes':sum(n.endswith('.class') for n in runtime),'removed_fixture_classes':len(removed),
        'retained_runtime_entries_byte_identical':len(runtime)-1,'loom_manifest_preserved':True,
        'no_obfuscation':True,'optional_repeaters_not_bundled_or_required':True,
        'blockbench_models':7,'embedded_texture_validation':True,'top_references_excluded':True,
        'wide_array_mining_tags_valid':True,'capture_helpers_not_bundled':True,
        'dish_uv_pixels_per_model_unit':1,'dish_material_pixels_per_block':16,
        'dish_faces_checked':sum(len(e['faces']) for e in model['elements']),
        'refined_faces_checked':sum(len(e['faces']) for e in refined['elements']),
        'solid_whole_unit_geometry':True,'solid_orthogonal_only':True,
        'smallest_extent_at_least_one_unit':True,'original_32_panel_outline_and_concavity_preserved':True,
        'solid_shell_has_no_gaps_between_steps':True,'new_materials_fully_opaque':True,
        'mesh_alternate_preserves_native_antenna_texture':True,'refined_mount_matches_array':True,
        'all_three_alternate_packs_included':True,'authored_dish_matches_packaged_layers':True}
output=ROOT/f'validation/{version}/prototype-verification.json'
output.parent.mkdir(parents=True,exist_ok=True);output.write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))
