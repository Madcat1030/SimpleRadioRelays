"""Finish the original 32-panel stepped mesh receiver; preserve native texture density."""
import base64
import json
import math
from pathlib import Path
import uuid
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/simpleradio_directional'
KIT = ROOT / 'art/model-kit'
elements = []


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n')


def cube(name, start, end, material='frame'):
    size = [b - a for a, b in zip(start, end)]
    for axis, length in enumerate(size):
        if length > 16:
            split = start[axis] + 16
            a, b = end.copy(), start.copy()
            a[axis] = b[axis] = split
            cube(name + '_a', start, a, material)
            cube(name + '_b', b, end, material)
            return
    x, y, z = size
    uv = {'north': [0, 0, x, y], 'south': [0, 0, x, y],
          'east': [0, 0, z, y], 'west': [0, 0, z, y],
          'up': [0, 0, x, z], 'down': [0, 0, x, z]}
    result = {'name': name, 'from': start, 'to': end,
              'faces': {face: {'uv': coords, 'texture': '#' + material} for face, coords in uv.items()}}
    elements.append(result)
    return result


# The base mod's actual mesh remains the visible receiving surface. Only the frame
# gets a new restrained 16x16 galvanized material; all parts stay on the whole-unit grid.
palette = ['#929a9d', '#a7afb1', '#848e92', '#b9bfc0', '#9ca5a8']
image = Image.new('RGBA', (16, 16))
for y in range(16):
    for x in range(16):
        color = palette[(x // 4 + y // 4 * 2) % len(palette)]
        image.putpixel((x, y), tuple(bytes.fromhex(color[1:])) + (255,))
image.save(ASSETS / 'textures/block/mesh_frame.png')

panels = {}
for row in range(6):
    for col in range(6):
        x, y = -16 + col * 8, -16 + row * 8
        radius = math.hypot(x + 4 - 8, y + 4 - 8)
        if radius > 25:
            continue
        depth = 6 - round(7 * (radius / 25) ** 2)
        panels[col, row] = (x, y, depth)
        e = cube(f'mesh_panel_{row}_{col}', [x, y, depth], [x + 8, y + 8, depth + 1], 'mesh')
        # Keep the original eight-pixel mesh patch on each eight-unit panel.
        e['faces']['north']['uv'] = [0, 0, 8, 8]
        # Match the physical holes through the front and back faces of each panel.
        e['faces']['south']['uv'] = [8, 0, 0, 8]
        for face in ['up', 'down', 'east', 'west']:
            e['faces'][face]['texture'] = '#frame'
assert len(panels) == 32

# A two-unit perimeter follows the original square-stepped outline and its bowl
# depths. Merge border cells without overlapping coplanar corners.
for (col, row), (x, y, depth) in panels.items():
    mask = {(a, b) for a in range(4) for b in range(4)
            if (a == 0 and (col - 1, row) not in panels)
            or (a == 3 and (col + 1, row) not in panels)
            or (b == 0 and (col, row - 1) not in panels)
            or (b == 3 and (col, row + 1) not in panels)}
    segment = 0
    while mask:
        a, b = min(mask, key=lambda p: (p[1], p[0]))
        w = 1
        while (a + w, b) in mask: w += 1
        h = 1
        while all((a + dx, b + h) in mask for dx in range(w)): h += 1
        mask.difference_update((a + dx, b + dy) for dx in range(w) for dy in range(h))
        cube(f'rim_{row}_{col}_{segment}', [x + 2 * a, y + 2 * b, depth - 1],
             [x + 2 * (a + w), y + 2 * (b + h), depth + 2])
        segment += 1

# Four broad copper binding straps, set in front of the metal perimeter.
for suffix, start, end in [
    ('top', [6, 30, -1], [10, 32, 0]),
    ('bottom', [6, -16, -1], [10, -14, 0]),
    ('left', [-16, 6, -1], [-14, 10, 0]),
    ('right', [30, 6, -1], [32, 10, 0])]:
    cube('copper_binding_' + suffix, start, end, 'copper')

# Original cross-arm language, finished with a copper feed and ceramic insulator.
cube('feed_left', [-8, 7, -4], [6, 9, -2])
cube('feed_right', [10, 7, -4], [24, 9, -2])
cube('feed_lower', [7, -8, -4], [9, 6, -2])
for x, y in [(-8, 8), (24, 8), (8, -8)]:
    cube(f'feed_standoff_{x}_{y}', [x - 1, y - 1, -2], [x + 1, y + 1, 8])
cube('feed_horn', [6, 6, -6], [10, 10, -2], 'copper')
cube('feed_face', [7, 7, -7], [9, 9, -6], 'cable')
cube('feed_insulator', [6, 6, -2], [10, 10, 0], 'ceramic')

# Backing carrier and stand-offs connect the shallow bowl to the fixed mast clamp.
cube('rear_horizontal', [-8, 7, 10], [24, 9, 14])
cube('rear_vertical', [7, -8, 10], [9, 24, 14])
for x, y in [(-4, 8), (20, 8), (8, -4), (8, 20)]:
    cube(f'rear_standoff_{x}_{y}', [x - 1, y - 1, 6], [x + 1, y + 1, 13])
cube('rear_saddle', [6, 4, 8], [10, 12, 15], 'iron')
octagonal = json.loads((ASSETS / 'models/block/parabolic_receiver_octagonal.json').read_text())
elements.extend(e for e in octagonal['elements'] if e['name'].startswith('mount_'))

textures = {k: 'simpleradio_directional:block/array_' + k for k in ['copper', 'iron', 'ceramic', 'cable']}
textures.update(mesh='simpleradio:block/antenna_column', frame='simpleradio_directional:block/mesh_frame',
                particle='simpleradio_directional:block/mesh_frame')
model = {'credit': 'Simple Radio Directional Antenna / finished original stepped mesh receiver',
         'parent': 'minecraft:block/block', 'ambientocclusion': False,
         'textures': textures, 'elements': elements, 'display': octagonal['display']}
for e in elements:
    assert all(v == int(v) and -16 <= v <= 32 for v in e['from'] + e['to']), e['name']
    assert not e.get('rotation')
    x, y, z = [b - a for a, b in zip(e['from'], e['to'])]
    assert min(x, y, z) >= 1
    sizes = {'north': (x, y), 'south': (x, y), 'east': (z, y), 'west': (z, y), 'up': (x, z), 'down': (x, z)}
    for face, f in e['faces'].items():
        u0, v0, u1, v1 = f['uv']
        assert (abs(u1 - u0), abs(v1 - v0)) == sizes[face]
        assert all(0 <= v <= 16 for v in f['uv'])
write(ASSETS / 'models/block/parabolic_receiver_mesh.json', model)
pack = ROOT / 'src/main/resources/resourcepacks/mesh_parabolic'
write(pack / 'pack.mcmeta', {'pack': {'pack_format': 15, 'description': 'The 0.2.3 stepped mesh receiver, retained as a visual alternate.'}})
pack_models = pack / 'assets/simpleradio_directional/models/block'
write(pack_models / 'parabolic_receiver.json', model)
for suffix, fixed in [('_mount', True), ('_head', False)]:
    write(pack_models / ('parabolic_receiver' + suffix + '.json'), dict(model,
          elements=[e for e in elements if e['name'].startswith('mount_') == fixed]))

def uid(name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, 'simple-radio-directional/finished-mesh/' + name))

keys = [k for k in textures if k != 'particle']
cubes = [{'name': e['name'], 'uuid': uid(str(i)), 'type': 'cube', 'from': e['from'], 'to': e['to'],
          'origin': [8, 8, 8], 'rotation': [0, 0, 0], 'rescale': False,
          'faces': {face: dict(f, texture=keys.index(f['texture'][1:])) for face, f in e['faces'].items()},
          'autouv': 0, 'export': True, 'visibility': True} for i, e in enumerate(elements)]
images = []
for i, key in enumerate(keys):
    namespace, path = textures[key].split(':', 1)
    base = ASSETS if namespace == 'simpleradio_directional' else KIT / 'native-reference/assets' / namespace
    raw = (base / 'textures' / (path + '.png')).read_bytes()
    images.append({'name': Path(path).name + '.png', 'id': str(i), 'uuid': uid(key), 'width': 16, 'height': 16,
                   'uv_width': 16, 'uv_height': 16, 'folder': 'block', 'namespace': namespace,
                   'source': 'data:image/png;base64,' + base64.b64encode(raw).decode(), 'mode': 'bitmap', 'saved': False})
groups = [{'name': name, 'uuid': uid(name), 'origin': [8, 8, 8], 'export': True,
           'children': [c['uuid'] for c in cubes if c['name'].startswith('mount_') == fixed]}
          for name, fixed in [('Fixed mount', True), ('Stepped mesh and feed', False)]]
write(KIT / 'Parabolic-Receiver-Mesh.bbmodel', {'meta': {'format_version': '4.10', 'model_format': 'java_block', 'box_uv': False},
      'name': 'Finished Stepped Mesh Receiver', 'resolution': {'width': 16, 'height': 16},
      'elements': cubes, 'outliner': groups, 'textures': images, 'display': model['display']})
print(f'Authored {len(elements)} whole-unit cuboids, including 32 original-shaped mesh panels and a continuous rim.')
