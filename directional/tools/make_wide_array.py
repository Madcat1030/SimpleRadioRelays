"""Author the Java block model, pixel textures and editable Blockbench project. No rendered imagery."""
import base64
import json
from pathlib import Path
import uuid
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/simpleradio_directional'
DATA = ROOT / 'src/main/resources/data'
KIT = ROOT / 'art/model-kit'

def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + '\n')

palettes = {
    'array_copper': ['#b96739', '#c77b49', '#a95430', '#da9259', '#8f4b2b'],
    'array_iron': ['#656d71', '#879194', '#515b60', '#a2aaab', '#727c80'],
    'array_ceramic': ['#d5d0b5', '#e8e2ca', '#b9b297', '#f0ead5', '#cbc3a7'],
    'array_cable': ['#252b2c', '#303738', '#1b2021', '#3b4343', '#242a2a'],
}
for name, palette in palettes.items():
    image = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            # Large restrained pixel patches, rather than noisy high-resolution material.
            image.putpixel((x, y), tuple(bytes.fromhex(palette[(x // 3 + y // 4 * 3 + (x * y) % 3) % len(palette)][1:])) + (255,))
    if name == 'array_copper':
        for point in [(1, 2), (2, 2), (12, 13), (13, 13)]: image.putpixel(point, (73, 103, 86, 255))
    output = ASSETS / 'textures/block' / (name + '.png')
    output.parent.mkdir(parents=True, exist_ok=True)
    image.save(output)

elements = []
def cube(name, start, end, material, angle=None):
    # Long members tile at sixteen units. One texture pixel always covers one model unit.
    for axis, length in enumerate(b-a for a, b in zip(start, end)):
        if length > 16:
            a, b = end.copy(), start.copy()
            a[axis] = b[axis] = start[axis]+16
            cube(name+'_a', start, a, material, angle)
            cube(name+'_b', b, end, material, angle)
            return
    x, y, z = [b-a for a, b in zip(start, end)]
    assert all(v == int(v) for v in start+end) and min(x, y, z) >= 1
    uv = {'north': [0, 0, x, y], 'south': [0, 0, x, y],
          'west': [0, 0, z, y], 'east': [0, 0, z, y],
          'up': [0, 0, x, z], 'down': [0, 0, x, z]}
    element = {'name': name, 'from': start, 'to': end,
               'faces': {face: {'texture': '#' + material, 'uv': coords} for face, coords in uv.items()}}
    if angle is not None: element['rotation'] = {'axis': 'y', 'angle': angle, 'origin': [8, 8, 8], 'rescale': False}
    elements.append(element)

# Two 17-pixel wings rotate ±22.5 degrees: a broad, genuinely open V, not a textured solid panel.
for side, left, right, angle in [('left', -9, 7, -22.5), ('right', 9, 25, 22.5)]:
    for suffix, start, end in [
        ('bottom', [left, -8, 7], [right, -6, 9]),
        ('top', [left, 22, 7], [right, 24, 9]),
        ('outer', [left if side == 'left' else right - 2, -6, 7],
                  [left + 2 if side == 'left' else right, 22, 9]),
    ]: cube(side + '_copper_' + suffix, start, end, 'copper', angle)
    for index, x in enumerate([left + 4, left + 8, left + 12]):
        cube(f'{side}_grille_vertical_{index}', [x, -6, 7], [x + 1, 22, 8], 'iron', angle)
    for index, y in enumerate([2, 14]):
        cube(f'{side}_grille_horizontal_{index}', [left + 2 if side == 'left' else left, y, 7],
             [right if side == 'left' else right - 2, y + 1, 8], 'iron', angle)
    for x in [left, right - 1]:
        for y in [-8, 23]:
            cube(f'{side}_corner_bolt_{x}_{y}', [x, y, 6], [x + 1, y + 1, 7], 'iron', angle)
cube('reflector_spine', [7, -7, 13], [9, 23, 15], 'copper')
for y in [5, 10]: cube('reflector_bridge_'+str(y), [6, y, 7], [10, y+1, 14], 'iron')
cube('dipole_lower', [7, -4, -1], [9, 6, 0], 'copper')
cube('dipole_upper', [7, 10, -1], [9, 20, 0], 'copper')
cube('ceramic_insulator', [6, 6, -2], [10, 10, 1], 'ceramic')
for y in [5, 10]: cube('dipole_band_' + str(y), [6, y, -2], [10, y + 1, 1], 'iron')
for y in [4, 12]:
    cube('dipole_standoff_' + str(y), [7, y, 0], [9, y + 1, 9], 'iron')
cube('feed_connector', [7, 2, -2], [9, 4, 0], 'cable')
cube('feed_cable_forward', [7, 3, 0], [9, 4, 6], 'cable')
cube('feed_cable_up', [7, 4, 6], [9, 7, 7], 'cable')

# Everything prefixed mount_ stays fixed while the entire reflector and receiving element swivel.
for y in [2, 11]:
    cube('mount_arm_' + str(y), [6, y, 10], [10, y + 2, 22], 'iron')
for y in [2, 11]:
    for suffix, start, end in [
        ('front', [3, y, 19], [13, y + 3, 21]),
        ('back', [3, y, 27], [13, y + 3, 29]),
        ('left', [3, y, 21], [5, y + 3, 27]),
        ('right', [11, y, 21], [13, y + 3, 27]),
    ]: cube(f'mount_clamp_{y}_{suffix}', start, end, 'iron')
    for x in [3, 11]: cube(f'mount_bolt_{x}_{y}', [x, y + 1, 18], [x + 2, y + 2, 19], 'cable')
cube('mount_pivot_lower', [5, -2, 10], [11, 0, 16], 'iron')
cube('mount_pivot_upper', [5, 16, 10], [11, 18, 16], 'iron')
cube('mount_pivot_pin', [7, 0, 8], [9, 16, 10], 'iron')

textures = {key: 'simpleradio_directional:block/array_' + key for key in ['copper', 'iron', 'ceramic', 'cable']}
textures['particle'] = textures['copper']
display = {'gui': {'rotation': [25, 225, 0], 'translation': [0, 0, 0], 'scale': [.32, .32, .32]},
           'ground': {'translation': [0, 3, 0], 'scale': [.16, .16, .16]},
           'fixed': {'scale': [.35, .35, .35]},
           'firstperson_righthand': {'rotation': [0, 45, 0], 'scale': [.28, .28, .28]},
           'thirdperson_righthand': {'rotation': [75, 45, 0], 'scale': [.20, .20, .20]}}
model = {'credit': 'Simple Radio Directional Antenna / hand-authored Java block geometry',
         'parent': 'minecraft:block/block', 'ambientocclusion': False, 'textures': textures,
         'elements': elements, 'display': display}
assert all(-16 <= v <= 32 for e in elements for v in e['from'] + e['to'])
write(ASSETS / 'models/block/wide_angle_receiver.json', model)
write(ASSETS / 'models/item/wide_angle_receiver.json', {'parent': 'simpleradio_directional:block/wide_angle_receiver'})
write(ASSETS / 'models/block/wide_angle_part.json', {'parent': 'minecraft:block/block', 'elements': []})
write(ASSETS / 'blockstates/wide_angle_part.json', {'variants': {'': {'model': 'simpleradio_directional:block/wide_angle_part'}}})
for block in ['wide_angle_receiver', 'wide_angle_part']:
    write(DATA / f'simpleradio_directional/loot_tables/blocks/{block}.json', {
        'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'simpleradio_directional:wide_angle_receiver'}],
                                         'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
for tag in ['mineable/pickaxe', 'needs_stone_tool']:
    path = DATA / f'minecraft/tags/blocks/{tag}.json'
    data = json.loads(path.read_text())
    for block in ['wide_angle_receiver', 'wide_angle_part']:
        key = 'simpleradio_directional:' + block
        if key not in data['values']: data['values'].append(key)
    write(path, data)
write(DATA / 'simpleradio_directional/recipes/wide_angle_receiver.json', {
    'type': 'minecraft:crafting_shaped', 'pattern': ['CBC', 'B B', ' W '],
    'key': {'C': {'item': 'minecraft:copper_ingot'}, 'B': {'item': 'minecraft:iron_bars'}, 'W': {'item': 'simpleradio:copper_wire'}},
    'result': {'item': 'simpleradio_directional:wide_angle_receiver', 'count': 1}})
# Names, tooltips and handbook text are maintained in the language resources.
# Regenerating geometry must not overwrite player-facing descriptions.

def uid(name): return str(uuid.uuid5(uuid.NAMESPACE_URL, 'simple-radio-directional/wide-array/' + name))
keys = ['copper', 'iron', 'ceramic', 'cable']
cubes = []
for index, element in enumerate(elements):
    rotation = element.get('rotation', {})
    cubes.append({'name': element['name'], 'uuid': uid(str(index)), 'type': 'cube', 'from': element['from'], 'to': element['to'],
                  'origin': rotation.get('origin', [8, 8, 8]), 'rotation': [0, rotation.get('angle', 0), 0], 'rescale': False,
                  'faces': {face: dict(value, texture=keys.index(value['texture'][1:])) for face, value in element['faces'].items()},
                  'autouv': 0, 'export': True, 'visibility': True})
images = [{'name': 'array_' + key + '.png', 'id': str(index), 'uuid': uid(key), 'width': 16, 'height': 16,
           'folder': 'block', 'namespace': 'simpleradio_directional',
           'uv_width': 16, 'uv_height': 16, 'source': 'data:image/png;base64,' + base64.b64encode((ASSETS / f'textures/block/array_{key}.png').read_bytes()).decode(),
           'mode': 'bitmap', 'saved': False} for index, key in enumerate(keys)]
groups = [{'name': name, 'uuid': uid(name), 'origin': [8, 8, 8], 'export': True,
           'children': [c['uuid'] for c in cubes if c['name'].startswith('mount_') == fixed]}
          for name, fixed in [('Fixed mount', True), ('Swivelling receiver', False)]]
write(KIT / 'Wide-Angle-Receiver.bbmodel', {'meta': {'format_version': '4.10', 'model_format': 'java_block', 'box_uv': False},
     'name': 'Wide-Angle Receiver Array', 'resolution': {'width': 16, 'height': 16}, 'elements': cubes,
     'outliner': groups, 'textures': images, 'display': display})
print(f'Authored {len(elements)} native cuboids, four 16x16 textures, recipe/tags, and an embedded-texture Blockbench project.')
