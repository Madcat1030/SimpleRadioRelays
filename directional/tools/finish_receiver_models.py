"""Finish authored receiver assets: common mast hardware, no duplicate rendered faces."""
import base64
from copy import deepcopy
import json
import math
from pathlib import Path
import uuid
from model_surfaces import clean, faces, overlaps, motion, crop_face

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources'
ASSETS = RES / 'assets/simpleradio_directional'
KIT = ROOT / 'art/model-kit'


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n')


def cube(name, start, end, texture='iron'):
    x, y, z = [b-a for a, b in zip(start, end)]
    uv = {'north': [0, 0, x, y], 'south': [0, 0, x, y], 'west': [0, 0, z, y],
          'east': [0, 0, z, y], 'up': [0, 0, x, z], 'down': [0, 0, x, z]}
    assert max(x, y, z) <= 16
    return {'name': name, 'from': start, 'to': end,
            'faces': {f: {'uv': v, 'texture': '#'+texture} for f, v in uv.items()}}


def mounting():
    result = []
    # Square rings centered on the mast. Every inner face is one whole unit
    # clear of the native column's X=4/12 and Z=4/12 texture planes.
    # Lower ring moves up one unit, upper ring down one unit.
    for y in [2, 11]:
        for side, a, b in [
            ('front', [3, y, 19], [13, y+3, 21]),
            ('back', [3, y, 27], [13, y+3, 29]),
            ('left', [3, y, 21], [5, y+3, 27]),
            ('right', [11, y, 21], [13, y+3, 27])]:
            result.append(cube(f'mount_clamp_{y}_{side}', a, b))
        for x in [3, 11]:
            result.append(cube(f'mount_bolt_{y}_{x}_front', [x, y+1, 18], [x+2, y+2, 19], 'cable'))
            result.append(cube(f'mount_bolt_{y}_{x}_back', [x, y+1, 29], [x+2, y+2, 30], 'cable'))
    for label, y in [('lower', 3), ('upper', 12)]:
        result.append(cube('mount_arm_'+label, [6, y, 10], [10, y+1, 22]))
    result.extend([cube('mount_pivot_lower', [5, -2, 10], [11, -1, 16]),
                   cube('mount_pivot_upper', [5, 17, 10], [11, 18, 16]),
                   cube('mount_pivot_pin_lower', [7, -1, 10], [9, 8, 12]),
                   cube('mount_pivot_pin_upper', [7, 8, 10], [9, 17, 12])])
    return result


def blockbench(model, path):
    keys = [k for k in model['textures'] if k != 'particle']
    def uid(name): return str(uuid.uuid5(uuid.NAMESPACE_URL, 'directional/surface-finish/'+path.stem+'/'+name))
    elements = []
    for index, e in enumerate(model['elements']):
        r = e.get('rotation', {}); rotation = [0, 0, 0]
        if r: rotation['xyz'.index(r['axis'])] = r.get('angle', 0)
        fs = {f: {'uv': [0, 0, 0, 0], 'texture': None} for f in ['north', 'south', 'east', 'west', 'up', 'down']}
        fs.update({f: dict(v, texture=keys.index(v['texture'][1:])) for f, v in e['faces'].items()})
        elements.append({'name': e['name'], 'uuid': uid(str(index)), 'type': 'cube', 'from': e['from'], 'to': e['to'],
                         'origin': r.get('origin', [8, 8, 8]), 'rotation': rotation, 'rescale': False,
                         'faces': fs, 'autouv': 0, 'export': True, 'visibility': True})
    textures = []
    for i, key in enumerate(keys):
        namespace, name = model['textures'][key].split(':', 1)
        source = ASSETS if namespace == 'simpleradio_directional' else KIT/'native-reference/assets'/namespace
        raw = (source/'textures'/(name+'.png')).read_bytes()
        textures.append({'name': Path(name).name+'.png', 'id': str(i), 'uuid': uid(key), 'width': 16, 'height': 16,
                         'uv_width': 16, 'uv_height': 16, 'folder': 'block', 'namespace': namespace,
                         'source': 'data:image/png;base64,'+base64.b64encode(raw).decode(), 'mode': 'bitmap', 'saved': False})
    outliner = [{'name': label, 'uuid': uid(label), 'origin': [8, 8, 8], 'export': True,
                 'children': [e['uuid'] for e in elements if motion(e['name']) == kind]}
                for kind, label in [('clamps', 'Shared mast rings'), ('mount', 'Fixed receiver support'), ('head', 'Swivelling head')]]
    write(path, {'meta': {'format_version': '4.10', 'model_format': 'java_block', 'box_uv': False},
                 'name': path.stem, 'resolution': {'width': 16, 'height': 16}, 'elements': elements,
                 'outliner': outliner, 'textures': textures, 'display': model['display']})


def finish():
    models = [('parabolic_receiver', 'Parabolic-Receiver', None),
              ('parabolic_receiver_mesh', 'Parabolic-Receiver-Mesh', 'mesh'),
              ('parabolic_receiver_octagonal', 'Parabolic-Receiver-Octagonal', 'octagonal'),
              ('parabolic_receiver_refined', 'Parabolic-Receiver-Refined', 'refined'),
              ('wide_angle_receiver', 'Wide-Angle-Receiver', None)]
    counts = {}
    for name, editable, style in models:
        path = ASSETS/'models/block'/(name+'.json')
        model = json.loads(path.read_text())
        assert 'surface-checked' not in model['credit'], 'Regenerate raw assets with make_receiver_assets.py first'
        model['elements'] = [e for e in model['elements'] if not e['name'].startswith('mount_')]
        for e in model['elements']:
            before_resize = deepcopy(e)
            if e['name'].startswith(('rear_vertical', 'rear_horizontal')):
                e['from'][2] = 13
            if e['name'] == 'rear_saddle' and name in {'parabolic_receiver', 'parabolic_receiver_mesh'}:
                e['from'] = [6, 5, 9]; e['to'] = [10, 11, 15]
            if name == 'parabolic_receiver_refined':
                if e['name'].startswith(('copper_rolled_rim_', 'inner_bevel_', 'rim_rear_lip_')):
                    axis = max([0, 1], key=lambda a: e['to'][a]-e['from'][a])
                    width = min(e['to'][a]-e['from'][a] for a in [0, 1])
                    trim = (width*math.tan(math.pi/16)+.12)/2
                    e['from'][axis] += trim; e['to'][axis] -= trim
                if e['name'].startswith('inner_bevel_'): e['to'][2] -= .15
                if e['name'].startswith('rim_rear_lip_'): e['from'][2] -= .4
                if e['name'].startswith('copper_rolled_rim_'): e['to'][2] += .1
                if e['name'].startswith('rim_fastener_'): e['to'][2] += .1
                if e['name'] == 'rear_saddle':
                    e['from'][1] = 5; e['to'][1] = 11; e['to'][2] = 12.25
                if e['name'].startswith('rear_saddle_bolt_'):
                    e['from'][2] = 12.25; e['to'][2] = 12.75
                if e['name'] in {'feed_lower_crossmember', 'feed_upper_crossmember'}:
                    e['from'][0] = 5.75; e['to'][0] = 10.25
            if name == 'parabolic_receiver_octagonal':
                if e['name'].startswith('rim_clip_'): e['to'][2] += 1
                if e['name'].startswith('inner_bevel_corner_'):
                    for axis in [0, 1]:
                        delta = -1 if e['rotation']['origin'][axis] > 8 else 1
                        e['from'][axis] += delta; e['to'][axis] += delta; e['rotation']['origin'][axis] += delta
                    e['from'][2] = -1; e['to'][2] = 3
                    e['from'][0] += 1; e['to'][0] -= 1
                if e['name'] == 'rear_saddle': e['to'][2] = 13
            if e['from'] != before_resize['from'] or e['to'] != before_resize['to']:
                e['faces'] = {face: crop_face(before_resize, face, (e['from'], e['to'])) for face in e['faces']}
                for face, value in e['faces'].items():
                    if any(v < 0 or v > 16 for v in value['uv']):
                        from model_surfaces import FRAMES
                        _, _, u, _, v, _ = FRAMES[face]
                        value['uv'] = [0, 0, e['to'][u]-e['from'][u], e['to'][v]-e['from'][v]]
        model['elements'].extend(mounting())
        before = sum(len(e['faces']) for e in model['elements'])
        model = clean(model)
        model['credit'] += ' / surface-checked 0.2.5'
        write(path, model); blockbench(model, KIT/(editable+'.bbmodel'))
        if style:
            folder = RES/f'resourcepacks/{style}_parabolic/assets/simpleradio_directional/models/block'
            write(folder/'parabolic_receiver.json', model)
            for suffix, kind in [('_mount', 'mount'), ('_head', 'head'), ('_clamps', 'clamps')]:
                write(folder/('parabolic_receiver'+suffix+'.json'), dict(model,
                      elements=[e for e in model['elements'] if motion(e['name']) == kind]))
        counts[name] = {'authored_faces': before, 'finished_faces': sum(len(e['faces']) for e in model['elements']),
                        'elements': len(model['elements']), 'remaining_overlap_counts_by_aim': {str(a): len(overlaps(faces(model, a))) for a in range(-45, 46, 15)}}
    write(ROOT/'validation/0.2.5/surface-cleanup.json', counts)
    print(json.dumps(counts, indent=2))


if __name__ == '__main__': finish()
