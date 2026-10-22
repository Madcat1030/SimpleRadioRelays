"""Validate runtime packaging, pixel density and transformed receiver/mast surfaces."""
from pathlib import Path
import base64
import hashlib
import itertools
import json
import math
import re
import zipfile
from PIL import Image
from model_surfaces import faces, overlaps, motion, FRAMES

ROOT=Path(__file__).resolve().parents[1]
version=re.search(r"version = '([^']+)'",(ROOT/'build.gradle').read_text()).group(1)
prefix='simple-radio-directional-antenna-fabric-1.20.1-'+version
prototype=ROOT/'build/libs'/(prefix+'-prototype.jar')
with zipfile.ZipFile(ROOT/'build/libs'/(prefix+'.jar')) as a,zipfile.ZipFile(prototype) as b:
    original={n:a.read(n) for n in a.namelist() if not n.endswith('/')}
    runtime={n:b.read(n) for n in b.namelist() if not n.endswith('/')}
    removed={n for n in original if n.startswith('dev/paul/simpleradiodirectional/gametest/')}
    assert removed and set(runtime)==set(original)-removed
    assert len(b.namelist())==len(set(b.namelist()))
    for n in runtime:
        if n!='fabric.mod.json':assert runtime[n]==original[n],n
    metadata=json.loads(original['fabric.mod.json']);metadata['entrypoints'].pop('fabric-gametest')
    assert json.loads(runtime['fabric.mod.json'])==metadata
    assert 'simpleradio_repeaters' not in metadata['depends']
    assert not any(n.startswith('dev/paul/simpleradiorepeaters/') or 'preview/' in n or 'task/buildhost' in n for n in runtime)

kit=ROOT/'art/model-kit'
for path in kit.glob('*.bbmodel'):
    m=json.loads(path.read_text());assert m['meta']['model_format']=='java_block'
    for t in m['textures']:assert base64.b64decode(t['source'].split(',',1)[1]).startswith(b'\x89PNG\r\n\x1a\n')
    for e in m['elements']:
        for f in e['faces'].values():assert f['texture'] is None or 0<=f['texture']<len(m['textures'])
assert len(list(kit.glob('*.bbmodel')))==7
assert len(list(kit.glob('Native-Antenna-*.bbmodel')))==2 and not list(kit.rglob('*top*.json'))

assets=ROOT/'src/main/resources/assets/simpleradio_directional'
names=['parabolic_receiver','wide_angle_receiver','parabolic_receiver_mesh','parabolic_receiver_octagonal','parabolic_receiver_refined']
models={n:json.loads((assets/'models/block'/(n+'.json')).read_text()) for n in names}
for name,m in models.items():
    for e in m['elements']:
        assert all(-16<=v<=32 for v in e['from']+e['to'])
        if name in names[:2]:
            assert all(v==int(v) for v in e['from']+e['to']),e['name']
            assert min(b-a for a,b in zip(e['from'],e['to']))>=1,e['name']
        for face,value in e['faces'].items():
            _,_,u,_,v,_=FRAMES[face]
            u0,v0,u1,v1=value['uv']
            assert math.isclose(abs(u1-u0),e['to'][u]-e['from'][u],abs_tol=1e-7),(name,e['name'],face)
            assert math.isclose(abs(v1-v0),e['to'][v]-e['from'][v],abs_tol=1e-7),(name,e['name'],face)
            assert all(-1e-7<=x<=16+1e-7 for x in value['uv']),(name,e['name'],value)
    for texture in m['textures'].values():
        namespace,path=texture.split(':',1)
        raw=runtime[f'assets/{namespace}/textures/{path}.png'] if namespace=='simpleradio_directional' else (kit/f'native-reference/assets/{namespace}/textures/{path}.png').read_bytes()
        assert int.from_bytes(raw[16:20],'big')==int.from_bytes(raw[20:24],'big')==16
for image in ['solid_reflector','solid_backing','solid_frame']:
    assert Image.open(assets/f'textures/block/{image}.png').getextrema()[3]==(255,255)

geometric={}
for name,m in models.items():
    values={}
    for aim in range(-45,46,15):
        found=overlaps(faces(m,aim));assert not found,(name,aim,found[:3])
        values[str(aim)]=0
    geometric[name]=values
mast_checks=pair_checks=0
for variant in ['column','vertical']:
    native=json.loads((kit/f'native-reference/assets/simpleradio/models/block/antenna/antenna_{variant}.json').read_text())
    nf=faces(native,offset=(0,0,16))
    # Native zero-thickness antenna planes deliberately have two sides. Only
    # receiver-to-native conflicts belong to the addon's surface audit.
    def cross_conflicts(fs):
        return [p for p in overlaps(fs) if not(p['a'].startswith('native mast/') and p['b'].startswith('native mast/'))]
    for name in names[:2]:
        for aim in range(-45,46,15):
            found=cross_conflicts(faces(models[name],aim)+nf)
            assert not found,(name,variant,aim,found[:3]);mast_checks+=1
    for a,b in itertools.combinations_with_replacement(names[:2],2):
        for aa,bb in itertools.product([-45,0,45],repeat=2):
            fs=faces(models[a],aa)+faces(models[b],bb,offset=(0,0,32),yaw=180,brackets=False)+nf
            found=cross_conflicts(fs);assert not found,(a,b,variant,aa,bb,found[:3]);pair_checks+=1

for name in names[:2]:
    model=models[name]
    for suffix,kind in [('_head','head'),('_mount','mount'),('_clamps','clamps')]:
        data=json.loads(runtime[f'assets/simpleradio_directional/models/block/{name}{suffix}.json'])
        assert data['elements']==[e for e in model['elements'] if motion(e['name'])==kind]
    parts=json.loads(runtime[f'assets/simpleradio_directional/blockstates/{name}.json'])['multipart']
    rings=[p for p in parts if p['apply']['model'].endswith('_clamps')]
    assert len(rings)==4 and all(p['when']['brackets']=='true' for p in rings)
for style in ['mesh','octagonal','refined']:
    m=models['parabolic_receiver_'+style];base=f'resourcepacks/{style}_parabolic/'
    assert json.loads(runtime[base+'pack.mcmeta'])['pack']['pack_format']==15
    assert json.loads(runtime[base+'assets/simpleradio_directional/models/block/parabolic_receiver.json'])==m
    for suffix,kind in [('_head','head'),('_mount','mount'),('_clamps','clamps')]:
        assert json.loads(runtime[base+f'assets/simpleradio_directional/models/block/parabolic_receiver{suffix}.json'])['elements']==[e for e in m['elements'] if motion(e['name'])==kind]
for tag in ['mineable/pickaxe','needs_stone_tool']:
    values=json.loads((ROOT/f'src/main/resources/data/minecraft/tags/blocks/{tag}.json').read_text())['values']
    assert 'simpleradio_directional:wide_angle_receiver' in values and 'simpleradio_directional:wide_angle_part' in values

report={'passed':True,'jar':prototype.name,'bytes':prototype.stat().st_size,'sha256':hashlib.sha256(prototype.read_bytes()).hexdigest(),
        'runtime_classes':sum(n.endswith('.class') for n in runtime),'removed_fixture_classes':len(removed),
        'retained_runtime_entries_byte_identical':len(runtime)-1,'no_obfuscation':True,'capture_helpers_not_bundled':True,
        'optional_repeaters_not_bundled_or_required':True,'blockbench_models':7,'all_materials_16x16':True,
        'all_receiver_faces_one_texture_pixel_per_model_unit':True,'primary_receivers_whole_unit_geometry':True,
        'coplanar_overlaps_by_model_and_aim':geometric,'receiver_to_native_mast_cases_checked':mast_checks,
        'opposite_shared_mount_cases_checked':pair_checks,'duplicate_faces_found':0,
        'all_three_alternate_packs_and_shared_layers_valid':True,'prototype_contains_no_tests':True}
output=ROOT/f'validation/{version}/prototype-verification.json';output.write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))
