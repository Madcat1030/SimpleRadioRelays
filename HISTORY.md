# Simple Radio Addons: recovered Git history

48 verified full-project source snapshots, from Repeaters 0.1.2 through suite 0.9r1.

The `main` branch contains the chronological source snapshots followed by a housekeeping commit. The latest working tree contains Repeaters, Dimensional Foci and Directional Antenna. The earlier audit supplement is on its own independent archive branch.

```sh
git log --oneline --decorate main
git tag --list
git diff repeaters/v0.7.5 v0.9r1 -- core/src
git switch --detach v0.9r1
git switch main
python3 tools/history/verify_history.py
```

See [scope and gaps](history/GAPS.md) for reconstruction limits and exact filtering. All tags are annotated with the original archive filename and SHA-256. Per-snapshot commits, versions, hashes and omissions are in [the manifest](history/snapshots.json). Project build instructions remain in the original [README](README.md) and [development guide](docs/Development.md).

## Snapshot index

| Saved date (UTC) | Snapshot tag | Repeaters | Foci | Directional | Commit |
| --- | --- | --- | --- | --- | --- |
| 2026-10-12 18:20 | `snapshot/repeaters-0.1.2` | 0.1.2 | — | — | `0c4a1672a346` |
| 2026-10-13 19:10 | `snapshot/repeaters-0.1.3` | 0.1.3 | — | — | `0205b647d447` |
| 2026-10-14 00:07 | `snapshot/repeaters-0.2.0` | 0.2.0 | — | — | `a8a7af03dbcc` |
| 2026-10-14 18:03 | `snapshot/repeaters-0.2.1` | 0.2.1 | — | — | `465d37824363` |
| 2026-10-14 18:47 | `snapshot/repeaters-0.2.2` | 0.2.2 | — | — | `b8661bca0703` |
| 2026-10-14 18:56 | `snapshot/repeaters-0.2.3` | 0.2.3 | — | — | `c8701eb14950` |
| 2026-10-14 20:37 | `snapshot/repeaters-0.2.4` | 0.2.4 | — | — | `ce8ae38b03ad` |
| 2026-10-15 18:53 | `snapshot/repeaters-0.2.5` | 0.2.5 | — | — | `38f903faec19` |
| 2026-10-16 06:11 | `snapshot/repeaters-0.2.6` | 0.2.6 | — | — | `4f753588c0e7` |
| 2026-10-16 06:52 | `snapshot/repeaters-0.2.7` | 0.2.7 | — | — | `f55780b70a04` |
| 2026-10-16 16:19 | `snapshot/repeaters-0.2.8` | 0.2.8 | — | — | `b655be7c3014` |
| 2026-10-16 17:15 | `snapshot/repeaters-0.3.0` | 0.3.0 | — | — | `bca1d1ad9b05` |
| 2026-10-16 18:22 | `snapshot/repeaters-0.3.1` | 0.3.1 | — | — | `cce7185a45cf` |
| 2026-10-16 20:54 | `snapshot/repeaters-0.3.2` | 0.3.2 | — | — | `a296160eed88` |
| 2026-10-16 22:12 | `snapshot/repeaters-0.4.0` | 0.4.0 | — | — | `8af3785fbdbe` |
| 2026-10-16 22:28 | `snapshot/repeaters-0.4.0-prototype` | 0.4.0 | — | — | `85427e895dc9` |
| 2026-10-16 23:36 | `snapshot/repeaters-0.4.1-prototype` | 0.4.1 | — | — | `a083892aa43e` |
| 2026-10-17 00:04 | `snapshot/repeaters-0.4.2-prototype` | 0.4.2 | — | — | `a9bd2a1d769e` |
| 2026-10-17 00:20 | `snapshot/repeaters-0.4.3-prototype` | 0.4.3 | — | — | `f85b7908649c` |
| 2026-10-17 00:55 | `snapshot/repeaters-0.4.4-prototype` | 0.4.4 | — | — | `fc49c34a4d4a` |
| 2026-10-20 21:34 | `snapshot/repeaters-0.5.0-split` | 0.5.0 | 0.5.0 | — | `da18e6f1cec7` |
| 2026-10-20 22:33 | `snapshot/directional-0.1.0` | 0.5.0 | 0.5.0 | 0.1.0 | `810e94e480b0` |
| 2026-10-21 02:25 | `snapshot/directional-0.1.1` | 0.5.0 | 0.5.0 | 0.1.1 | `474a360320d5` |
| 2026-10-21 03:18 | `snapshot/repeaters-0.5.1` | 0.5.1 | 0.5.1 | 0.1.1 | `f893ef380eeb` |
| 2026-10-21 03:41 | `snapshot/repeaters-0.5.2` | 0.5.2 | 0.5.2 | 0.1.1 | `21478bd88f14` |
| 2026-10-21 17:02 | `snapshot/directional-0.2.0` | 0.5.2 | 0.5.2 | 0.2.0 | `568fd89fa584` |
| 2026-10-21 17:25 | `snapshot/directional-0.2.1` | 0.5.2 | 0.5.2 | 0.2.1 | `ff9299f7b774` |
| 2026-10-21 17:58 | `snapshot/directional-0.2.2` | 0.5.2 | 0.5.2 | 0.2.2 | `d7640b2dca1a` |
| 2026-10-21 19:05 | `snapshot/directional-0.2.3` | 0.5.2 | 0.5.2 | 0.2.3 | `0e725142088b` |
| 2026-10-21 20:34 | `snapshot/directional-0.2.4` | 0.5.2 | 0.5.2 | 0.2.4 | `e180ec62d541` |
| 2026-10-22 00:27 | `snapshot/directional-0.2.5` | 0.5.2 | 0.5.2 | 0.2.5 | `07f69d825674` |
| 2026-10-22 01:03 | `snapshot/directional-0.2.6` | 0.5.2 | 0.5.2 | 0.2.6 | `5de69084adb0` |
| 2026-10-22 01:20 | `snapshot/suite-0.5.3-handbook` | 0.5.3 | 0.5.3 | 0.2.6 | `75d8ef3e3dfd` |
| 2026-10-22 21:36 | `snapshot/suite-0.5.4` | 0.5.4 | 0.5.4 | 0.2.6 | `24df31da31ac` |
| 2026-10-22 22:35 | `snapshot/suite-0.5.5` | 0.5.5 | 0.5.5 | 0.2.6 | `40a8783f2541` |
| 2026-10-23 17:13 | `snapshot/suite-0.5.6` | 0.5.6 | 0.5.6 | 0.2.7 | `c344da581ea8` |
| 2026-10-23 21:28 | `snapshot/suite-0.5.7` | 0.5.7 | 0.5.7 | 0.2.8 | `24ed7ccbb995` |
| 2026-10-24 09:00 | `snapshot/suite-0.5.8` | 0.5.8 | 0.5.8 | 0.2.9 | `4f263798811e` |
| 2026-10-25 03:41 | `snapshot/suite-0.6.0` | 0.6.0 | 0.6.0 | 0.2.9 | `113b0a9d5e1a` |
| 2026-10-25 07:44 | `snapshot/suite-0.6.1` | 0.6.1 | 0.6.1 | 0.2.9 | `166512835633` |
| 2026-10-25 09:11 | `snapshot/suite-0.7.0` | 0.7.0 | 0.7.0 | 0.2.9 | `c658e4413365` |
| 2026-10-25 17:54 | `snapshot/suite-0.7.1` | 0.7.1 | 0.7.1 | 0.2.9 | `c48fa04ba09f` |
| 2026-10-25 19:09 | `snapshot/suite-0.7.2` | 0.7.2 | 0.7.2 | 0.2.9 | `281c5676f5e6` |
| 2026-10-25 19:50 | `snapshot/suite-0.7.3` | 0.7.3 | 0.7.3 | 0.2.9 | `4118e3f51abf` |
| 2026-10-25 20:18 | `snapshot/suite-0.7.4` | 0.7.4 | 0.7.3 | 0.2.9 | `bf7a8e448b6c` |
| 2026-10-27 02:55 | `snapshot/suite-0.7.5` | 0.7.5 | 0.7.3 | 0.2.9 | `3e454d173a3d` |
| 2026-10-27 16:59 | `snapshot/suite-0.7.6` | 0.7.6 | 0.7.3 | 0.2.9 | `a2a3f4477e8b` |
| 2026-10-27 18:22 | `snapshot/suite-0.9r1` | 0.9.0-rc.1 | 0.9.0-rc.1 | 0.9.0-rc.1 | `6fec325b718b` |

## Rebuild the import

Keep the original ZIPs in one directory and pass the included source catalog. The output must be a new directory. The timestamp below reproduces this reconstruction's dates; no archived project code is executed.

```sh
python3 tools/history/reconstruct_history.py \
  --archives /path/to/original-zips \
  --catalog history/source-catalog.json \
  --at 2026-11-06T22:12:02Z \
  --output /path/to/new-repository
```
