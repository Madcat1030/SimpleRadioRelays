from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import hashlib, json, shutil

ROOT = Path('/workspace/scratch/b97ec851c650')
PROJECT = ROOT / 'radio-card-controls/work/simple-radio-repeaters'
TASK = ROOT / 'radio-interactions'
VALIDATION = PROJECT / 'validation/0.4.3'
DELIVERY = TASK / 'delivery'
JAR = PROJECT / 'build/libs/simple-radio-repeaters-fabric-1.20.1-0.4.3-prototype.jar'
BASE_SOURCE = ROOT / 'radio-optional-features/delivery/Simple-Radio-Repeaters-0.4.2-prototype-source.zip'

def main():
    DELIVERY.mkdir(exist_ok=True)
    bytecode = json.loads((TASK/'analysis/bytecode-verification.json').read_text())
    production = json.loads((TASK/'analysis/production-smoke-result.json').read_text())
    assert bytecode['passed'] and production['passed']
    assert bytecode['sha256'] == hashlib.sha256(JAR.read_bytes()).hexdigest()
    assert 'BUILD SUCCESSFUL' in (TASK/'analysis/release-build.log').read_text()
    for name in ['verify_prototype.py','smoke_prototype.py','bytecode-verification.json',
                 'production-smoke-result.json','prototype-server.log','release-build.log']:
        shutil.copyfile(TASK/'analysis'/name, VALIDATION/name)
    shutil.copyfile(PROJECT/'build/prototype/packaging-report.json', VALIDATION/'prototype-packaging.json')
    summary = {
        'version':'0.4.3', 'minecraft':'1.20.1', 'loader':'Fabric',
        'simple_radio':'1.20.1-4.5.7.9', 'standalone_build':'passed',
        'unit_tests':{'tests':51,'failures':0}, 'addon_gametests':{'tests':60,'failures':0},
        'native_gametests':{'tests':7,'failures':4,
            'names':['shortCircuit','garbageCollection','openSystem','closedSystem'],
            'status':'Same recorded addon-disabled headless failures; cause unresolved, not established live-server bugs.'},
        'feature_controls':{'interdimensionalRelays':True,'netherTweaks':True,'active_only_when_native_cross_dimensional_is_off':True,'independent':True,'commands_saved_immediately':True,'cache_scope_tracks_effective_flags':True},
        'interaction_and_compatibility':{'distance_hooks':'MixinExtras ModifyExpressionValue','unused_dimensional_field_redirect_removed':True,'socket_only_ejection':True,'all_facings_checked':True,'hopper_automation':False,'unsided_container_api':True,'quiet_removal_next_tick_regression':True,'first_transmitter_delivery_order':'Still open; per-transmitter path selection is separate'},
        'bug_fixes':{'transponder_pickaxe_and_stone_tool_tags':True,'translated_outputs_use_nullable_lookup':True,'listener_free_virtual_hops_preserved':True},
        'propagation':{'nether_local_distance_scale':4,'nether_local_noise_floor_addition':10,
            'nether_focused_distance_scale':2,'nether_focused_noise_floor_addition':0,
            'nether_dimensional_modifier_multiplier':2,'nether_dimensional_coordinate_scale':'native, including Y',
            'ordinary_native_endpoints':True,'native_wire_and_receiver_internal_fanout_untouched':True},
        'foci':{'nether_from_overworld':'mapped X/Z, roof Y=128, two-way pocket',
            'nether_from_nether':'local propagation improvement only; no Overworld projection',
            'end_from_overworld':'mapped X/Z, bottom Y=0',
            'end_from_end':'mapped Overworld X/Z, own clamped Y',
            'end_from_nether':'no projection','end_radius_cap':None,'projected_to_projected_links':False,
            'native_cross_dimensional_mode':'native rules exactly; projections, local Focus benefits and all Nether tweaks disabled'},
        'amplifier':{'display_name':'Amplification Focus','antenna_multiplier':1.5,
            'affects_transmit_and_receive':True,'native_radio_base_multiplied':False,
            'saved_score_multiplied':False,'decay_reset':1,'total_visit_limit_preserved':True,'audio_repaired':False},
        'prototype':bytecode,'production_smoke':production,
        'limits':['Synthetic encoded packets; no live multiplayer microphone playback.',
            'No new client render capture; geometry unchanged, effective ANT label updated.',
            'Four native headless test failures remain unresolved.']}
    for path in [VALIDATION/'build-verification.json', PROJECT/'validation/build-verification.json']:
        path.write_text(json.dumps(summary,indent=2)+'\n')
    shutil.copyfile(Path(__file__), VALIDATION/'package_release.py')
    shutil.copyfile(JAR, DELIVERY/JAR.name)
    shutil.copyfile(PROJECT/'README.md', DELIVERY/'Simple-Radio-Repeaters-Readme.md')
    shutil.copyfile(PROJECT/'docs/Simple-Radio-Repeaters-Documentation.md', DELIVERY/'Simple-Radio-Repeaters-Documentation.md')
    shutil.copyfile(VALIDATION/'Release-Notes.md', DELIVERY/'Simple-Radio-Repeaters-0.4.3-Release-Notes.md')
    overrides = set()
    for folder in ['src','tools','validation/0.4.3']:
        overrides.update(p.relative_to(PROJECT).as_posix() for p in (PROJECT/folder).rglob('*') if p.is_file())
    overrides.update(['README.md','docs/Simple-Radio-Repeaters-Documentation.md','docs/Prototype-Distribution.md',
        'gradle.properties','build.gradle','art/make_expansion_models.py','validation/build-verification.json'])
    source = DELIVERY/'Simple-Radio-Repeaters-0.4.3-prototype-source.zip'
    prefix = 'simple-radio-repeaters/'
    with ZipFile(BASE_SOURCE) as old, ZipFile(source,'w',ZIP_DEFLATED,compresslevel=9) as new:
        oldnames = set(old.namelist())
        for name in sorted(oldnames | {prefix+p for p in overrides}):
            relative = name.removeprefix(prefix)
            path = PROJECT/relative
            assert not any(x in {'build','run','.gradle','library-helpers','classes'} for x in Path(relative).parts)
            if path.is_file(): new.write(path,name)
            else: new.writestr(old.getinfo(name),old.read(name))
    with ZipFile(source) as z:
        assert z.testzip() is None
        assert json.loads(z.read(prefix+'validation/build-verification.json'))['version']=='0.4.3'
        for path in (PROJECT/'src').rglob('*'):
            if path.is_file(): assert z.read(prefix+path.relative_to(PROJECT).as_posix())==path.read_bytes()
    with ZipFile(DELIVERY/JAR.name) as z:
        assert 'fabric-gametest' not in json.loads(z.read('fabric.mod.json'))['entrypoints']
        assert not any('/gametest/' in name for name in z.namelist())
        lang=json.loads(z.read('assets/simpleradio_repeaters/lang/en_us.json'))
        assert lang['item.simpleradio_repeaters.relay_amplifier']=='Amplification Focus'
    print(json.dumps({'files':[{'name':p.name,'bytes':p.stat().st_size} for p in sorted(DELIVERY.iterdir())],
                      'sha256':bytecode['sha256'],'unit_tests':51,'addon_gametests':60},indent=2))

if __name__ == '__main__': main()
