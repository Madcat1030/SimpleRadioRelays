"""Author the retained octagonal alternate. Whole units, 0/45-degree angles, 1:1 UVs."""
import base64
import json
from pathlib import Path
import uuid

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/simpleradio_directional'
KIT = ROOT / 'art/model-kit'
elements = []


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n')


def cube(name, start, end, material='iron', rotation=None):
    size = [b - a for a, b in zip(start, end)]
    for axis, length in enumerate(size):
        if length > 16:
            # Tile a long face instead of fitting more than 16 units onto 16 pixels.
            split = start[axis] + 16
            a, b = end.copy(), start.copy()
            a[axis] = b[axis] = split
            cube(name + '_a', start, a, material, rotation)
            cube(name + '_b', b, end, material, rotation)
            return
    x, y, z = size
    uv = {'north': [0, 0, x, y], 'south': [0, 0, x, y],
          'east': [0, 0, z, y], 'west': [0, 0, z, y],
          'up': [0, 0, x, z], 'down': [0, 0, x, z]}
    value = {'name': name, 'from': start, 'to': end,
             'faces': {face: {'uv': coords, 'texture': '#' + material} for face, coords in uv.items()}}
    if rotation:
        value['rotation'] = dict(rotation, origin=rotation['origin'].copy())
    elements.append(value)


# Broad four-pixel cells form just three terraces. The outer silhouette is octagonal.
for x in range(-16, 17, 4):
    for y in range(-16, 17, 4):
        if abs(x) + abs(y) > 20:
            if abs(x) + abs(y) == 24:
                # Whole-pixel corner fillers meet the 45-degree inner bevel.
                # They occupy only the inner quarter of an otherwise omitted cell.
                x0, x1 = (x - 2, x) if x > 0 else (x, x + 2)
                y0, y1 = (y - 2, y) if y > 0 else (y, y + 2)
                depth = 2 if max(abs(x), abs(y)) <= 12 else 0
                cube(f'bowl_corner_{x}_{y}', [8 + x0, 8 + y0, depth],
                     [8 + x1, 8 + y1, depth + 2], 'reflector')
            continue
        depth = 4 if max(abs(x), abs(y)) <= 4 else 2 if max(abs(x), abs(y)) <= 12 else 0
        cube(f'bowl_{x}_{y}', [x + 6, y + 6, depth], [x + 10, y + 10, depth + 2], 'reflector')


def rim(prefix, side, corner, width, z0, z1, material):
    # Four straight rails and four 45-degree corners. Every local extent is integral.
    for suffix, start, end in [
        ('top', [0, 8 + side - width // 2, z0], [16, 8 + side + width // 2, z1]),
        ('bottom', [0, 8 - side - width // 2, z0], [16, 8 - side + width // 2, z1]),
        ('left', [8 - side - width // 2, 0, z0], [8 - side + width // 2, 16, z1]),
        ('right', [8 + side - width // 2, 0, z0], [8 + side + width // 2, 16, z1])]:
        cube(prefix + '_' + suffix, start, end, material)
    for sx in [-1, 1]:
        for sy in [-1, 1]:
            cx, cy = 8 + sx * corner, 8 + sy * corner
            cube(f'{prefix}_corner_{sx}_{sy}', [cx - 7, cy - width // 2, z0],
                 [cx + 7, cy + width // 2, z1], material,
                 {'origin': [cx, cy, z0], 'axis': 'z', 'angle': -sx * sy * 45, 'rescale': False})


rim('copper_rim', 18, 13, 2, -4, 0, 'copper')
rim('inner_bevel', 17, 12, 4, -2, 1, 'reflector')
for x, y in [(0, -10), (16, -10), (26, 0), (26, 16), (16, 26), (0, 26), (-10, 16), (-10, 0)]:
    cube(f'rim_clip_{x}_{y}', [x - 1, y - 1, -5], [x + 1, y + 1, -4])

# A few substantial braces and one feed arm replace fine rods and small fasteners.
cube('rear_saddle', [4, 2, 6], [12, 14, 12])
cube('rear_brace_lower', [-4, 2, 6], [20, 4, 8])
cube('rear_brace_upper', [-4, 12, 6], [20, 14, 8])
cube('feed_foot', [6, -8, -2], [10, -4, 2])
cube('feed_arm', [7, -8, -7], [9, 8, -5], rotation={
    'origin': [8, 0, -6], 'axis': 'x', 'angle': -45, 'rescale': False})
cube('feed_neck', [6, 4, -12], [10, 6, -8])
cube('feed_horn', [6, 6, -12], [10, 10, -8], 'copper')
cube('feed_face', [7, 7, -13], [9, 9, -12], 'cable')
cube('feed_insulator', [6, 6, -8], [10, 10, -6], 'ceramic')

# The same paired wrap-around clamp, simplified onto the whole-pixel grid.
for y in [1, 12]:
    for suffix, start, end in [
        ('front', [2, y, 20], [14, y + 3, 22]),
        ('back', [2, y, 27], [14, y + 3, 29]),
        ('left', [2, y, 22], [4, y + 3, 27]),
        ('right', [12, y, 22], [14, y + 3, 27])]:
        cube(f'mount_clamp_{y}_{suffix}', start, end)
    for x in [2, 12]:
        cube(f'mount_bolt_{x}_{y}', [x, y + 1, 19], [x + 2, y + 3, 20], 'cable')
    cube('mount_arm_' + str(y), [6, y + 1, 10], [10, y + 3, 21])
cube('mount_pivot_lower', [5, 0, 6], [11, 2, 12])
cube('mount_pivot_upper', [5, 15, 6], [11, 17, 12])
cube('mount_pivot_pin', [7, 2, 8], [9, 15, 10])

textures = {k: 'simpleradio_directional:block/array_' + k for k in ['copper', 'iron', 'ceramic', 'cable']}
textures.update(reflector='simpleradio_directional:block/dish_reflector', particle=textures['copper'])
display = json.loads((ASSETS / 'models/block/parabolic_receiver_refined.json').read_text())['display']
model = {'credit': 'Simple Radio Directional Antenna / chunky 45-degree parabolic alternate',
         'parent': 'minecraft:block/block', 'ambientocclusion': False,
         'textures': textures, 'elements': elements, 'display': display}

for e in elements:
    assert all(v == int(v) and -16 <= v <= 32 for v in e['from'] + e['to']), e['name']
    r = e.get('rotation', {})
    assert r.get('angle', 0) in [-45, 0, 45] and not r.get('rescale', False)
    assert all(v == int(v) for v in r.get('origin', [8, 8, 8]))
    x, y, z = [b - a for a, b in zip(e['from'], e['to'])]
    sizes = {'north': (x, y), 'south': (x, y), 'east': (z, y), 'west': (z, y), 'up': (x, z), 'down': (x, z)}
    for face, f in e['faces'].items():
        u0, v0, u1, v1 = f['uv']
        assert (u1 - u0, v1 - v0) == sizes[face] and all(0 <= v <= 16 for v in f['uv'])
write(ASSETS / 'models/block/parabolic_receiver_octagonal.json', model)
pack = ROOT / 'src/main/resources/resourcepacks/octagonal_parabolic'
write(pack / 'pack.mcmeta', {'pack': {'pack_format': 15, 'description': 'The solid octagonal parabolic receiver, retained as a visual alternate.'}})
pack_models = pack / 'assets/simpleradio_directional/models/block'
write(pack_models / 'parabolic_receiver.json', model)
for suffix, fixed in [('_mount', True), ('_head', False)]:
    write(pack_models / ('parabolic_receiver' + suffix + '.json'), dict(model,
          elements=[e for e in elements if e['name'].startswith('mount_') == fixed]))

def uid(name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, 'simple-radio-directional/chunky-parabolic/' + name))

keys = [k for k in textures if k != 'particle']
cubes = []
for i, e in enumerate(elements):
    r = e.get('rotation', {})
    rot = [0, 0, 0]
    if r: rot['xyz'.index(r['axis'])] = r['angle']
    cubes.append({'name': e['name'], 'uuid': uid(str(i)), 'type': 'cube', 'from': e['from'], 'to': e['to'],
                  'origin': r.get('origin', [8, 8, 8]), 'rotation': rot, 'rescale': False,
                  'faces': {face: dict(f, texture=keys.index(f['texture'][1:])) for face, f in e['faces'].items()},
                  'autouv': 0, 'export': True, 'visibility': True})
images = []
for i, key in enumerate(keys):
    path = textures[key].split(':', 1)[1]
    raw = (ASSETS / 'textures' / (path + '.png')).read_bytes()
    images.append({'name': Path(path).name + '.png', 'id': str(i), 'uuid': uid(key), 'width': 16, 'height': 16,
                   'uv_width': 16, 'uv_height': 16, 'folder': 'block', 'namespace': 'simpleradio_directional',
                   'source': 'data:image/png;base64,' + base64.b64encode(raw).decode(), 'mode': 'bitmap', 'saved': False})
groups = [{'name': name, 'uuid': uid(name), 'origin': [8, 8, 8], 'export': True,
           'children': [c['uuid'] for c in cubes if c['name'].startswith('mount_') == fixed]}
          for name, fixed in [('Fixed mount', True), ('Swivelling dish and feed', False)]]
write(KIT / 'Parabolic-Receiver-Octagonal.bbmodel', {'meta': {'format_version': '4.10', 'model_format': 'java_block', 'box_uv': False},
      'name': 'Chunky Parabolic Receiver', 'resolution': {'width': 16, 'height': 16}, 'elements': cubes,
      'outliner': groups, 'textures': images, 'display': display})
print(f'Preserved the {len(elements)}-cuboid octagonal alternate and optional resource pack.')
