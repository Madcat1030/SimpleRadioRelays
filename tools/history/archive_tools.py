"""Read archived source safely and preserve its exact file bytes and Git modes."""
import hashlib
import json
import re
import stat
import zipfile
from pathlib import PurePosixPath

POLICY_VERSION = 1
RUNTIME_DIRS = {'.git', '.gradle', 'build', 'run', 'run-client', 'run-server',
                'logs', 'crash-reports', '__pycache__', 'node_modules', '__MACOSX',
                '.idea', '.vscode'}

def exclusion(path):
    parts = PurePosixPath(path).parts
    if any(part in RUNTIME_DIRS for part in parts):
        return 'generated/cache/runtime directory'
    if path.lower().endswith(('.log', '.pyc', '.class')):
        return 'generated log or bytecode'
    if parts[-1] in {'.DS_Store', 'Thumbs.db', 'local.properties'}:
        return 'machine-local file'
    if path.lower().endswith('.jar') and path != 'gradle/wrapper/gradle-wrapper.jar':
        return 'built or dependency jar'
    return None

def blob_id(content):
    return hashlib.sha1(b'blob ' + str(len(content)).encode() + b'\0' + content).hexdigest()

def read_archive(path):
    files, omitted = {}, []
    with zipfile.ZipFile(path) as archive:
        infos = archive.infolist()
        roots = {PurePosixPath(i.filename).parts[0] for i in infos}
        if len(roots) != 1 or len(infos) > 50000 or sum(i.file_size for i in infos) > 500_000_000:
            raise ValueError(f'Unexpected archive layout/size: {path.name}')
        root = roots.pop()
        for info in infos:
            member = PurePosixPath(info.filename)
            if member.is_absolute() or '..' in member.parts or '\\' in info.filename:
                raise ValueError(f'Unsafe archive member: {info.filename}')
            if stat.S_ISLNK(info.external_attr >> 16):
                raise ValueError(f'Symlink in source archive: {info.filename}')
            if info.is_dir():
                continue
            rel = str(PurePosixPath(*member.parts[1:]))
            if rel in {'.', ''} or rel in files:
                raise ValueError(f'Duplicate/empty archive path: {info.filename}')
            content = archive.read(info)  # Also verifies each member's CRC, including omitted files.
            reason = exclusion(rel)
            if reason:
                omitted.append({'path': rel, 'size': info.file_size, 'reason': reason})
                continue
            mode = '100755' if (info.external_attr >> 16) & 0o111 else '100644'
            files[rel] = {'content': content, 'mode': mode, 'oid': blob_id(content)}
    return files, omitted, root

def versions(files):
    props = files['gradle.properties']['content'].decode('utf-8')
    values = dict(re.findall(r'^(mod_version|foci_version|directional_version)=(.*)$', props, re.M))
    result = {'repeaters': values['mod_version'].strip()}
    for module in ('foci', 'directional'):
        key = module + '/build.gradle'
        if key not in files:
            continue
        if module + '_version' in values:
            result[module] = values[module + '_version'].strip()
        else:
            build = files[key]['content'].decode('utf-8')
            literal = re.search(r'''version\s*=\s*['"]([^'"]+)''', build)
            result[module] = literal.group(1) if literal else result['repeaters']
    return result

def module_hashes(files):
    result = {}
    for module in ('repeaters', 'foci', 'directional'):
        prefix = ('core/' if 'core/build.gradle' in files else '') if module == 'repeaters' else module + '/'
        rows = [(p[len(prefix):], value['mode'], value['oid']) for p, value in sorted(files.items())
                if p.startswith(prefix + 'src/') or p == prefix + 'build.gradle']
        if rows:
            result[module] = hashlib.sha256(json.dumps(rows, separators=(',', ':')).encode()).hexdigest()
    return result

def snapshot_label(name):
    for prefix, kind in [('Simple-Radio-Repeaters-', 'repeaters'),
                         ('Simple-Radio-Directional-Antenna-', 'directional'),
                         ('Simple-Radio-Addons-', 'suite')]:
        if name.startswith(prefix):
            version = re.sub(r'-source\.zip$', '', name[len(prefix):], flags=re.I).lower()
            return kind, version, 'snapshot/' + kind + '-' + version
    raise ValueError('Unrecognized source archive: ' + name)
