"""Generate code-native 0.4 models/recipes using the existing 16px palette and native Minecraft textures.

No bitmap is generated or edited. Existing Bank artwork is untouched. Rerunning replaces only the
new assets and the named attachment-socket elements added to both Relay variants.
"""
import copy
import json
from pathlib import Path
from PIL import Image

PROJECT = Path(__file__).resolve().parents[1]
ASSETS = PROJECT / 'src/main/resources/assets/simpleradio_repeaters'
DATA = PROJECT / 'src/main/resources/data/simpleradio_repeaters'


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n')


def pixel(texture, color):
    im = Image.open(ASSETS / ('textures/block/' + texture + '.png')).convert('RGB')
    x, y = min(((x, y) for y in range(16) for x in range(16)),
               key=lambda xy: sum((a - b) ** 2 for a, b in zip(im.getpixel(xy), color)))
    return [x, y, x + 1, y + 1]


GOLD = pixel('card_back', (185, 161, 95))
DARK = pixel('card_back', (24, 30, 26))
METAL = pixel('relay_side', (49, 59, 57))
GREEN = pixel('relay_screen', (24, 45, 35))


def cube(name, start, end, texture, uv=None):
    return {'name': name, 'from': start, 'to': end,
            'faces': {face: {'uv': uv or [0, 0, 16, 16], 'texture': texture}
                      for face in ['north', 'south', 'east', 'west', 'up', 'down']}}


def plane(name, start, end, texture, uv):
    return {'name': name, 'from': start, 'to': end, 'shade': False,
            'faces': {'north': {'uv': uv, 'texture': texture}}}


def relay_socket():
    for variant in ['relay', 'relay_active']:
        path = ASSETS / ('models/block/' + variant + '.json')
        model = json.loads(path.read_text())
        model['textures']['attachment_frame'] = 'simpleradio_repeaters:block/card_back'
        model['elements'] = [e for e in model['elements'] if not e.get('name', '').startswith('attachment_')]
        for name, a, b in [
            ('left', [9, 1, -1], [10, 7, 0]), ('right', [14, 1, -1], [15, 7, 0]),
            ('bottom', [10, 1, -1], [14, 2, 0]), ('top', [10, 6, -1], [14, 7, 0]),
        ]:
            model['elements'].append(cube('attachment_' + name, a, b, '#attachment_frame', GOLD))
        model['elements'].append(plane('attachment_socket', [10, 2, -.01], [14, 6, -.01], '#attachment_frame', DARK))
        write(path, model)


def transponder():
    bank = json.loads((ASSETS / 'models/block/channel_bank.json').read_text())
    textures = {'side': 'simpleradio_repeaters:block/card_bank_side', 'metal': 'simpleradio_repeaters:block/relay_side',
                'trim': 'simpleradio_repeaters:block/card_back', 'screen': 'simpleradio_repeaters:block/relay_screen',
                'particle': 'simpleradio_repeaters:block/card_bank_side'}
    elements = [copy.deepcopy(e) for e in bank['elements'] if e['name'] in ['rack_housing', 'connector_4', 'connector_10']]
    for e in elements:
        for f in e['faces'].values():
            f['texture'] = '#side' if f['texture'] in ['#0', '#1'] else '#trim'
    elements += [cube('front_plate', [1, 1, 1], [15, 16, 2], '#metal', METAL),
                 cube('top_trim', [1, 15, 0], [15, 16, 1], '#trim', GOLD),
                 cube('bottom_trim', [1, 1, 0], [15, 2, 1], '#trim', GOLD),
                 cube('screen_bezel', [6, 5, 0], [10, 11, 1], '#trim', GOLD),
                 plane('screen_glass', [6.25, 5.25, -.01], [9.75, 10.75, -.01], '#screen', GREEN)]
    positions = [(10, 6), (1, 10), (1, 2)]
    for slot, (x, y) in enumerate(positions):
        elements += [cube('slot_' + str(slot) + '_lip', [x, y + 3, 0], [x + 5, y + 4, 1], '#trim', GOLD),
                     plane('slot_' + str(slot) + '_recess', [x, y, .99], [x + 5, y + 3, .99], '#trim', DARK)]
        card = json.loads((ASSETS / 'models/block/bank_card_0.json').read_text())
        card['elements'] = [cube('card_' + str(slot), [x, y, 0], [x + 5, y + 3, 1], '#3', [5, 6, 11, 9]),
                            plane('label_' + str(slot), [x + 1, y + 1, -.004], [x + 4, y + 2, -.004], '#3', DARK)]
        write(ASSETS / ('models/block/transponder_card_' + str(slot) + '.json'), card)
    model = {'parent': 'minecraft:block/block', 'textures': textures, 'elements': elements,
             'display': json.loads((ASSETS / 'models/block/relay.json').read_text()).get('display', {})}
    write(ASSETS / 'models/block/transponder.json', model)
    # Connection LED uses the current native Bank atlas, independent of the dynamic mode arrows.
    active = copy.deepcopy(model)
    active['textures']['led'] = 'simpleradio_repeaters:block/card_bank_front_connected'
    active['elements'].append(plane('connected_led', [7, 14, -.01], [9, 15, -.01], '#led', [7, 1, 9, 2]))
    write(ASSETS / 'models/block/transponder_connected.json', active)
    states = json.loads((ASSETS / 'blockstates/channel_bank.json').read_text())
    for v in states['variants'].values():
        v['model'] = v['model'].replace('channel_bank', 'transponder')
    write(ASSETS / 'blockstates/transponder.json', states)
    write(ASSETS / 'models/item/transponder.json', {'parent': 'simpleradio_repeaters:block/transponder'})
    loot = json.loads((DATA / 'loot_tables/blocks/channel_bank.json').read_text())
    loot['pools'][0]['entries'][0]['name'] = 'simpleradio_repeaters:transponder'
    write(DATA / 'loot_tables/blocks/transponder.json', loot)


def component(name, case, accent, core, cap):
    textures = {'case': case, 'accent': accent, 'core': core, 'cap': cap,
                'contact': 'minecraft:block/copper_block', 'dark': 'minecraft:block/black_concrete', 'particle': case}
    pieces = [cube('case', [3, 4, 7], [13, 14, 9], '#case'),
              cube('panel', [4, 5, 6], [12, 13, 7], '#dark'),
              cube('left_rail', [3, 5, 5], [5, 12, 7], '#accent'),
              cube('right_rail', [11, 5, 5], [13, 12, 7], '#accent'),
              cube('cap', [5, 13, 5], [11, 15, 9], '#cap'),
              plane('core', [5, 6, 4.99], [11, 12, 4.99], '#core', [0, 0, 16, 16])]
    for x in [4, 7, 10]:
        pieces.append(cube('contact_' + str(x), [x, 1, 7], [x + 2, 5, 9], '#contact'))
    display = {'gui': {'rotation': [15, 150, 0], 'scale': [1, 1, 1]},
               'fixed': {'rotation': [0, 0, 0], 'scale': [1, 1, 1]},
               'ground': {'translation': [0, 2, 0], 'scale': [.5, .5, .5]},
               'thirdperson_righthand': {'rotation': [0, 90, 0], 'scale': [.5, .5, .5]}}
    write(ASSETS / ('models/item/' + name + '.json'),
          {'parent': 'minecraft:block/block', 'ambientocclusion': False, 'textures': textures, 'elements': pieces, 'display': display})


def recipe(name, pattern, keys):
    write(DATA / ('recipes/' + name + '.json'), {'type': 'minecraft:crafting_shaped', 'category': 'redstone',
          'pattern': pattern, 'key': {k: ([{'item': x} for x in v] if isinstance(v, list) else {'item': v}) for k, v in keys.items()},
          'result': {'item': 'simpleradio_repeaters:' + name, 'count': 1}})


def main():
    relay_socket(); transponder()
    component('nether_focus', 'minecraft:block/obsidian', 'minecraft:block/netherrack', 'minecraft:item/quartz', 'minecraft:block/obsidian')
    component('end_focus', 'minecraft:block/end_stone', 'minecraft:block/purpur_block', 'minecraft:item/ender_pearl', 'minecraft:block/end_stone')
    component('relay_amplifier', 'minecraft:block/ancient_debris_side', 'minecraft:block/end_rod', 'minecraft:item/ender_eye', 'minecraft:block/ancient_debris_top')
    wire = 'simpleradio:copper_wire'
    recipe('nether_focus', [' O ', 'NQN', ' W '], {'O': 'minecraft:obsidian', 'N': 'minecraft:netherrack', 'Q': 'minecraft:quartz', 'W': wire})
    recipe('end_focus', [' P ', 'ECE', ' W '], {'P': 'minecraft:ender_pearl', 'E': 'minecraft:end_stone', 'C': 'minecraft:chorus_fruit', 'W': wire})
    recipe('relay_amplifier', [' D ', 'RER', ' W '], {'D': 'minecraft:ancient_debris', 'R': 'minecraft:end_rod', 'E': 'minecraft:ender_eye', 'W': wire})
    recipe('transponder', ['IGI', 'RCT', 'IWI'], {'I': 'minecraft:iron_ingot', 'G': 'minecraft:glass', 'R': 'simpleradio:receiving_module',
           'C': ['minecraft:amethyst_block', 'minecraft:gilded_blackstone'], 'T': 'simpleradio:transmitting_module', 'W': wire})
    path = ASSETS / 'lang/en_us.json'; lang = json.loads(path.read_text())
    lang.update({'block.simpleradio_repeaters.transponder': 'Radio Transponder',
                 'item.simpleradio_repeaters.nether_focus': 'Nether Focus', 'item.simpleradio_repeaters.end_focus': 'End Focus',
                 'item.simpleradio_repeaters.relay_amplifier': 'Amplification Focus',
                 'tooltip.simpleradio_repeaters.transponder': 'Plug into a Relay. Left card to right cards; click the display to toggle simplex/duplex.',
                 'tooltip.simpleradio_repeaters.nether_focus': 'Attach to an overworld Relay to project a weak signal into the Nether. Attach to a Nether relay to improve signal reliability and reach.',
                 'tooltip.simpleradio_repeaters.end_focus': 'Attach to an overworld Relay to project a decent signal into the End, and vice versa.',
                 'tooltip.simpleradio_repeaters.amplifier': 'Attach to a Relay to amplify the attached Antenna power, and any signals passing through.'})
    write(path, lang)


if __name__ == '__main__':
    main()
