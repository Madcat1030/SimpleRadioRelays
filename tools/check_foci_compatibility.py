"""Run real Fabric pre-launch checks with one unchanged Foci build and altered core metadata."""
from pathlib import Path
import json
import os
import shlex
import subprocess

root = Path(__file__).resolve().parents[1]
properties = dict(line.split('=', 1) for line in (root / 'gradle.properties').read_text().splitlines()
                  if '=' in line and not line.startswith('#'))
foci_version = properties['foci_version'].strip()
api = int(properties['foci_required_api_version'])
out = root / 'validation' / properties['mod_version'].strip()
out.mkdir(parents=True, exist_ok=True)
runner = shlex.split(os.environ.get('RADIO_GRADLE_COMMAND', './gradlew'))
artifacts = [next((root / module / 'build/devlibs').glob('*-dev.jar')) for module in ('core', 'foci')]
originals = {path: path.read_bytes() for path in artifacts}
cases = {
    'same_api': f'Foci API {api} accepted: Repeaters 0.99.0, Dimensional Foci {foci_version}',
    'different_api': f'requires Foci API {api}, but Repeaters 0.99.0 provides API {api + 1}',
    'missing_api': 'Simple Radio Repeaters 0.99.0 has no valid Foci API marker',
}
results = []
for case, expected in cases.items():
    log = out / ('compatibility-' + case + '.log')
    command = runner + [':directional:runGameTest', '-PwithFoci', '-I', str(root / 'tools/foci-compatibility.gradle'),
                        '-PcompatibilityCase=' + case]
    try:
        with log.open('w') as output:
            process = subprocess.run(command, cwd=root, stdout=output, stderr=subprocess.STDOUT)
        text = log.read_text()
        # All probes deliberately stop at the QA sentinel, before Minecraft starts a world.
        assert process.returncode != 0, (case, 'QA sentinel did not stop the launch')
        assert 'FOCI_COMPATIBILITY_PROBE_COMPLETE' in text, (case, 'preLaunch list was not reached')
        assert expected in text, (case, 'expected compatibility decision missing', log)
        assert 'Starting test server' not in text, (case, 'game initialized before the check')
        if case == 'same_api':
            assert 'requires Foci API' not in text and 'no valid Foci API marker' not in text
        else:
            assert f'Foci API {api} accepted:' not in text
        assert all(path.read_bytes() == data for path, data in originals.items()), 'Gradle did not restore metadata'
        results.append({'case': case, 'passed': True, 'core_version': '0.99.0', 'foci_version': foci_version,
                        'decision': 'accepted' if case == 'same_api' else 'rejected', 'world_started': False,
                        'intentional_qa_abort': True, 'metadata_restored': True})
        print(case + ': passed', flush=True)
    finally:
        for path, data in originals.items():
            path.write_bytes(data)
(out / 'compatibility-smoke.json').write_text(json.dumps(results, indent=2) + '\n')
