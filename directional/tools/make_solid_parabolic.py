"""Opaque stepped dish: original 32-panel shape, connected shell, whole-unit 1:1 UVs."""
import base64
from copy import deepcopy
import json
from pathlib import Path
import uuid
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/simpleradio_directional'
KIT = ROOT / 'art/model-kit'


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n')


def texture(name, pixel):
    image = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            image.putpixel((x, y), (*pixel(x, y), 255))
    image.save(ASSETS / f'textures/block/{name}.png')


def plate(x, y):
    # Four softly varied eight-pixel sheet quadrants; no fine mesh or cutouts.
    base = [(190, 198, 193), (184, 193, 189), (180, 190, 187), (187, 196, 191)][x // 8 + 2 * (y // 8)]
    u, v = x % 8, y % 8
    delta = -19 if u == 7 or v == 7 else 8 if u == 0 or v == 0 else 0
    if (u, v) in {(2, 3), (3, 3), (5, 5)}:
        delta -= 3
    return tuple(c + delta for c in base)


def back(x, y):
    base = (104, 118, 115)
    delta = -11 if x % 8 == 7 or y % 8 == 7 else 8 if x % 8 == 0 or y % 8 == 0 else 0
    if y % 8 in (3, 4) and 1 <= x % 8 <= 5:
        delta += 5
    return tuple(c + delta for c in base)


def frame(x, y):
    base = (117, 132, 131)
    delta = 13 if y == 0 else -10 if y in (3, 7, 11, 15) else 0
    if x in (5, 6) and y in (1, 2):
        delta -= 4
    return tuple(c + delta for c in base)


texture('solid_reflector', plate)
texture('solid_backing', back)
texture('solid_frame', frame)

mesh = json.loads((ASSETS / 'models/block/parabolic_receiver_mesh.json').read_text())
model = deepcopy(mesh)
model['credit'] = 'Simple Radio Directional Antenna / solid original stepped receiver'
model['textures'].pop('mesh')
model['textures'].update(reflector='simpleradio_directional:block/solid_reflector',
                         backing='simpleradio_directional:block/solid_backing',
                         frame='simpleradio_directional:block/solid_frame',
                         particle='simpleradio_directional:block/solid_frame')
for e in model['elements']:
    if e['name'] in {'mount_pivot_lower', 'mount_pivot_upper'}:
        # Mesh holes hid these hinge faces. Keep them behind the opaque shell,
        # rather than coplanar with its central front surface at Z=6.
        e['from'][2] = 10
        e['to'][2] += 4
    if not e['name'].startswith('mesh_panel_'):
        continue
    _, _, row, col = e['name'].split('_')
    e['name'] = f'solid_panel_{row}_{col}'
    # Three-unit shells meet even across the steepest (three-unit) terrace.
    # Front faces and the full outline remain exactly where the mesh faces were.
    e['to'][2] = e['from'][2] + 3
    x0, y0 = int(col) % 2 * 8, (1 - int(row) % 2) * 8
    e['faces']['north'] = {'uv': [x0, y0, x0 + 8, y0 + 8], 'texture': '#reflector'}
    e['faces']['south'] = {'uv': [x0, y0, x0 + 8, y0 + 8], 'texture': '#backing'}
    for side in ['east', 'west']:
        e['faces'][side] = {'uv': [0, 0, 3, 8], 'texture': '#frame'}
    for side in ['up', 'down']:
        e['faces'][side] = {'uv': [0, 0, 8, 3], 'texture': '#frame'}

for e in model['elements']:
    assert all(v == int(v) and -16 <= v <= 32 for v in e['from'] + e['to'])
    assert not e.get('rotation')
    x, y, z = [b - a for a, b in zip(e['from'], e['to'])]
    assert min(x, y, z) >= 1
    sizes = {'north': (x, y), 'south': (x, y), 'east': (z, y), 'west': (z, y), 'up': (x, z), 'down': (x, z)}
    for face, f in e['faces'].items():
        u0, v0, u1, v1 = f['uv']
        assert (abs(u1 - u0), abs(v1 - v0)) == sizes[face]
        assert all(0 <= v <= 16 for v in f['uv'])
write(ASSETS / 'models/block/parabolic_receiver.json', model)


def uid(name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, 'simple-radio-directional/solid-stepped/' + name))


keys = [k for k in model['textures'] if k != 'particle']
cubes = [{'name': e['name'], 'uuid': uid(str(i)), 'type': 'cube', 'from': e['from'], 'to': e['to'],
          'origin': [8, 8, 8], 'rotation': [0, 0, 0], 'rescale': False,
          'faces': {face: dict(f, texture=keys.index(f['texture'][1:])) for face, f in e['faces'].items()},
          'autouv': 0, 'export': True, 'visibility': True} for i, e in enumerate(model['elements'])]
images = []
for i, key in enumerate(keys):
    namespace, path = model['textures'][key].split(':', 1)
    raw = (ASSETS / 'textures' / (path + '.png')).read_bytes()
    images.append({'name': Path(path).name + '.png', 'id': str(i), 'uuid': uid(key), 'width': 16, 'height': 16,
                   'uv_width': 16, 'uv_height': 16, 'folder': 'block', 'namespace': namespace,
                   'source': 'data:image/png;base64,' + base64.b64encode(raw).decode(), 'mode': 'bitmap', 'saved': False})
groups = [{'name': name, 'uuid': uid(name), 'origin': [8, 8, 8], 'export': True,
           'children': [c['uuid'] for c in cubes if c['name'].startswith('mount_') == fixed]}
          for name, fixed in [('Fixed mount', True), ('Solid stepped dish and feed', False)]]
write(KIT / 'Parabolic-Receiver.bbmodel', {'meta': {'format_version': '4.10', 'model_format': 'java_block', 'box_uv': False},
      'name': 'Solid Stepped Parabolic Receiver', 'resolution': {'width': 16, 'height': 16},
      'elements': cubes, 'outliner': groups, 'textures': images, 'display': model['display']})
print(f'Authored {len(cubes)} whole-unit cuboids, with 32 connected opaque reflector panels and 1:1 UVs.')
