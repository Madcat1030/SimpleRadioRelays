from __future__ import annotations
import copy
import hashlib
import json
import shutil
import struct
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED

ROOT = Path('/workspace/scratch/b97ec851c650')
PROJECT = ROOT / 'radio-card-controls/work/simple-radio-repeaters'
TASK = ROOT / 'radio-dimension-transponder'
DELIVERY = TASK / 'delivery'
VALIDATION = PROJECT / 'validation/0.4.0'
BASELINE = ROOT / 'radio-catalyst-hardening/delivery/Simple-Radio-Repeaters-0.3.2-source.zip'
JAR_NAME = 'simple-radio-repeaters-fabric-1.20.1-0.4.0.jar'


def write_json(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n')


def write_xml(path, root):
    ET.indent(root)
    ET.ElementTree(root).write(path, encoding='utf-8', xml_declaration=True)


def suite(name, cases):
    result = ET.Element('testsuite', name=name, tests=str(len(cases)),
                        failures=str(sum(c.find('failure') is not None for c in cases)),
                        errors=str(sum(c.find('error') is not None for c in cases)))
    for case in cases:
        result.append(copy.deepcopy(case))
    return result


def main():
    assert 'BUILD SUCCESSFUL' in (TASK / 'analysis/release-build.log').read_text()
    assert 'BUILD SUCCESSFUL' in (TASK / 'analysis/client-preview.log').read_text()
    assert 'EXPANSION_QA native expansion rendered' in (TASK / 'analysis/client-preview.log').read_text()
    DELIVERY.mkdir(parents=True, exist_ok=True); VALIDATION.mkdir(parents=True, exist_ok=True)
    jar = PROJECT / 'build/libs' / JAR_NAME
    with ZipFile(jar) as z:
        assert z.testzip() is None
        assert json.loads(z.read('fabric.mod.json'))['version'] == '0.4.0'
        assert {struct.unpack('>H', z.read(n)[6:8])[0] for n in z.namelist() if n.endswith('.class')} == {61}
        for name in ['radio/RadioSpace', 'network/RelayPort', 'block/TransponderBlock', 'block/TransponderBlockEntity',
                     'network/TransponderChannels', 'client/TransponderRenderer', 'item/RelayAttachmentItem']:
            assert 'dev/paul/simpleradiorepeaters/' + name + '.class' in z.namelist()
        assert not any('Preview' in n or 'lifecycle-qa' in n or 'CatalogLifecycleClient' in n for n in z.namelist())
        for name in ['nether_focus', 'end_focus', 'relay_amplifier', 'transponder']:
            assert 'data/simpleradio_repeaters/recipes/' + name + '.json' in z.namelist()
    digest = hashlib.sha256(jar.read_bytes()).hexdigest()
    shutil.copyfile(jar, DELIVERY / JAR_NAME)
    units = ET.Element('testsuites'); unit_count = failures = 0
    for path in sorted((PROJECT / 'build/test-results/test').glob('TEST-*.xml')):
        r = ET.parse(path).getroot(); units.append(r)
        unit_count += int(r.get('tests', '0'))
        failures += int(r.get('failures', '0')) + int(r.get('errors', '0'))
    assert (unit_count, failures) == (48, 0)
    write_xml(VALIDATION / 'verification-unit-tests.xml', units)
    report = PROJECT / 'run/gametest/reports/gametest-report.xml'
    assert report.stat().st_mtime >= max(p.stat().st_mtime for p in (PROJECT / 'src/main/java').rglob('*.java'))
    cases = list(ET.parse(report).getroot().iter('testcase'))
    addon = [c for c in cases if not c.get('name', '').startswith('SystemTest.')]
    native = [c for c in cases if c.get('name', '').startswith('SystemTest.')]
    assert len(addon) == 50 and all(c.find('failure') is None and c.find('error') is None for c in addon)
    native_failures = [c.get('name') for c in native if c.find('failure') is not None or c.find('error') is not None]
    assert len(native) == 7 and set(native_failures) == {'SystemTest.shortCircuit', 'SystemTest.garbageCollection', 'SystemTest.openSystem', 'SystemTest.closedSystem'}
    shutil.copyfile(report, VALIDATION / 'verification-gametest-combined.xml')
    write_xml(VALIDATION / 'verification-gametest-addon.xml', suite('Simple Radio Repeaters 0.4.0', addon))
    write_xml(VALIDATION / 'verification-gametest-native.xml', suite('Native Simple Radio', native))
    for name in ['release-build.log', 'build-gametest-040-final.log', 'client-preview.log', 'run_preview.py']:
        shutil.copyfile(TASK / 'analysis' / name, VALIDATION / name)
    for directory in ['java', 'resources']:
        shutil.copytree(TASK / 'analysis/qa' / directory, VALIDATION / 'qa' / directory, dirs_exist_ok=True)
    shutil.copyfile(TASK / 'analysis/qa/client-preview.gradle', VALIDATION / 'qa/client-preview.gradle')
    image = PROJECT / 'art/Relay-Expansion-0.4.0.png'
    assert image.stat().st_size > 50000
    shutil.copyfile(image, VALIDATION / image.name); shutil.copyfile(image, DELIVERY / image.name)

    with ZipFile(BASELINE) as z:
        names = z.namelist()
        bank = [n for n in names if '/models/block/channel_bank' in n or '/models/block/bank_card' in n
                or '/textures/block/' in n or n.endswith('/client/ChannelBankRenderer.java')]
        assert all((PROJECT / n.removeprefix('simple-radio-repeaters/')).read_bytes() == z.read(n) for n in bank)
        paths = {Path(n.removeprefix('simple-radio-repeaters/')) for n in names if not n.endswith('/')}

    verification = {
        'version': '0.4.0', 'minecraft': '1.20.1', 'simple_radio': '1.20.1-4.5.7.9',
        'verified_at_utc': datetime.now(timezone.utc).isoformat(), 'java_bytecode': 17, 'jar_sha256': digest,
        'build': 'passed', 'build_command': '--offline build', 'minecraft_test_command': '--offline build runGameTest',
        'combined_minecraft_task_exit': 1,
        'unit_tests': {'status': 'passed', 'tests': 48, 'failures': 0},
        'addon_minecraft_tests': {'status': 'passed', 'tests': 50, 'failures': 0},
        'native_minecraft_tests': {'tests': 7, 'failures': native_failures,
            'note': 'Same four headless test names fail in the earlier addon-disabled baseline. Cause unresolved; not established live-server bugs.'},
        'dimensions': {
            'native_mode': 'Follow frequency.crossDimensional automatically; physical sites, all native XYZ scaling, native dimensional attenuation, foci ignored.',
            'focus_mode': 'Only a focus adds a foreign-world site while native cross-dimensional radio is disabled.',
            'mapping': 'home X/Z * home coordinate scale / target coordinate scale',
            'nether_y': 'minimum build height + logical height (default 128)',
            'end_y': 'minimum build height (default 0)', 'end_radius_cap': None,
            'attenuation': 'Native base-loss reach gate, then travel with dimensionalInterference (halved for AM).',
            'pocket_to_pocket': False, 'physical_target_hub': True,
            'native_speaker': 'Focus output localizes carrier origin only; ownership, bytes, activity, worst noise and both counts retained.',
            'chunk_force_loading': False,
        },
        'power': {
            'formula': 'current tower nominal power * current catalyst strength * 0.9 ^ (decay count - 1)',
            'ordinary': 'Increment total visits and decay count.', 'amplifier': 'Increment total visits, reset decay to 1.',
            'budget': 'Configured maximum total visits also applies across amplifiers, wires and conversions.',
            'audio': 'Retain worst interference; amplifier cannot repair audio.',
            'catalyst': 'Always required; native registered item with finite positive strength.',
            'antenna': 'Verified positive native score on supported core; native receiver base gain retained.',
            'cutoff': 'No artificial percentage floor.',
        },
        'transponder': {
            'mount': 'Directly atop Relay, mutually exclusive with six-card Bank.', 'slots': 3,
            'required': 'Tuned left plus at least one tuned right.',
            'simplex': 'Left to both selected right channels.', 'duplex': 'Left to rights; either right to left.',
            'same_channel_both_sides': True, 'am_fm_conversion': True, 'right_to_right_internal_port': False,
            'mode_switch': 'Empty-hand front center display; card boundary excluded.',
            'persistent': 'Ordered card policy and mode update unloaded Relay records and survive pending entity restoration.',
        },
        'routing': {
            'nodes': 'Directed channel input/output ports over immutable tower snapshots.',
            'dominance': 'Same port; no greater total visits, no greater decay, no worse noise.',
            'amplifier_detours': 'Retain higher-total routes when lower decay makes them useful.',
            'route_cache_key': 'Entry port, previous total, previous decay, pruning mode; scoped by network revision and native dimensional mode.',
            'route_cache_limits': {'entries': 128, 'numeric_elements': 32768},
            'link_cache_limits': {'rows': 512, 'edges': 65536},
            'live_inputs': 'Packet ingress noise and receiver position/reception are not cached.',
            'source_copies': 'Materialize selected ordinary delivery only; independent copies for custom callbacks.',
            'fingerprint_packet_path': False, 'configuration_fallback_ticks': 100,
        },
        'regressions': {
            'ordinary_randomized_graphs': 2000, 'amplified_randomized_graphs': 1000, 'changed_ingress_packets_each': 2,
            'native_end_beyond_500_blocks': True, 'native_focus_return': True, 'native_speaker_origin_gate': True,
            'native_hub_required': True, 'native_am_fm_simplex_duplex_conversion': True,
            'amplifier_budget_and_audio': True, 'all_facing_card_and_socket_controls': True,
            'unloaded_relay_module_edits_and_restore': True, 'native_recipe_ingredients': True,
            'real_copper_wire_count_and_channel_bridge': True,
            'fresh_gametest_world': True,
            'fixture_note': 'Native speaker fixture removes itself from both its container and global map, avoiding stale validation of a reused router ID.',
        },
        'appearance': {'native_render_capture': image.name, 'temporary_qa_absent_from_release': True,
                       'existing_bank_assets_and_renderer_unchanged': True, 'texture_bitmaps_unchanged': True,
                       'attachment_item_and_transponder_models': 'Code-native meshes using existing 16px palette and native Minecraft textures.'},
        'persistence': {'store': 'simpleradio_repeaters_relays_v3', 'schema': 3,
                        'new_optional_fields': ['Attachment', 'Transponder'], 'migration_layer': False},
        'limits': ['Synthetic encoded packets, not actual multiplayer microphone playback.',
                   'Native rendering checked in standalone Minecraft QA screen, not a joined 0.4 multiplayer session.',
                   'Historical 0.3.2 catalog lifecycle and 0.3.1 graph benchmark retained under their original versions; neither is a new 0.4 measurement.',
                   'Four native headless GameTest failures remain; cause unresolved.'],
    }
    write_json(VALIDATION / 'build-verification.json', verification)
    write_json(PROJECT / 'validation/build-verification.json', verification)
    notes = '''# Simple Radio Repeaters 0.4.0

The Relay gains an attachment socket left of the catalyst. Install a Nether Focus, End Focus or Relay Amplifier; the catalyst and a supported antenna remain mandatory.

Foci add mapped transmit/receive pockets only while Simple Radio's native cross-dimensional radio is disabled. Nether injection is at roof Y, End injection at bottom Y. The End has no 500-block cap: native dimensional attenuation decides reach. Two projected pockets need a physical target-world relay to communicate through that world. With native cross-dimensional radio enabled, foci are ignored and physical relays use the mod's normal XYZ coordinate scaling and attenuation.

Amplifiers restore the local catalyst-scaled departure ceiling by resetting decay count to 1. Total visits still increment, and inherited audio degradation remains. The search tracks both counts, retains useful amplifier detours and caches exact numeric alternatives.

A three-card Radio Transponder replaces the Bank atop a Relay. Simplex converts the left input to selected right outputs; duplex also converts either right input back to the left. A tuned pair is required. AM/FM conversion, main-channel branching and later relay hops are supported. Front card controls and a center direction switch match the physical model. The existing six-card Bank artwork and behavior remain unchanged.

New recipes use the requested focus ingredients, Ancient Debris for netherite ore, and native receiving/transmitting modules in the Transponder. Neither crafting a Transponder with a catalyst block nor installing an attachment fills the Relay's separate catalyst socket.

Standalone build: passed. Unit tests: 48/48. Addon Minecraft tests: 50/50. Seven native tests also run; the same four names fail as in the earlier addon-disabled headless control. Their cause is unresolved. Native model captures and separated reports are included. Tests use synthetic encoded bytes; actual multiplayer voice playback was not exercised.

The saved network remains schema 3 with new optional attachment/policy fields. Existing 0.3 towers do not reset. The temporary preview mod is excluded from the release JAR.
'''
    (VALIDATION / 'Release-Notes.md').write_text(notes)
    shutil.copyfile(PROJECT / 'README.md', DELIVERY / 'Simple-Radio-Repeaters-Readme.md')
    shutil.copyfile(PROJECT / 'docs/Simple-Radio-Repeaters-Documentation.md', DELIVERY / 'Simple-Radio-Repeaters-Documentation.md')
    shutil.copyfile(VALIDATION / 'Release-Notes.md', DELIVERY / 'Simple-Radio-Repeaters-0.4.0-Release-Notes.md')
    shutil.copyfile(Path(__file__), VALIDATION / 'package_release.py')
    paths.update(p.relative_to(PROJECT) for p in (PROJECT / 'src').rglob('*') if p.is_file())
    paths.update(p.relative_to(PROJECT) for p in VALIDATION.rglob('*') if p.is_file())
    paths.update({Path('README.md'),Path('docs/Simple-Radio-Repeaters-Documentation.md'), Path('art/Model-Editing-Guide.md'),
                  Path('art/make_expansion_models.py'),Path('art/Relay-Expansion-0.4.0.png'),Path('validation/build-verification.json')})
    output = DELIVERY / 'Simple-Radio-Repeaters-0.4.0-source.zip'
    with ZipFile(output, 'w', ZIP_DEFLATED, compresslevel=9) as z:
        for relative in sorted(paths):
            path = PROJECT / relative
            if path.is_file():
                assert not any(part in {'build','run','.gradle','.library-helpers','classes'} for part in relative.parts)
                z.write(path, 'simple-radio-repeaters/' + relative.as_posix())
    with ZipFile(output) as z:
        assert z.testzip() is None
        assert json.loads(z.read('simple-radio-repeaters/validation/build-verification.json'))['version'] == '0.4.0'
        assert 'mod_version=0.4.0' in z.read('simple-radio-repeaters/gradle.properties').decode()
        for r in (PROJECT / 'src').rglob('*'):
            if r.is_file(): assert z.read('simple-radio-repeaters/' + r.relative_to(PROJECT).as_posix()) == r.read_bytes()
        entries = len(z.namelist())
    print(json.dumps({'files': [{'name': p.name, 'bytes': p.stat().st_size} for p in sorted(DELIVERY.iterdir())],
                      'source_entries': entries, 'jar_sha256': digest, 'unit_tests': unit_count, 'addon_minecraft_tests': len(addon)}, indent=2))

if __name__ == '__main__':
    main()
