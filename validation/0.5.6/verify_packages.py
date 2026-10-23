"""Release-only packaging and ownership checks; unchanged model geometry is not re-tested."""
from pathlib import Path
import hashlib, json, subprocess, sys, zipfile

project = Path(__file__).resolve().parents[2]
subprocess.run([sys.executable, str(project / 'tools/verify_prototypes.py')], check=True)
module = project / 'directional'
prefix = 'simple-radio-directional-antenna-fabric-1.20.1-0.2.7'
prototype = module / 'build/libs' / (prefix + '-prototype.jar')
with zipfile.ZipFile(module / 'build/libs' / (prefix + '.jar')) as original, zipfile.ZipFile(prototype) as runtime:
    removed = {n for n in original.namelist() if n.startswith('dev/paul/simpleradiodirectional/gametest/')}
    assert removed and set(runtime.namelist()) == set(original.namelist()) - removed
    assert len(runtime.namelist()) == len(set(runtime.namelist())) and runtime.testzip() is None
    for name in runtime.namelist():
        if name != 'fabric.mod.json': assert original.read(name) == runtime.read(name), name
    metadata = json.loads(original.read('fabric.mod.json'))
    metadata['entrypoints'].pop('fabric-gametest')
    assert json.loads(runtime.read('fabric.mod.json')) == metadata
    assert 'simpleradio_repeaters' not in metadata['depends']
    assert not any(n.startswith('dev/paul/simpleradiorepeaters/') for n in runtime.namelist())
report = {'passed': True, 'jar': prototype.name, 'bytes': prototype.stat().st_size,
          'sha256': hashlib.sha256(prototype.read_bytes()).hexdigest(),
          'retained_runtime_entries_identical': len(runtime.namelist()) - 1,
          'removed_fixture_classes': len(removed), 'no_obfuscation': True,
          'optional_repeaters_not_bundled_or_required': True}
Path(__file__).with_name('directional-prototype-verification.json').write_text(json.dumps(report, indent=2) + '\n')

ownership = {}
for name, namespace, version, count in [('core', 'simpleradio_repeaters', '0.5.6', 7),
                                       ('foci', 'simpleradio_repeaters_foci', '0.5.6', 1),
                                       ('directional', 'simpleradio_directional', '0.2.7', 1)]:
    jar = next((project / name / 'build/libs').glob('*-' + version + '-prototype.jar'))
    with zipfile.ZipFile(jar) as archive:
        chapters = [n for n in archive.namelist() if '/radio_handbook/chapters/' in n and n.endswith('.json')]
        assert len(chapters) == count and all(n.startswith('assets/' + namespace + '/') for n in chapters)
        assert not any('HandbookPreview' in n or 'handbook_checks' in n or 'paritychecks' in n or '/gametest/' in n for n in archive.namelist())
        language = json.loads(archive.read('assets/' + namespace + '/lang/en_us.json'))
        for path in chapters:
            chapter = json.loads(archive.read(path)); assert chapter['title'] in language
            for entry in chapter['entries']:
                if 'text' in entry: assert entry['text'] in language
                else:
                    owner, recipe = entry['recipe'].split(':')
                    assert 'data/' + owner + '/recipes/' + recipe + '.json' in archive.namelist()
        if name == 'core':
            assert not any('.foci.' in key or '.extenders.' in key or '.directional.' in key for key in language)
        ownership[name] = {'chapters': len(chapters), 'own_namespace': True, 'all_text_and_recipes_present': True}
Path(__file__).with_name('handbook-ownership.json').write_text(json.dumps(ownership, indent=2) + '\n')
print(json.dumps({'directional': report, 'chapter_ownership': ownership}, indent=2))
