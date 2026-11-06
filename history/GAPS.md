# Scope and gaps

This is reconstructed release history. The source archives contained no original Git objects, original commit identities or intermediate edits. Each mainline historical commit represents one recovered full-project source snapshot, ordered by its saved archive record date. Author dates are those archive record dates, not claimed original edit times. Committer/tag dates identify this reconstruction. No original authorship is invented.

The oldest complete source is Repeaters 0.1.2. Complete 0.1.0 and 0.1.1 source was not recovered. The separate `archive/handheld-audit-0.1.1` branch and `audit/v0.1.1-handheld` tag preserve only a seven-file audit/test supplement; they cannot substitute for a full 0.1.1 release. No full-release tag is fabricated for these missing versions.

0.4.0 and its later prototype package are both preserved. They declare the same mod version but have different source/test packaging. Module version tags identify the first recovered snapshot introducing a declared version; snapshot tags always identify exact archived project states. Later suite snapshots also record the modules' separately pinned versions. The manifest explicitly records any source changes without a version bump.

Repeaters first used a single-project `src/` layout; the real 0.5.0 split moved it to `core/src/` and added `foci/`. Directional source snapshots subsequently added `directional/`. Only the enclosing ZIP folder is removed. Java, resources, recipes, tests, model sources, build scripts and documentation retain their archived bytes and paths. `git log --follow` can follow the genuine module move.

Generated logs, bytecode, runtime/cache directories and output/dependency JARs are omitted. The Gradle wrapper JAR is retained. Native captures, validation XML/JSON, asset generators and Blockbench projects are retained. Every omission is listed per snapshot in `snapshots.json`; all archive members were read and CRC checked. Archived test reports are historical evidence, not tests rerun during this reconstruction.

The final mainline commit adds Git housekeeping and reconstruction documentation/tools. It does not modify mod behavior. The latest recovered mod release is 0.9r1 (declared build version `0.9.0-rc.1`); the separate review editor's 0.9r2 target is not a recovered mod release or implemented review.
