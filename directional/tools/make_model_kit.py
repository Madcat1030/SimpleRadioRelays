"""Extract self-contained native Blockbench references from the pinned base-mod archive.

Usage: python3 directional/tools/make_model_kit.py /path/to/simple-radio-sources.jar
No images are generated or altered. Native textures are copied and embedded losslessly.
"""
import base64
import json
import math
from pathlib import Path
import sys
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/simpleradio_directional'
DATA = ROOT / 'src/main/resources/data'
KIT = ROOT / 'art/model-kit'
NATIVE = KIT / 'native-reference'

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n')

def uid(name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, 'simple-radio-directional/' + name))

native_files = [
    'assets/simpleradio/models/block/antenna/antenna_column.json',
    'assets/simpleradio/models/block/antenna/antenna_vertical.json',
    'assets/simpleradio/textures/block/antenna.png',
    'assets/simpleradio/textures/block/antenna_column.png',
    'LICENSE_Simple Radio'
]
with zipfile.ZipFile(sys.argv[1]) as source:
    for name in native_files:
        target = NATIVE / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(source.read(name))

def blockbench(name, source):
    keys=[]
    for value in source['textures'].values():
        if not value.startswith('#') and value not in keys: keys.append(value)
    def resolve(key):
        while key.startswith('#'): key=source['textures'][key[1:]]
        return keys.index(key)
    cubes=[]
    for i,element in enumerate(source['elements']):
        rotation=[0,0,0]
        if 'rotation' in element:
            r=element['rotation']; rotation['xyz'.index(r['axis'])]=r['angle']
        faces={face:{'uv':value.get('uv',[0,0,16,16]),'texture':resolve(value['texture']),
                     'rotation':value.get('rotation',0)} for face,value in element['faces'].items()}
        for face in ['north','south','east','west','up','down']:
            faces.setdefault(face,{'uv':[0,0,0,0],'texture':None})
        cubes.append({'name':element.get('name',f'native_{i}'),'uuid':uid(name+f'/cube/{i}'),'type':'cube',
                      'from':element['from'],'to':element['to'],'origin':element.get('rotation',{}).get('origin',[8,8,8]),
                      'rotation':rotation,'rescale':element.get('rotation',{}).get('rescale',False),
                      'faces':faces,'autouv':0,'shade':element.get('shade',True),'export':True,'visibility':True})
    images=[]
    for i,key in enumerate(keys):
        namespace,path=key.split(':',1)
        raw=(NATIVE/f'assets/{namespace}/textures/{path}.png').read_bytes()
        images.append({'name':Path(path).name+'.png','id':str(i),'uuid':uid(name+f'/texture/{i}'),
                       'folder':str(Path(path).parent),'namespace':namespace,'width':16,'height':16,'uv_width':16,'uv_height':16,
                       'source':'data:image/png;base64,'+base64.b64encode(raw).decode(),'mode':'bitmap','saved':False,
                       'render_mode':'default','visible':True})
    children=[c['uuid'] for c in cubes]
    if name == 'Parabolic-Receiver-Placeholder':
        fixed=lambda c: c['name'] in ('mast_clamp','mounting_arm') or c['name'].startswith('mount_')
        children=[{'name':group,'uuid':uid(name+'/'+group),'origin':[8,8,8],'export':True,
                   'children':[c['uuid'] for c in cubes if fixed(c)==stationary]}
                  for group,stationary in [('Mount',True),('Reflector',False)]]
    result={'meta':{'format_version':'4.10','model_format':'java_block','box_uv':False},'name':name,'model_identifier':name,
            'visible_box':[4,4,1],'resolution':{'width':16,'height':16},'elements':cubes,
            'outliner':[{'name':name,'uuid':uid(name+'/group'),'origin':[8,8,8],'export':True,'children':children}],
            'textures':images,'display':source.get('display',{})}
    write(KIT/(name+'.bbmodel'),result)

for variant in ['column','vertical']:
    source=json.loads((NATIVE/f'assets/simpleradio/models/block/antenna/antenna_{variant}.json').read_text())
    blockbench('Native-Antenna-'+variant.title(),source)
print('Extracted two exact native reference projects. Use make_parabolic_receiver.py or make_wide_array.py for receiver resources.')
