#!/usr/bin/env python3
"""Run from the extracted 0.1.1 source project; audit sources remain separate."""
from pathlib import Path
import json
import os
import shutil
import subprocess
import sys
import uuid
import xml.etree.ElementTree as ET
import zipfile

audit = Path(__file__).resolve().parent
project = Path.cwd()
wrapper = project / ('gradlew.bat' if os.name == 'nt' else 'gradlew')
if not wrapper.is_file():
    sys.exit('Run this script with the 0.1.1 source project as the working directory.')
gradle = [str(wrapper)] if os.name == 'nt' else ['bash', str(wrapper)]
overlay = ['-I', str(audit / 'audit.gradle'), '--console=plain']
subprocess.run(gradle + ['compileJava'] + overlay, check=True)
class_path = 'dev/paul/simpleradiorepeaters/audit/NativeHandheldProbe.class'
jar = audit / 'native-handheld-probe.jar'
metadata = {
    'schemaVersion': 1, 'id': 'handheld_native_audit', 'version': '1.0',
    'name': 'Native handheld audit probe', 'environment': 'server',
    'entrypoints': {'fabric-gametest': ['dev.paul.simpleradiorepeaters.audit.NativeHandheldProbe']},
    'depends': {'fabricloader': '>=0.19.0', 'minecraft': '1.20.1', 'fabric-api': '*',
                'simpleradio': '1.20.1-4.5.7.9'},
}
with zipfile.ZipFile(jar, 'w', zipfile.ZIP_DEFLATED) as z:
    z.write(project / 'build/classes/java/main' / class_path, class_path)
    z.writestr('fabric.mod.json', json.dumps(metadata))
copies = []
try:
    for run in ['gametest', 'baseline']:
        mods = project / 'run' / run / 'mods'; mods.mkdir(parents=True, exist_ok=True)
        target = mods / ('handheld-audit-' + uuid.uuid4().hex + '.jar')
        shutil.copy2(jar, target); copies.append(target)
    # --continue runs the disabled-addon control even when the known upstream tests fail.
    subprocess.run(gradle + ['runGameTest', 'runBaseGameTest', '--continue'] + overlay)
    results = audit / 'results'; results.mkdir(exist_ok=True)
    failures = {}
    cases = {}
    for run in ['gametest', 'baseline']:
        report = project / 'run' / run / 'reports/gametest-report.xml'
        shutil.copy2(report, results / (run + '-controls.xml'))
        cases[run] = {c.attrib['name']: c.find('failure') is None for c in ET.parse(report).iter('testcase')}
        failures[run] = {name for name, passed in cases[run].items() if not passed}
    required = [
        'handheldauditgametests.handheld_frames_survive_collection_owner_transfer_and_reload',
        'handheldauditgametests.tuning_one_handheld_does_not_interrupt_another_stream',
        'handheldauditgametests.channel_conversion_returns_to_wired_radio_and_handheld_receivers',
    ]
    probe = 'nativehandheldprobe.quick_bench_retune_leaves_native_registration_on_old_channel'
    good = all(cases['gametest'].get(name, False) for name in required)
    good &= all(cases[run].get(probe, False) for run in ['gametest', 'baseline'])
    known = {'SystemTest.closedSystem', 'SystemTest.garbageCollection', 'SystemTest.openSystem', 'SystemTest.shortCircuit'}
    good &= failures['gametest'] == failures['baseline'] == known
    print('Audit matches the recorded findings.' if good else 'Audit results differ; inspect results/*.xml.')
finally:
    for target in copies:
        target.unlink(missing_ok=True)
    jar.unlink(missing_ok=True)
    subprocess.run(gradle + ['compileJava', 'processResources', '--console=plain'], check=True)
sys.exit(0 if good else 1)
