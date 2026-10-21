"""Author the optional refined dish, built-in resource pack and Blockbench project.

Does not change recipes, reception, aiming, or the wide-angle array. No rendered images.
"""
import base64
import json
import math
from pathlib import Path
import uuid
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/simpleradio_directional'
KIT = ROOT / 'art/model-kit'


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n')


def uid(name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, 'simple-radio-directional/refined-parabolic/' + name))


# A restrained, cool galvanized surface: large pixel patches and sparse one-pixel marks.
palette = ['#b2bec1', '#bcc7c9', '#aab8bc', '#c6ced0', '#a2b1b6']
image = Image.new('RGBA', (16, 16))
for y in range(16):
    for x in range(16):
        color = palette[(x // 4 + y // 4 * 2) % len(palette)]
        image.putpixel((x, y), tuple(bytes.fromhex(color[1:])) + (255,))
for x, y in [(2, 5), (11, 2), (7, 12)]:
    image.putpixel((x, y), (151, 168, 175, 255))
image.save(ASSETS / 'textures/block/dish_reflector.png')

elements = []


def cube(name, start, end, material='iron', rotation=None, front_uv=None):
    dimensions = [abs(b - a) for a, b in zip(start, end)]
    for axis, size in enumerate(dimensions):
        if size > 16:
            # A 16x16 material must not be scaled over a longer face. Subdivide
            # the native cuboid, retaining the same rotation origin for both halves.
            middle = (start[axis] + end[axis]) / 2
            left_end, right_start = end.copy(), start.copy()
            left_end[axis] = right_start[axis] = middle
            cube(name + '_a', start, left_end, material, rotation)
            cube(name + '_b', right_start, end, material, rotation)
            return
    x, y, z = dimensions
    uv = {'north': [0, 0, x, y], 'south': [0, 0, x, y],
          'west': [0, 0, z, y], 'east': [0, 0, z, y],
          'up': [0, 0, x, z], 'down': [0, 0, x, z]}
    value = {'name': name, 'from': start, 'to': end,
             'faces': {face: {'texture': '#' + material, 'uv': coords} for face, coords in uv.items()}}
    if front_uv is not None:
        value['faces']['north']['uv'] = front_uv
    if rotation is not None:
        value['rotation'] = dict(rotation, origin=rotation['origin'].copy())
    elements.append(value)
    return value


def surface_depth(x, y):
    return round((6.5 - 7 * (x * x + y * y) / (18.5 * 18.5)) * 4) / 4


# Continuous, stepped concavity. Two-pixel rows keep the silhouette rounded;
# contiguous facets meet without coplanar overlaps or floating individual tiles.
for row, y0 in enumerate(range(-18, 18, 2)):
    y1 = y0 + 2
    outer_y = max(abs(y0), abs(y1))
    half_width = math.floor(math.sqrt(max(0, 18.2 ** 2 - outer_y ** 2)) * 2) / 2
    if half_width < .5:
        continue
    cuts = sorted(set([-half_width, half_width] + [x for x in range(-16, 17, 4) if -half_width < x < half_width]))
    for col, (x0, x1) in enumerate(zip(cuts, cuts[1:])):
        front = surface_depth((x0 + x1) / 2, (y0 + y1) / 2)
        panel = cube(f'reflector_facet_{row}_{col}', [8 + x0, 8 + y0, front],
                     [8 + x1, 8 + y1, front + 3], 'reflector',
                     front_uv=[(x0 + 16) % 12, (y0 + 18) % 12,
                               (x0 + 16) % 12 + x1 - x0, (y0 + 18) % 12 + y1 - y0])


def ring(name, radius, width, z0, z1, material):
    length = 2 * radius * math.tan(math.pi / 16) - .08
    for i in range(16):
        normal = i * 22.5
        rad = math.radians(normal)
        cx, cy = 8 + radius * math.cos(rad), 8 + radius * math.sin(rad)
        angle = (normal + 90 + 90) % 180 - 90
        # Native Java models permit only 0, ±22.5 and ±45-degree element rotations.
        horizontal = abs(angle) <= 45
        turn = angle if horizontal else angle - math.copysign(90, angle)
        dx, dy = (length / 2, width / 2) if horizontal else (width / 2, length / 2)
        rotation = {'origin': [cx, cy, (z0 + z1) / 2], 'axis': 'z', 'angle': turn, 'rescale': False}
        cube(f'{name}_{i:02}', [cx - dx, cy - dy, z0], [cx + dx, cy + dy, z1], material, rotation)


ring('copper_rolled_rim', 18.5, 1.4, -1.5, 1.5, 'copper')
ring('inner_bevel', 17.4, 2.6, -.75, 1.5, 'reflector')
ring('rim_rear_lip', 18.5, 1.1, 1.5, 2.25, 'iron')
for i in range(16):
    a = math.radians(i * 22.5 + 11.25)
    r = 18.5 / math.cos(math.pi / 16)
    x, y = 8 + r * math.cos(a), 8 + r * math.sin(a)
    cube(f'rim_fastener_{i:02}', [x - .45, y - .45, -1.8], [x + .45, y + .45, -1.5])

# Ribs follow the back of the bowl. The broad central saddle meets the native swivel.
for offset in range(-16, 17, 4):
    depth = surface_depth(offset, 0) + 3
    cube(f'back_rib_horizontal_{offset}', [8 + offset - 2, 7, depth], [8 + offset + 2, 9, depth + .9])
    depth = surface_depth(0, offset) + 3
    cube(f'back_rib_vertical_{offset}', [7, 8 + offset - 2, depth], [9, 8 + offset + 2, depth + .9])
cube('rear_saddle', [5.5, 3, 9.25], [10.5, 13, 13.25])
for y in [3.5, 11.5]:
    cube('rear_saddle_bolt_' + str(y), [7, y, 13.25], [9, y + 1, 13.75], 'cable')

# A compact feed horn on twin 45-degree rails, clear of most of the reflector face.
for x in [5.5, 10.5]:
    midpoint = [x, -1.5, -5.5]
    length = 13 * math.sqrt(2)
    cube('feed_rail_' + str(x), [x - .5, midpoint[1] - length / 2, -6],
         [x + .5, midpoint[1] + length / 2, -5], 'iron',
         {'origin': midpoint, 'axis': 'x', 'angle': -45, 'rescale': False})
cube('feed_lower_crossmember', [5, -8.75, .25], [11, -7.5, 1.75])
cube('feed_upper_crossmember', [5, 4.5, -12.5], [11, 5.5, -11.5])
cube('feed_neck', [7.25, 5.5, -12.5], [8.75, 7, -11.5])
cube('feed_ceramic', [6.5, 7, -12.75], [9.5, 10, -10.75], 'ceramic')
cube('feed_horn_body', [6.75, 7.25, -13.25], [9.25, 9.75, -12.75], 'copper')
cube('feed_horn_lip', [6.25, 6.75, -13.75], [9.75, 10.25, -13.25], 'copper')
cube('feed_horn_face', [6.75, 7.25, -13.8], [9.25, 9.75, -13.75], 'cable')
cube('feed_connector', [7.25, 8, -10.75], [8.75, 9, -9.75], 'cable')

# Reuse the wide array's exact fixed two-point mast clamp and swivel hardware.
# Put the bowl in front of that hardware; the pivot must not cross its visible face.
for element in elements:
    element['from'][2] -= 2
    element['to'][2] -= 2
    if 'rotation' in element:
        element['rotation']['origin'][2] -= 2
array = json.loads((ASSETS / 'models/block/wide_angle_receiver.json').read_text())
elements.extend(e for e in array['elements'] if e['name'].startswith('mount_'))
textures = {'copper': 'simpleradio_directional:block/array_copper',
            'iron': 'simpleradio_directional:block/array_iron',
            'ceramic': 'simpleradio_directional:block/array_ceramic',
            'cable': 'simpleradio_directional:block/array_cable',
            'reflector': 'simpleradio_directional:block/dish_reflector'}
textures['particle'] = textures['copper']
display = {'gui': {'rotation': [25, 225, 0], 'scale': [.25, .25, .25]},
           'ground': {'translation': [0, 3, 0], 'scale': [.125, .125, .125]},
           'fixed': {'scale': [.28, .28, .28]},
           'firstperson_righthand': {'rotation': [0, 45, 0], 'scale': [.23, .23, .23]},
           'thirdperson_righthand': {'rotation': [75, 45, 0], 'scale': [.17, .17, .17]}}
assert all(-16 <= v <= 32 for e in elements for v in e['from'] + e['to'])
assert all(e.get('rotation', {}).get('angle', 0) in [-45, -22.5, 0, 22.5, 45] for e in elements)
for element in elements:
    x, y, z = [b - a for a, b in zip(element['from'], element['to'])]
    sizes = {'north': (x, y), 'south': (x, y), 'east': (z, y), 'west': (z, y), 'up': (x, z), 'down': (x, z)}
    for face, value in element['faces'].items():
        u0, v0, u1, v1 = value['uv']
        assert math.isclose(abs(u1 - u0), sizes[face][0], abs_tol=1e-9), (element['name'], face)
        assert math.isclose(abs(v1 - v0), sizes[face][1], abs_tol=1e-9), (element['name'], face)
        assert all(0 <= v <= 16 for v in value['uv']), (element['name'], face, value['uv'])
model = {'credit': 'Simple Radio Directional Antenna / refined hand-authored parabolic receiver',
         'parent': 'minecraft:block/block', 'ambientocclusion': False,
         'textures': textures, 'elements': elements, 'display': display}
write(ASSETS / 'models/block/parabolic_receiver_refined.json', model)
pack = ROOT / 'src/main/resources/resourcepacks/refined_parabolic'
write(pack / 'pack.mcmeta', {'pack': {'pack_format': 15, 'description': 'The finer round parabolic receiver, retained as a visual alternate.'}})
pack_models = pack / 'assets/simpleradio_directional/models/block'
write(pack_models / 'parabolic_receiver.json', model)
for suffix, fixed in [('_mount', True), ('_head', False)]:
    write(pack_models / ('parabolic_receiver' + suffix + '.json'), dict(model,
          elements=[e for e in elements if e['name'].startswith('mount_') == fixed]))

keys = [k for k in textures if k != 'particle']
cubes = []
for index, element in enumerate(elements):
    r = element.get('rotation', {})
    rotation = [0, 0, 0]
    if r:
        rotation['xyz'.index(r['axis'])] = r['angle']
    cubes.append({'name': element['name'], 'uuid': uid(str(index)), 'type': 'cube',
                  'from': element['from'], 'to': element['to'], 'origin': r.get('origin', [8, 8, 8]),
                  'rotation': rotation, 'rescale': False,
                  'faces': {face: dict(value, texture=keys.index(value['texture'][1:])) for face, value in element['faces'].items()},
                  'autouv': 0, 'export': True, 'visibility': True})
images = []
for index, key in enumerate(keys):
    path = textures[key].split(':', 1)[1]
    raw = (ASSETS / 'textures' / (path + '.png')).read_bytes()
    images.append({'name': Path(path).name + '.png', 'id': str(index), 'uuid': uid(key),
                   'width': 16, 'height': 16, 'uv_width': 16, 'uv_height': 16,
                   'folder': 'block', 'namespace': 'simpleradio_directional',
                   'source': 'data:image/png;base64,' + base64.b64encode(raw).decode(),
                   'mode': 'bitmap', 'saved': False})
groups = [{'name': name, 'uuid': uid(name), 'origin': [8, 8, 8], 'export': True,
           'children': [c['uuid'] for c in cubes if c['name'].startswith('mount_') == fixed]}
          for name, fixed in [('Fixed mount', True), ('Swivelling dish and feed', False)]]
write(KIT / 'Parabolic-Receiver-Refined.bbmodel', {'meta': {'format_version': '4.10', 'model_format': 'java_block', 'box_uv': False},
      'name': 'Parabolic Receiver', 'resolution': {'width': 16, 'height': 16}, 'elements': cubes,
      'outliner': groups, 'textures': images, 'display': display})
(KIT / 'Parabolic-Receiver-Placeholder.bbmodel').unlink(missing_ok=True)
print(f'Preserved the {len(elements)}-cuboid refined alternate and its optional resource pack.')
