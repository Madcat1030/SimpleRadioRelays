"""Verify release copies and source-set/module boundaries without rewriting bytecode."""
from pathlib import Path
from zipfile import ZipFile
import hashlib
import json
import struct

root = Path(__file__).resolve().parents[1]
properties = dict(line.split('=', 1) for line in (root / 'gradle.properties').read_text().splitlines()
                  if '=' in line and not line.startswith('#'))
version = properties['mod_version'].strip()
api_key = 'simpleradio_repeaters:foci_api'
reports = []
for module in ('core', 'foci', 'directional'):
    folder = root / module / 'build/libs'
    originals = [path for path in folder.glob('*.jar')
                 if not path.name.endswith(('-prototype.jar', '-sources.jar', '-dev.jar'))]
    assert len(originals) == 1, originals
    original = originals[0]
    copy = original.with_name(original.stem + '-prototype.jar')
    assert original.read_bytes() == copy.read_bytes(), 'Prototype must be an unmodified release copy'
    classes = 0
    with ZipFile(original) as jar:
        assert jar.testzip() is None
        names = jar.namelist()
        assert not any('/gametest/' in name or 'paritychecks' in name for name in names)
        metadata = json.loads(jar.read('fabric.mod.json'))
        assert 'fabric-gametest' not in metadata['entrypoints']
        for name in names:
            if not name.endswith('.class'):
                continue
            data = jar.read(name)
            assert struct.unpack_from('>IHH', data) == (0xcafebabe, 0, 61), name
            assert b'org/junit/' not in data and b'net/minecraft/gametest/' not in data, name
            classes += 1
        if module == 'core':
            assert metadata['version'] == version
            assert metadata['custom'][api_key] == int(properties['foci_api_version'])
            assert not any('simpleradiofoci' in name or 'simpleradiodirectional' in name for name in names)
            assert not any('nether_focus' in name or 'end_focus' in name for name in names)
        elif module == 'foci':
            assert metadata['version'] == properties['foci_version'].strip()
            assert metadata['depends']['simpleradio_repeaters'] == '>=0.7.3'
            assert metadata['custom'][api_key] == int(properties['foci_required_api_version'])
            assert properties['foci_required_api_version'] == properties['foci_api_version']
            assert metadata['entrypoints']['preLaunch'] == ['dev.paul.simpleradiofoci.FociCompatibility']
            assert not any(name.startswith('dev/paul/simpleradiorepeaters/') for name in names)
        else:
            assert metadata['version'] == properties['directional_version'].strip()
            assert 'simpleradio_repeaters' not in metadata['depends']
            assert 'simpleradio_repeaters_foci' not in metadata['depends']
            assert not any(name.startswith('dev/paul/simpleradiorepeaters/') for name in names)
            mixins = json.loads(jar.read('simpleradio_directional.mixins.json'))
            assert 'plugin' not in mixins
            assert not any('Repeaters' in name for name in mixins['mixins'])
    sources = original.with_name(original.stem + '-sources.jar')
    with ZipFile(sources) as jar:
        assert not any('/gametest/' in name for name in jar.namelist())
    reports.append({'module': module, 'passed': True, 'jar': copy.name,
                    'bytes': copy.stat().st_size, 'sha256': hashlib.sha256(copy.read_bytes()).hexdigest(),
                    'classes': classes, 'identical_to_release': True,
                    'addon_test_classes': 0, 'custom_bytecode_rewriting': False})
output = root / 'validation' / version / 'prototype-verification.json'
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(reports, indent=2) + '\n')
print(json.dumps(reports, indent=2))
