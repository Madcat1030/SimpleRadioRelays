"""Verify recovered Git history; optionally compare every file to the original ZIPs."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
from archive_tools import read_archive

def git(repo, *args):
    return subprocess.check_output(['git', *args], cwd=repo, stderr=subprocess.STDOUT)

def check_files(repo, ref, files):
    expected = {p: (v['mode'], v['oid']) for p, v in files.items()}
    actual = {}
    for row in git(repo, 'ls-tree', '-rz', '--full-tree', ref).split(b'\0'):
        if row:
            head, path = row.split(b'\t', 1)
            mode, kind, oid = head.decode().split()
            assert kind == 'blob'
            actual[path.decode()] = (mode, oid)
    assert actual == expected, f'File contents/modes differ: {ref}'

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument('--archives', type=Path)
    args = parser.parse_args()
    repo = args.repo.resolve()
    manifest = json.loads((repo/'history/snapshots.json').read_text())
    snapshots = manifest['snapshots']
    assert len(snapshots) == manifest['snapshot_count']
    parents = git(repo, 'rev-list', '--reverse', '--first-parent', 'main').decode().splitlines()
    assert parents[:len(snapshots)] == [s['commit'] for s in snapshots]
    checked = 0
    for item in snapshots + ([manifest['partial_audit']] if manifest['partial_audit'] else []):
        ref = item.get('snapshot_tag') or item['tag']
        commit = git(repo, 'rev-parse', ref+'^{commit}').decode().strip()
        assert commit == item['commit'], f'Tag moved: {ref}'
        assert git(repo, 'rev-parse', ref+'^{tree}').decode().strip() == item['tree']
        for tag in item.get('tags_created', []):
            assert git(repo, 'rev-parse', tag+'^{commit}').decode().strip() == commit
            assert git(repo, 'cat-file', '-t', 'refs/tags/'+tag).decode().strip() == 'tag'
        if args.archives:
            archive = args.archives/item['archive']['name']
            assert hashlib.sha256(archive.read_bytes()).hexdigest() == item['archive']['sha256']
            files, omitted, root = read_archive(archive)
            check_files(repo, ref, files)
            assert root == item['original_root']
            assert omitted == item['excluded_files']
            assert len(files) == item['included_files']
            checked += len(files)
    assert git(repo, 'rev-parse', 'v0.9r1^{commit}').decode().strip() == snapshots[-1]['commit']
    git(repo, 'fsck', '--full', '--strict')
    assert not git(repo, 'status', '--porcelain').strip(), 'Working tree is not clean'
    print(json.dumps({'snapshots_verified': len(snapshots), 'audit_verified': bool(manifest['partial_audit']),
                      'archive_files_compared': checked, 'git_integrity': 'passed', 'working_tree': 'clean'}))

if __name__ == '__main__':
    main()
