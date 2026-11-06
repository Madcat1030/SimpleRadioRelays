"""Import verified release snapshots into a new Git repository; never alter an existing repo.

python reconstruct_history.py --archives PATH --catalog PATH --output NEW_DIRECTORY
An optional --at timestamp reproduces this import's commit/tag dates.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
from archive_tools import POLICY_VERSION, read_archive, versions, module_hashes, snapshot_label

IDENTITY = {'GIT_AUTHOR_NAME': 'Release snapshot import',
            'GIT_AUTHOR_EMAIL': 'release-history@simple-radio-addons.invalid',
            'GIT_COMMITTER_NAME': 'Release snapshot import',
            'GIT_COMMITTER_EMAIL': 'release-history@simple-radio-addons.invalid'}
AUDIT_NAME = 'Simple-Radio-Repeaters-0.1.1-handheld-audit.zip'

def run(repo, *args, data=None, env=None):
    result = subprocess.run(['git', '-c', 'commit.gpgSign=false', '-c', 'tag.gpgSign=false', *args],
        cwd=repo, input=data, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        env={**os.environ, **(env or {})})
    if result.returncode:
        raise RuntimeError('Git operation failed: ' + ' '.join(args[:3]) + '\n' + result.stderr.decode())
    return result.stdout

def tree_map(repo, ref):
    result = {}
    for record in run(repo, 'ls-tree', '-rz', '--full-tree', ref).split(b'\0'):
        if not record:
            continue
        meta, path = record.split(b'\t', 1)
        mode, kind, oid = meta.decode().split()
        if kind != 'blob':
            raise ValueError('Unexpected non-blob source entry')
        result[path.decode()] = (mode, oid)
    return result

def check_tree(repo, ref, files):
    expected = {path: (value['mode'], value['oid']) for path, value in files.items()}
    actual = tree_map(repo, ref)
    if actual != expected:
        mismatch = sorted(set(actual) ^ set(expected))
        mismatch += [p for p in actual.keys() & expected.keys() if actual[p] != expected[p]]
        raise ValueError(f'Archive/tree mismatch at {ref}: {mismatch[:10]}')

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--archives', required=True, type=Path)
    parser.add_argument('--catalog', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--at')
    args = parser.parse_args()
    repo = args.output.resolve()
    if repo.exists():
        raise ValueError('Output already exists. Choose a NEW directory; existing work is never replaced.')
    now = datetime.fromisoformat(args.at.replace('Z', '+00:00')) if args.at else datetime.now(timezone.utc).replace(microsecond=0)
    reconstructed = now.isoformat().replace('+00:00', 'Z')
    common_env = {**IDENTITY, 'GIT_COMMITTER_DATE': str(int(now.timestamp())) + ' +0000'}
    catalog = json.loads(args.catalog.read_text())
    sources = [r for r in catalog if r['name'].lower().endswith('source.zip') and (args.archives/r['name']).is_file()]
    sources.sort(key=lambda r: (r['created_at'], r['name']))
    if not sources:
        raise ValueError('No source archives matched the catalog')
    repo.mkdir(parents=True)
    run(repo, 'init', '--initial-branch=main')
    run(repo, 'config', 'core.autocrlf', 'false')
    run(repo, 'config', 'core.filemode', 'true')
    previous = {}
    previous_versions, previous_modules = {}, {}
    seen_tags, records, same_version_changes, selected_catalog = set(), [], [], []

    def annotated_tag(name, commit, message):
        if name in seen_tags:
            return False
        run(repo, 'tag', '-a', name, commit, '-F', '-', data=message.encode(), env=common_env)
        seen_tags.add(name)
        return True

    def source_record(source):
        item = {key: source.get(key) for key in ['name', 'library_file_id', 'file_id', 'version_id',
                'created_at', 'modified_at', 'size_bytes']}
        item['sha256'] = hashlib.sha256((args.archives/source['name']).read_bytes()).hexdigest()
        if source.get('sha256') and item['sha256'] != source['sha256']:
            raise ValueError('Source archive checksum changed: ' + source['name'])
        return item

    for number, source in enumerate(sources, 1):
        provenance = source_record(source)
        files, omitted, root = read_archive(args.archives/source['name'])
        declared = versions(files)
        module_digests = module_hashes(files)
        kind, label, snapshot_tag = snapshot_label(source['name'])
        for rel in previous.keys() - files.keys():
            (repo/rel).unlink()
        for rel, value in files.items():
            if previous.get(rel) == (value['mode'], value['oid']):
                continue
            path = repo/rel
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(value['content'])
            path.chmod(0o755 if value['mode'] == '100755' else 0o644)
        for path in sorted(repo.rglob('*'), key=lambda p: len(p.parts), reverse=True):
            if '.git' not in path.relative_to(repo).parts and path.is_dir() and not any(path.iterdir()):
                path.rmdir()
        author_date = datetime.fromisoformat(source['created_at'].replace('Z', '+00:00'))
        env = {**common_env, 'GIT_AUTHOR_DATE': str(int(author_date.timestamp())) + ' +0000'}
        title = {'repeaters': 'Repeaters', 'directional': 'Directional Antenna', 'suite': 'Addon suite'}[kind]
        body = (f'Import {title} {label} source snapshot\n\n'
                'Reconstructed release snapshot, not an original development commit.\n'
                f'Original-Archive: {source["name"]}\n'
                f'Archive-SHA256: {provenance["sha256"]}\n'
                f'Archive-Record-Date: {source["created_at"]}\n'
                f'Source-Library-ID: {source["library_file_id"]}\n'
                'File-Policy: strip archive wrapper; omit generated logs, caches and output only\n'
                f'Declared-Versions: {json.dumps(declared, sort_keys=True)}\n')
        run(repo, 'add', '--all', '--force', '--', '.')
        run(repo, 'commit', '--allow-empty', '-F', '-', data=body.encode(), env=env)
        commit = run(repo, 'rev-parse', 'HEAD').decode().strip()
        check_tree(repo, commit, files)
        tags = []
        for tag in [snapshot_tag] + [module + '/v' + version for module, version in declared.items()]:
            if annotated_tag(tag, commit, body):
                tags.append(tag)
        if 'prototype' in label:
            tag = 'repeaters/v' + label
            if annotated_tag(tag, commit, body):
                tags.append(tag)
        if kind == 'suite':
            tag = 'suite/v' + label.replace('-handbook', '')
            if annotated_tag(tag, commit, body):
                tags.append(tag)
        if label == '0.9r1':
            for tag in ['v0.9r1', 'suite/v0.9.0-rc.1'] + [m + '/v0.9r1' for m in declared]:
                if annotated_tag(tag, commit, body):
                    tags.append(tag)
        for module, digest in module_digests.items():
            if previous_versions.get(module) == declared.get(module) and previous_modules.get(module) != digest:
                same_version_changes.append({'snapshot': snapshot_tag, 'module': module, 'version': declared[module],
                                             'previous_snapshot': records[-1]['snapshot_tag']})
        record = {'sequence': number, 'archive': provenance, 'original_root': root,
                  'versions': declared, 'commit': commit,
                  'tree': run(repo, 'rev-parse', 'HEAD^{tree}').decode().strip(),
                  'snapshot_tag': snapshot_tag, 'tags_created': tags,
                  'included_files': len(files), 'included_bytes': sum(len(v['content']) for v in files.values()),
                  'excluded_files': omitted, 'module_source_sha256': module_digests}
        records.append(record)
        selected_catalog.append(provenance)
        previous = {p: (v['mode'], v['oid']) for p, v in files.items()}
        previous_versions, previous_modules = declared, module_digests
        print(f'{number}/{len(sources)} {snapshot_tag} {commit[:12]} ({len(files)} files)', flush=True)

    audit_source = next((r for r in catalog if r['name'] == AUDIT_NAME), None)
    audit = None
    if audit_source and (args.archives/AUDIT_NAME).is_file():
        provenance = source_record(audit_source)
        audit_files, omitted, root = read_archive(args.archives/AUDIT_NAME)
        for value in audit_files.values():
            oid = run(repo, 'hash-object', '-w', '--stdin', data=value['content']).decode().strip()
            assert oid == value['oid']
        def subtree(items):
            direct, children = [], {}
            for name, value in items.items():
                if '/' in name:
                    head, tail = name.split('/', 1)
                    children.setdefault(head, {})[tail] = value
                else:
                    direct.append(f'{value["mode"]} blob {value["oid"]}\t{name}\0'.encode())
            for name, child in sorted(children.items()):
                direct.append(f'040000 tree {subtree(child)}\t{name}\0'.encode())
            return run(repo, 'mktree', '-z', data=b''.join(direct)).decode().strip()
        tree = subtree(audit_files)
        date = datetime.fromisoformat(audit_source['created_at'].replace('Z', '+00:00'))
        message = ('Archive partial 0.1.1 handheld audit\n\n'
            'This independent branch is an audit/test supplement, NOT the complete 0.1.1 release.\n'
            f'Original-Archive: {AUDIT_NAME}\nArchive-SHA256: {provenance["sha256"]}\n')
        commit = run(repo, 'commit-tree', tree, data=message.encode(),
                     env={**common_env, 'GIT_AUTHOR_DATE': str(int(date.timestamp())) + ' +0000'}).decode().strip()
        branch = 'archive/handheld-audit-0.1.1'
        run(repo, 'update-ref', 'refs/heads/' + branch, commit)
        annotated_tag('audit/v0.1.1-handheld', commit, message)
        check_tree(repo, commit, audit_files)
        audit = {'archive': provenance, 'branch': branch, 'tag': 'audit/v0.1.1-handheld',
                 'commit': commit, 'tree': tree, 'included_files': len(audit_files),
                 'original_root': root, 'excluded_files': omitted, 'complete_release': False}
        selected_catalog.append(provenance)

    manifest = {'schema_version': 1, 'policy_version': POLICY_VERSION, 'reconstructed_at': reconstructed,
                'snapshot_count': len(records), 'snapshots': records, 'partial_audit': audit,
                'same_version_source_changes': same_version_changes,
                'verification': {'all_archive_members_crc_checked': True, 'all_imported_file_bytes_and_modes_matched': True,
                                 'gameplay_or_build_tests_run': False}}
    history = repo/'history'
    history.mkdir()
    (history/'snapshots.json').write_text(json.dumps(manifest, indent=2) + '\n')
    (history/'source-catalog.json').write_text(json.dumps(selected_catalog, indent=2) + '\n')
    (history/'GAPS.md').write_text('''# Scope and gaps

This is reconstructed release history. The source archives contained no original Git objects, original commit identities or intermediate edits. Each mainline historical commit represents one recovered full-project source snapshot, ordered by its saved archive record date. Author dates are those archive record dates, not claimed original edit times. Committer/tag dates identify this reconstruction. No original authorship is invented.

The oldest complete source is Repeaters 0.1.2. Complete 0.1.0 and 0.1.1 source was not recovered. The separate `archive/handheld-audit-0.1.1` branch and `audit/v0.1.1-handheld` tag preserve only a seven-file audit/test supplement; they cannot substitute for a full 0.1.1 release. No full-release tag is fabricated for these missing versions.

0.4.0 and its later prototype package are both preserved. They declare the same mod version but have different source/test packaging. Module version tags identify the first recovered snapshot introducing a declared version; snapshot tags always identify exact archived project states. Later suite snapshots also record the modules' separately pinned versions. The manifest explicitly records any source changes without a version bump.

Repeaters first used a single-project `src/` layout; the real 0.5.0 split moved it to `core/src/` and added `foci/`. Directional source snapshots subsequently added `directional/`. Only the enclosing ZIP folder is removed. Java, resources, recipes, tests, model sources, build scripts and documentation retain their archived bytes and paths. `git log --follow` can follow the genuine module move.

Generated logs, bytecode, runtime/cache directories and output/dependency JARs are omitted. The Gradle wrapper JAR is retained. Native captures, validation XML/JSON, asset generators and Blockbench projects are retained. Every omission is listed per snapshot in `snapshots.json`; all archive members were read and CRC checked. Archived test reports are historical evidence, not tests rerun during this reconstruction.

The final mainline commit adds Git housekeeping and reconstruction documentation/tools. It does not modify mod behavior. The latest recovered mod release is 0.9r1 (declared build version `0.9.0-rc.1`); the separate review editor's 0.9r2 target is not a recovered mod release or implemented review.
''')
    lines = ['# Simple Radio Addons: recovered Git history', '',
        f'{len(records)} verified full-project source snapshots, from Repeaters 0.1.2 through suite 0.9r1.', '',
        'The `main` branch contains the chronological source snapshots followed by a housekeeping commit. '
        'The latest working tree contains Repeaters, Dimensional Foci and Directional Antenna. '
        'The earlier audit supplement is on its own independent archive branch.', '',
        '```sh', 'git log --oneline --decorate main', 'git tag --list',
        'git diff repeaters/v0.7.5 v0.9r1 -- core/src',
        'git switch --detach v0.9r1', 'git switch main',
        'python3 tools/history/verify_history.py', '```', '',
        'See [scope and gaps](history/GAPS.md) for reconstruction limits and exact filtering. '
        'All tags are annotated with the original archive filename and SHA-256. '
        'Per-snapshot commits, versions, hashes and omissions are in [the manifest](history/snapshots.json). '
        'Project build instructions remain in the original [README](README.md) and [development guide](docs/Development.md).', '',
        '## Snapshot index', '',
        '| Saved date (UTC) | Snapshot tag | Repeaters | Foci | Directional | Commit |',
        '| --- | --- | --- | --- | --- | --- |']
    for r in records:
        v = r['versions']
        lines.append(f'| {r["archive"]["created_at"][:16].replace("T", " ")} | `{r["snapshot_tag"]}` | {v["repeaters"]} | {v.get("foci", "—")} | {v.get("directional", "—")} | `{r["commit"][:12]}` |')
    lines += ['', '## Rebuild the import', '',
        'Keep the original ZIPs in one directory and pass the included source catalog. The output must be a new directory. '
        'The timestamp below reproduces this reconstruction\'s dates; no archived project code is executed.', '',
        '```sh', 'python3 tools/history/reconstruct_history.py \\',
        '  --archives /path/to/original-zips \\', '  --catalog history/source-catalog.json \\',
        f'  --at {reconstructed} \\', '  --output /path/to/new-repository', '```', '']
    (repo/'HISTORY.md').write_text('\n'.join(lines))
    (repo/'.gitignore').write_text('''# Local output; archived validation evidence is intentionally tracked.
**/.gradle/
**/build/
**/run/
**/run-client/
**/run-server/
**/logs/
**/crash-reports/
**/__pycache__/
**/node_modules/
**/.idea/
**/.vscode/
*.log
*.class
*.pyc
*.jar
!gradle/wrapper/gradle-wrapper.jar
.DS_Store
Thumbs.db
local.properties
.env
.env.*
''')
    (repo/'.gitattributes').write_text('''# Preserve historical source bytes; mark real binary assets explicitly.
*.png binary
*.jar binary
*.pdf binary
''')
    tools = repo/'tools/history'
    tools.mkdir(parents=True)
    for name in ['archive_tools.py', 'reconstruct_history.py', 'verify_history.py']:
        shutil.copyfile(Path(__file__).with_name(name), tools/name)
    run(repo, 'add', '--all', '--force', '--', '.')
    run(repo, 'commit', '-F', '-', data=('Document reconstructed history and ignore local build output\n\n'
        f'{len(records)} source snapshots verified against archive bytes and executable modes.\n'
        'No changes to gameplay, resources, recipes or archived project build scripts.\n').encode(),
        env={**common_env, 'GIT_AUTHOR_DATE': common_env['GIT_COMMITTER_DATE']})
    assert not run(repo, 'status', '--porcelain').strip()
    for p in ['core/src/main/java', 'foci/src/main/java', 'directional/src/main/java']:
        assert (repo/p).is_dir()
    run(repo, 'fsck', '--full', '--strict')
    print(json.dumps({'repository': str(repo), 'snapshots': len(records), 'tags': len(seen_tags),
                      'main_commits': len(records)+1, 'total_commits': len(records)+1+bool(audit),
                      'head': run(repo, 'rev-parse', 'HEAD').decode().strip(),
                      'latest_source_commit': records[-1]['commit'], 'reconstructed_at': reconstructed}), flush=True)

if __name__ == '__main__':
    main()
