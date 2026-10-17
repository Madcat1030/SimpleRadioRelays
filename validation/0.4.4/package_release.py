from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import hashlib
import json
import shutil
import xml.etree.ElementTree as ET

ROOT = Path('/workspace/scratch/b97ec851c650')
PROJECT = ROOT / 'radio-card-controls/work/simple-radio-repeaters'
TASK = ROOT / 'radio-insert-visuals'
ANALYSIS = TASK / 'analysis'
VALIDATION = PROJECT / 'validation/0.4.4'
DELIVERY = TASK / 'delivery'
JAR = PROJECT / 'build/libs/simple-radio-repeaters-fabric-1.20.1-0.4.4-prototype.jar'
BASE_SOURCE = ROOT / 'radio-interactions/delivery/Simple-Radio-Repeaters-0.4.3-prototype-source.zip'

def main():
    bytecode = json.loads((ANALYSIS / 'bytecode-verification.json').read_text())
    scope = json.loads((ANALYSIS / 'change-scope.json').read_text())
    world = json.loads((ANALYSIS / 'world-render-result.json').read_text())
    digest = hashlib.sha256(JAR.read_bytes()).hexdigest()
    assert bytecode['passed'] and scope['passed'] and world['passed']
    assert digest == bytecode['sha256'] == world['prototype_sha256']
    assert 'BUILD SUCCESSFUL' in (ANALYSIS / 'release-build.log').read_text()
    suites = [ET.parse(p).getroot() for p in (PROJECT / 'build/test-results/test').glob('TEST-*.xml')]
    tests = sum(int(t.get('tests')) for t in suites)
    assert tests == 51 and all(int(t.get('failures')) == int(t.get('errors')) == 0 for t in suites)
    VALIDATION.mkdir(exist_ok=True)
    DELIVERY.mkdir(exist_ok=True)
    for name in ['verify_prototype.py', 'run_world_preview.py', 'package_release.py', 'release-build.log',
                 'bytecode-verification.json', 'change-scope.json', 'world-render-result.json', 'world-client.log', 'prototype-server.log']:
        shutil.copyfile(ANALYSIS / name, VALIDATION / name)
    shutil.copytree(ANALYSIS / 'world-qa', VALIDATION / 'world-qa',
                    ignore=shutil.ignore_patterns('classes', '*.ready', 'launches.json'), dirs_exist_ok=True)
    shutil.copytree(PROJECT / 'build/test-results/test', VALIDATION / 'unit-tests',
                    ignore=shutil.ignore_patterns('binary'), dirs_exist_ok=True)
    shutil.copyfile(PROJECT / 'build/prototype/packaging-report.json', VALIDATION / 'prototype-packaging.json')
    screenshot = PROJECT / 'run/screenshots/Relay-Sockets-0.4.4-In-World.png'
    shutil.copyfile(screenshot, VALIDATION / screenshot.name)
    summary = {'version': '0.4.4', 'minecraft': '1.20.1', 'loader': 'Fabric', 'simple_radio': '1.20.1-4.5.7.9',
               'build': 'passed', 'unit_tests': {'tests': tests, 'failures': 0, 'errors': 0},
               'rendering': {'sample': 'front neighbor sky/block light', 'catalyst_and_attachment': True,
                             'ordinary_environmental_lighting': True, 'geometry_and_item_selection_unchanged': True},
               'native_client_and_server': world, 'prototype': bytecode, 'change_scope': scope,
               'historical_gametests': {'version': '0.4.3', 'rerun_for_this_patch': False,
                    'addon_tests': 60, 'addon_failures': 0, 'native_tests': 7, 'native_failures': 4,
                    'status': 'Recorded baseline retained; native failure causes unresolved, not established live-server bugs.'},
               'limits': ['No live microphone playback; this is a client rendering patch.']}
    for path in [VALIDATION / 'build-verification.json', PROJECT / 'validation/build-verification.json']:
        path.write_text(json.dumps(summary, indent=2) + '\n')
    notes = '''# Simple Radio Repeaters 0.4.4

Fixes the nearly black catalyst and upgrade inserts on the Relay. Their light now comes from the block immediately in front of the Relay, where the inserts are visible, instead of inside its opaque housing. They still follow normal sky and block lighting.

The correct catalyst was already being selected; the darkness made amethyst difficult to recognize. Catalyst selection, synchronization, models, card rendering and gameplay are unchanged. The runtime JAR differs from 0.4.3 only in `RelayRenderer.class` and the version metadata.

Replace the previous addon JAR on both server and clients. Existing 0.4 worlds and configuration work directly. This prototype targets Fabric Minecraft 1.20.1, Java 17+, and Simple Radio 1.20.1-4.5.7.9.

## Validation

- Build and all 51 unit tests pass.
- A native client joined a production Fabric server running the exact prototype JAR with JVM verification enabled.
- Checked blackstone, amethyst and all three upgrades, including the dark-housing/light-front condition.
- Removed blackstone and installed amethyst through ordinary socket interactions; the correct item and model reached the client.
- Captured the actual in-world production renderer under daylight.
- All 625 retained methods in 72 classes match the full development build byte for byte. The JAR contains no addon test fixtures or temporary visual QA mod, and adds no obfuscation.

The full 0.4.3 GameTest baseline is retained in the source bundle and was not rerun for this rendering-only patch. Its 60 addon tests pass; four previously recorded native headless failures remain unresolved. Live microphone playback was not part of this visual check.
'''
    (VALIDATION / 'Release-Notes.md').write_text(notes)
    prototype_doc = f'''# Simple Radio Repeaters 0.4.4 prototype distribution

`{JAR.name}` is the test-free distribution for Fabric Minecraft 1.20.1 and Simple Radio 1.20.1-4.5.7.9. Replace the previous addon JAR on both server and clients. Existing 0.4 worlds and configuration work directly.

The prototype removes 13 addon GameTest fixture classes, the `fabric-gametest` entrypoints and 13 audited test-only helper methods. Packaging rejects a removal if retained code still calls it. Minecraft overrides, serialization, hooks, record/enum machinery and normal diagnostics remain.

No obfuscator or general optimizer is used. Addon names, source filenames, line numbers and debugging information remain intact. Minecraft references keep Fabric's required intermediary mappings.

The JAR is {bytecode['bytes']:,} bytes. All {bytecode['retained_methods_byte_identical_including_debug']} retained methods in {bytecode['classes_checked']} classes are byte-identical to the full 0.4.4 build, including debug data. Resources and mixin configuration match the full build. Compared with the 0.4.3 prototype, only the Relay renderer and version metadata change.

The build and 51 unit tests pass. A native client joined a production dedicated server running this exact JAR with `-Xverify:all`, checked socket lighting and ordinary blackstone-to-amethyst replacement, captured the in-world renderer, and shut down cleanly. Live microphone playback was not tested. The full GameTest baseline remains under `validation/0.4.3/` (60 addon passes and four unresolved native failures); current rendering and packaging evidence is under `validation/0.4.4/`.

## Rebuild

Use JDK 21 for the build tools; release classes target Java 17.

```sh
./gradlew build prototypeJar
```

Output is under `build/libs/`. `build/prototype/packaging-report.json` records removals and checksums. `tools/PrototypeJar.java` performs the conservative filter using ASM supplied with Gradle; no extra runtime dependency is added. Regular development builds and the source bundle retain tests.

SHA-256: `{digest}`.
'''
    (PROJECT / 'docs/Prototype-Distribution.md').write_text(prototype_doc)
    for source, name in [(JAR, JAR.name), (PROJECT / 'README.md', 'Simple-Radio-Repeaters-Readme.md'),
                         (PROJECT / 'docs/Simple-Radio-Repeaters-Documentation.md', 'Simple-Radio-Repeaters-Documentation.md'),
                         (VALIDATION / 'Release-Notes.md', 'Simple-Radio-Repeaters-0.4.4-Release-Notes.md'),
                         (screenshot, screenshot.name)]:
        shutil.copyfile(source, DELIVERY / name)
    overrides = {'README.md', 'docs/Simple-Radio-Repeaters-Documentation.md', 'docs/Prototype-Distribution.md',
                 'art/Model-Editing-Guide.md', 'gradle.properties', 'build.gradle', 'validation/build-verification.json'}
    for folder in ['src', 'tools', 'validation/0.4.4']:
        overrides.update(p.relative_to(PROJECT).as_posix() for p in (PROJECT / folder).rglob('*') if p.is_file())
    prefix = 'simple-radio-repeaters/'
    source_zip = DELIVERY / 'Simple-Radio-Repeaters-0.4.4-prototype-source.zip'
    with ZipFile(BASE_SOURCE) as old, ZipFile(source_zip, 'w', ZIP_DEFLATED, compresslevel=9) as new:
        for name in sorted(set(old.namelist()) | {prefix + p for p in overrides}):
            relative = name.removeprefix(prefix)
            assert not any(part in {'build', 'run', '.gradle', 'classes', 'library-helpers'} for part in Path(relative).parts)
            path = PROJECT / relative
            if path.is_file():
                new.write(path, name)
            else:
                new.writestr(old.getinfo(name), old.read(name))
    with ZipFile(source_zip) as z:
        assert z.testzip() is None
        assert json.loads(z.read(prefix + 'validation/build-verification.json'))['version'] == '0.4.4'
        for path in (PROJECT / 'src').rglob('*'):
            if path.is_file():
                assert z.read(prefix + path.relative_to(PROJECT).as_posix()) == path.read_bytes()
    print(json.dumps({'files': [{'name': p.name, 'bytes': p.stat().st_size} for p in sorted(DELIVERY.iterdir())], 'sha256': digest}, indent=2))

if __name__ == '__main__':
    main()
