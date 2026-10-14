"""Generate exact 16px textures, Java block JSON, and editable Blockbench projects.

Geometry uses whole Minecraft model pixels. Conditional parts remain separate
models at their original block coordinates; they must never be recentered.
Run from any working directory with Python 3 and Pillow installed.
"""
from pathlib import Path
from PIL import Image, ImageDraw
import base64
import json
import uuid

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/simpleradio_repeaters'
TEXTURES = ASSETS / 'textures/block'
MODELS = ASSETS / 'models/block'
EDITABLE = ROOT / 'art/blockbench'
for directory in (TEXTURES, MODELS, EDITABLE):
    directory.mkdir(parents=True, exist_ok=True)
P = dict(shell='#323e3e', edge='#171e1e', brass='#716448', bolt='#b8a15f',
         recess='#121918', olive='#677056', rim='#495653', green='#91d25f',
         glass='#18302b', amber='#b59646', dark='#242a29')

def texture(name, base='shell'):
    image = Image.new('RGB', (16, 16), P[base])
    return image, ImageDraw.Draw(image)

def save_texture(name, image):
    image.save(TEXTURES / (name + '.png'))

def case_texture(name, front=False):
    image, d = texture(name)
    d.rectangle((0, 0, 15, 15), outline=P['edge'])
    d.rectangle((1, 1, 14, 14), outline=P['brass'])
    for x in (1, 14):
        for y in (1, 14):
            d.point((x, y), fill=P['bolt'])
    if not front:
        for y in (5, 7, 9, 11):
            d.line((4, y, 11, y), fill=P['edge'])
            d.point((12, y), fill=P['rim'])
    return image, d

image, d = case_texture('relay_front', True)
d.rectangle((3, 3, 12, 8), fill=P['edge'])
d.rectangle((4, 4, 11, 7), fill=P['glass'])
d.rectangle((10, 10, 13, 13), fill=P['recess'], outline=P['brass'])
d.line((10, 14, 13, 14), fill=P['bolt'])
for y in (10, 12):
    d.line((3, y, 7, y), fill=P['edge'])
    d.point((8, y), fill=P['rim'])
d.point((3, 14), fill=P['amber'])
save_texture('relay_front', image)
active = image.copy(); active.putpixel((3, 14), tuple(bytes.fromhex('91d25f')))
save_texture('relay_front_active', active)
image, d = case_texture('relay_side'); save_texture('relay_side', image)
image, d = case_texture('relay_top', True)
for x in (4, 10):
    d.rectangle((x - 1, 5, x + 2, 10), outline=P['brass'])
    d.rectangle((x, 6, x + 1, 9), fill=P['recess'])
    d.line((x, 7, x + 1, 7), fill=P['bolt'])
    d.line((x, 9, x + 1, 9), fill=P['olive'])
save_texture('relay_top', image)
image, d = texture('relay_screen', 'edge')
d.rectangle((3, 3, 12, 8), fill=P['brass'])
d.rectangle((4, 4, 11, 7), fill=P['glass'])
d.point((3, 3), fill=P['bolt']); d.point((12, 8), fill=P['bolt'])
save_texture('relay_screen', image)
image, d = texture('catalyst', 'dark')
for x, y in ((1, 1), (3, 2), (5, 5), (8, 2), (10, 8), (13, 12), (2, 11), (7, 13)):
    d.rectangle((x, y, min(x + 1, 15), min(y + 1, 15)), fill=P['brass'])
d.rectangle((0, 0, 3, 3), fill=P['dark'])
d.line((0, 0, 3, 0), fill=P['bolt']); d.point((0, 1), fill=P['brass'])
d.point((2, 2), fill=P['bolt']); d.point((3, 3), fill=P['brass'])
save_texture('catalyst', image)

image, d = texture('card_bank_front')
d.rectangle((1, 0, 14, 15), outline=P['edge'])
d.line((1, 0, 14, 0), fill=P['brass'])
for slot in range(6):
    x = 9 if slot % 2 == 0 else 1
    y = 11 - (slot // 2) * 5
    u, v = 16 - (x + 6), 16 - (y + 4)
    d.rectangle((u, v, u + 5, v + 3), fill=P['recess'])
    d.line((u, v, u + 5, v), fill=P['brass'])
    d.point((u, v), fill=P['bolt'])
    d.line((u + 1, v + 3, u + 4, v + 3), fill=P['olive'])
d.point((7, 0), fill=P['amber']); d.point((8, 0), fill=P['amber'])
save_texture('card_bank_front', image)
connected = image.copy()
for x in (7, 8): connected.putpixel((x, 0), tuple(bytes.fromhex('91d25f')))
save_texture('card_bank_front_connected', connected)
image, d = case_texture('card_bank_side'); save_texture('card_bank_side', image)
image, d = texture('card_back', 'edge')
d.rectangle((5, 6, 10, 8), fill=P['olive'])
d.line((5, 6, 10, 6), fill=P['bolt'])
d.line((5, 8, 10, 8), fill=P['brass'])
save_texture('card_back', image)
image, d = texture('connector', 'dark')
for x in range(0, 16, 2): d.line((x, 0, x, 15), fill=P['brass'])
for y in (2, 7, 12): d.line((0, y, 15, y), fill=P['bolt'])
save_texture('connector', image)

def cube(name, start, end, north='front', other='side', up='top', uv_override=None):
    x, y, z = start; X, Y, Z = end
    bounds = dict(north=[16-X, 16-Y, 16-x, 16-y], south=[x, 16-Y, X, 16-y],
                  west=[z, 16-Y, Z, 16-y], east=[16-Z, 16-Y, 16-z, 16-y],
                  up=[x, z, X, Z], down=[x, 16-Z, X, 16-z])
    if uv_override:
        bounds = uv_override if isinstance(uv_override, dict) else {face: uv_override for face in bounds}
    faces = {face: {'uv': uv, 'texture': '#' + (north if face == 'north' else up if face == 'up' else other)}
             for face, uv in bounds.items()}
    return {'name': name, 'from': start, 'to': end, 'faces': faces}

relay = [cube('housing', [0, 0, 0], [16, 15, 16])]
for name, start, end in (
    ('top_front', [0, 15, 0], [16, 16, 6]), ('top_back', [0, 15, 10], [16, 16, 16]),
    ('top_left', [0, 15, 6], [4, 16, 10]), ('top_middle', [6, 15, 6], [10, 16, 10]),
    ('top_right', [12, 15, 6], [16, 16, 10])):
    relay.append(cube(name, start, end))
relay.append(cube('screen_bezel', [3, 7, -1], [13, 13, 0], north='screen', other='side', up='side', uv_override={
    'north': [3, 3, 13, 9], 'south': [3, 3, 13, 9], 'west': [1, 3, 2, 9], 'east': [1, 3, 2, 9],
    'up': [3, 1, 13, 2], 'down': [3, 1, 13, 2]}))
catalyst = [cube('inserted_catalyst', [2, 2, -2], [6, 6, 1], north='catalyst', other='catalyst', up='catalyst', uv_override={
    'north': [0, 0, 4, 4], 'south': [0, 0, 4, 4], 'west': [0, 0, 3, 4], 'east': [0, 0, 3, 4],
    'up': [0, 0, 4, 3], 'down': [0, 0, 4, 3]})]
bank = [cube('rack_housing', [1, 2, 2], [15, 16, 10]), cube('slot_backing', [1, 0, 1], [15, 16, 2])]
for x in (4, 10):
    bank.append(cube('connector_' + str(x), [x, -1, 6], [x+2, 2, 10], north='connector', other='connector', up='connector', uv_override={
        'north': [0, 0, 2, 3], 'south': [0, 0, 2, 3], 'west': [0, 0, 4, 3], 'east': [0, 0, 4, 3],
        'up': [0, 0, 2, 4], 'down': [0, 0, 2, 4]}))
cards = []
for slot in range(6):
    x = 9 if slot % 2 == 0 else 1; y = 11 - (slot // 2) * 5
    bank.append(cube('slot_' + str(slot+1) + '_lip', [x, y+3, 0], [x+6, y+4, 1]))
    cards.append([cube('card_' + str(slot+1), [x, y, 0], [x+6, y+3, 1], north='card', other='card', up='card', uv_override={
        'north': [5, 6, 11, 9], 'south': [5, 6, 11, 9], 'west': [5, 6, 6, 9], 'east': [10, 6, 11, 9],
        'up': [5, 6, 11, 7], 'down': [5, 8, 11, 9]})])

RELAY_TEX = dict(front='relay_front', side='relay_side', top='relay_top', screen='relay_screen', catalyst='catalyst')
BANK_TEX = dict(front='card_bank_front', side='card_bank_side', top='card_bank_side', connector='connector', card='card_back')

def java_model(name, elements, textures, item=False):
    model = {'parent': 'minecraft:block/block', 'textures': {key: 'simpleradio_repeaters:block/' + value for key, value in textures.items()},
             'elements': elements}
    model['textures']['particle'] = model['textures']['side']
    if item:
        model['display'] = {
            'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [.65, .65, .65]},
            'fixed': {'rotation': [0, 180, 0], 'translation': [0, 0, 0], 'scale': [.5, .5, .5]},
            'ground': {'translation': [0, 3, 0], 'scale': [.25, .25, .25]},
            'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [.375, .375, .375]}}
    (MODELS / (name + '.json')).write_text(json.dumps(model, indent=2) + '\n')

java_model('relay', relay, RELAY_TEX, True)
java_model('relay_active', relay, {**RELAY_TEX, 'front': 'relay_front_active'}, True)
java_model('relay_catalyst', catalyst, RELAY_TEX)
java_model('channel_bank', bank, BANK_TEX, True)
java_model('channel_bank_connected', bank, {**BANK_TEX, 'front': 'card_bank_front_connected'}, True)
for slot, parts in enumerate(cards): java_model('bank_card_' + str(slot), parts, BANK_TEX)

def bbmodel(name, textures, groups):
    names = list(dict.fromkeys(textures.values())); elements = []; outliner = []
    for group_name, parts in groups:
        children = []
        for part in parts:
            uid = str(uuid.uuid5(uuid.NAMESPACE_URL, name + '/' + group_name + '/' + part['name']))
            element = {**part, 'uuid': uid, 'type': 'cube', 'origin': [8, 8, 8], 'rotation': [0, 0, 0],
                       'box_uv': False, 'visibility': True, 'export': True, 'autouv': 0, 'rescale': False,
                       'color': 0, 'uv_offset': [0, 0]}
            element['faces'] = {face: {**info, 'texture': names.index(textures[info['texture'][1:]]), 'rotation': 0}
                                for face, info in part['faces'].items()}
            elements.append(element); children.append(uid)
        outliner.append({'name': group_name, 'uuid': str(uuid.uuid5(uuid.NAMESPACE_URL, name + '/' + group_name)),
                         'origin': [8, 8, 8], 'rotation': [0, 0, 0], 'export': True,
                         'visibility': True, 'isOpen': True, 'children': children})
    tex = []
    for index, name_texture in enumerate(names):
        filename = name_texture + '.png'; data = (TEXTURES / filename).read_bytes()
        tex.append({'name': filename, 'id': str(index), 'uuid': str(uuid.uuid5(uuid.NAMESPACE_URL, name + '/texture/' + name_texture)),
                    'source': 'data:image/png;base64,' + base64.b64encode(data).decode(), 'width': 16, 'height': 16,
                    'uv_width': 16, 'uv_height': 16, 'mode': 'bitmap', 'saved': True, 'render_mode': 'default',
                    'namespace': 'simpleradio_repeaters', 'folder': 'block', 'particle': name_texture == textures['side'], 'visible': True})
    project = {'meta': {'format_version': '4.10', 'model_format': 'java_block', 'box_uv': False},
               'name': name, 'model_identifier': name, 'visible_box': [1, 1, 0],
               'resolution': {'width': 16, 'height': 16}, 'elements': elements,
               'outliner': outliner, 'textures': tex, 'animations': []}
    (EDITABLE / (name + '.bbmodel')).write_text(json.dumps(project, indent=2) + '\n')

bbmodel('Relay', RELAY_TEX, [('base', relay), ('inserted_catalyst', catalyst)])
bbmodel('Repeater-Bank', BANK_TEX, [('base', bank)] + [('inserted_card_' + str(i+1), parts) for i, parts in enumerate(cards)])
for name, flag, active in (('relay', 'active', 'relay_active'), ('channel_bank', 'connected', 'channel_bank_connected')):
    variants = {}
    for facing, rotation in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        for value, model in (('false', name), ('true', active)):
            variant = {'model': 'simpleradio_repeaters:block/' + model}
            if rotation: variant['y'] = rotation
            variants['facing=' + facing + ',' + flag + '=' + value] = variant
    (ASSETS / 'blockstates' / (name + '.json')).write_text(json.dumps({'variants': variants}, indent=2) + '\n')
    (ASSETS / 'models/item' / (name + '.json')).write_text(json.dumps({'parent': 'simpleradio_repeaters:block/' + name}, indent=2) + '\n')
print('Generated Relay, six-card Bank, seven conditional models, and two editable Blockbench projects.')
