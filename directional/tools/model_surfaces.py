"""Offline Java-model surface trimming and transformed coplanar-face inspection."""
from copy import deepcopy
import itertools
import math

EPS = 1e-7
# Plane axis, outward sign, U axis/direction, V axis/direction (Minecraft FaceBakery).
FRAMES = {'north': (2, -1, 0, -1, 1, -1), 'south': (2, 1, 0, 1, 1, -1),
          'west': (0, -1, 2, 1, 1, -1), 'east': (0, 1, 2, -1, 1, -1),
          'down': (1, -1, 0, 1, 2, -1), 'up': (1, 1, 0, 1, 2, 1)}


def motion(name):
    if name.startswith(('mount_clamp_', 'mount_bolt_')):
        return 'clamps'
    return 'mount' if name.startswith('mount_') else 'head'


def rotation_key(e):
    r = e.get('rotation', {})
    return (r['axis'], r['angle'], *r['origin']) if r.get('angle', 0) else ()


def rect(e, axes):
    return tuple(e['from'][a] for a in axes) + tuple(e['to'][a] for a in axes)


def subtract(a, b):
    x0, y0, x1, y1 = a
    u0, v0, u1, v1 = max(x0, b[0]), max(y0, b[1]), min(x1, b[2]), min(y1, b[3])
    if u1 - u0 < EPS or v1 - v0 < EPS:
        return [a]
    return [r for r in [(x0, y0, u0, y1), (u1, y0, x1, y1),
                         (u0, y0, u1, v0), (u0, v1, u1, y1)]
            if r[2] - r[0] > EPS and r[3] - r[1] > EPS]


def crop_face(e, face, bounds):
    result = deepcopy(e['faces'][face])
    assert result.get('rotation', 0) == 0
    _, _, ua, us, va, vs = FRAMES[face]
    u0, v0, u1, v1 = result['uv']
    limits = []
    for axis, sign, start, end in [(ua, us, u0, u1), (va, vs, v0, v1)]:
        low, high = e['from'][axis], e['to'][axis]
        a, b = bounds[0][axis], bounds[1][axis]
        f0, f1 = ((a-low)/(high-low), (b-low)/(high-low)) if sign > 0 else ((high-b)/(high-low), (high-a)/(high-low))
        limits.append((start+(end-start)*f0, start+(end-start)*f1))
    result['uv'] = [limits[0][0], limits[1][0], limits[0][1], limits[1][1]]
    return result


def clean(model):
    """Keep the exterior of unions, with each coplanar patch owned by one face.

    Separate motion groups prevent a swivelling head from losing faces merely
    because the stationary support covers them in the straight-ahead pose.
    UVs are cropped, never fitted to the smaller fragments.
    """
    original = model['elements']
    groups = [(motion(e['name']), rotation_key(e)) for e in original]
    result = []
    for index, e in enumerate(original):
        base = deepcopy(e)
        base['faces'] = {}
        extras = []
        for face, material in e['faces'].items():
            axis, sign, ua, _, va, _ = FRAMES[face]
            plane = e['to' if sign > 0 else 'from'][axis]
            initial = rect(e, (ua, va))
            pieces = [initial]
            for j, other in enumerate(original):
                if j == index or groups[j] != groups[index]:
                    continue
                low, high = other['from'][axis], other['to'][axis]
                covered = (low <= plane+EPS and high > plane+EPS) if sign > 0 else (low < plane-EPS and high >= plane-EPS)
                duplicate = j > index and face in other['faces'] and abs(other['to' if sign > 0 else 'from'][axis]-plane) < EPS
                if covered or duplicate:
                    cut = rect(other, (ua, va))
                    pieces = [part for piece in pieces for part in subtract(piece, cut)]
                if not pieces:
                    break
            if pieces == [initial]:
                base['faces'][face] = deepcopy(material)
            else:
                for part_index, piece in enumerate(pieces):
                    fragment = deepcopy(e)
                    prefix = e['name'] if motion(e['name']) != 'head' else 'surface_' + e['name']
                    fragment['name'] = f'{prefix}_surface_{face}_{part_index}'
                    for a, value in zip((ua, va), piece[:2]): fragment['from'][a] = value
                    for a, value in zip((ua, va), piece[2:]): fragment['to'][a] = value
                    fragment['faces'] = {face: crop_face(e, face, (fragment['from'], fragment['to']))}
                    extras.append(fragment)
        if base['faces']:
            result.append(base)
        result.extend(extras)
    return dict(model, elements=result)


def rotate(point, axis, degrees, origin):
    p = [a-b for a, b in zip(point, origin)]
    a, b = {'x': (1, 2), 'y': (2, 0), 'z': (0, 1)}[axis]
    c, s = math.cos(math.radians(degrees)), math.sin(math.radians(degrees))
    p[a], p[b] = p[a]*c-p[b]*s, p[a]*s+p[b]*c
    return [a+b for a, b in zip(p, origin)]


def dot(a, b): return sum(x*y for x, y in zip(a, b))


def signed_area(p):
    return sum(a[0]*b[1]-b[0]*a[1] for a, b in zip(p, p[1:]+p[:1])) / 2


def intersection_area(a, b):
    if signed_area(b) < 0: b = list(reversed(b))
    output = list(a)
    for c, d in zip(b, b[1:]+b[:1]):
        source, output = output, []
        if not source: break
        def side(p): return (d[0]-c[0])*(p[1]-c[1])-(d[1]-c[1])*(p[0]-c[0])
        previous = source[-1]
        for current in source:
            x, y = side(previous), side(current)
            if (x >= -EPS) != (y >= -EPS):
                t = x/(x-y)
                output.append([previous[i]+t*(current[i]-previous[i]) for i in range(2)])
            if y >= -EPS: output.append(current)
            previous = current
    return abs(signed_area(output)) if len(output) >= 3 else 0


def faces(model, aim=0, offset=(0, 0, 0), yaw=0, brackets=True):
    result = []
    for e in model['elements']:
        if not brackets and motion(e.get('name', '')) == 'clamps': continue
        for face in e['faces']:
            axis, sign, ua, _, va, _ = FRAMES[face]
            plane = e['to' if sign > 0 else 'from'][axis]
            points = []
            for u, v in [(0, 0), (1, 0), (1, 1), (0, 1)]:
                p = e['from'].copy(); p[axis] = plane
                p[ua] = e['to' if u else 'from'][ua]; p[va] = e['to' if v else 'from'][va]
                r = e.get('rotation', {})
                if r.get('angle', 0): p = rotate(p, r['axis'], r['angle'], r['origin'])
                if aim and motion(e.get('name', '')) == 'head': p = rotate(p, 'y', aim, [8, 8, 8])
                if yaw: p = rotate(p, 'y', yaw, [8, 8, 8])
                points.append([x+y for x, y in zip(p, offset)])
            a, b = [[p[i]-points[0][i] for i in range(3)] for p in points[1:3]]
            n = [a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0]]
            length = math.sqrt(dot(n, n))
            if length < EPS: continue
            n = [x/length for x in n]
            if next(x for x in n if abs(x) > EPS) < 0: n = [-x for x in n]
            distance = dot(n, points[0])
            key = tuple(round(x, 6) for x in n+[distance])
            # All faces in a plane need the same projection, including exact 45-degree ties.
            dropped = max(range(3), key=lambda i: abs(key[i]))
            axes = [i for i in range(3) if i != dropped]
            polygon = [[p[i] for i in axes] for p in points]
            result.append({'key': key, 'polygon': polygon,
                           'name': e.get('name', 'native mast')+'/'+face})
    return result


def overlaps(surface_list):
    groups = {}
    for face in surface_list: groups.setdefault(face['key'], []).append(face)
    result = []
    for group in groups.values():
        for a, b in itertools.combinations(group, 2):
            p, q = a['polygon'], b['polygon']
            if any(min(max(x[i] for x in p), max(x[i] for x in q))-max(min(x[i] for x in p), min(x[i] for x in q)) < EPS for i in range(2)):
                continue
            area = intersection_area(p, q)
            if area > EPS: result.append({'a': a['name'], 'b': b['name'], 'area': round(area, 6)})
    return result
