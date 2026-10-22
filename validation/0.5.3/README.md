# Handbook validation — 0.5.3

Runtime builds: core 0.5.3, optional Dimensional Foci 0.5.3. Directional Antenna remains 0.2.6. The feature uses native `BookViewScreen` with client-built pages and adds no book-mod dependency.

The core and Foci builds and all 53 unit checks pass. One selected native dedicated-server GameTest verifies the real handbook recipe and server-side use without consuming or annotating the item. Earlier radio GameTests are not repeated; no propagation or routing code changes.

The native graphical client checks core only, Foci only, Extenders only and both optional chapters. The books have 25, 32, 31 and 38 small native pages respectively. Every page fits within 14 native text lines at 114 pixels; every link target is in bounds. Actual page navigation and the registered item model are checked. Six unretouched screenshots show the cover, core and expanded contents, catalysts, foci and extender profiles. `handbook-client.log` records the checks and capture confirmations.

The QA-only preview's first attempt incorrectly expected navigating to the already-open cover to report a page change. The corrected selector moves away before testing the native change-page action. This was a QA assertion error, not a runtime handbook failure.

```sh
./gradlew :core:build :foci:build :core:prototypeJar :foci:prototypeJar :core:runGameTest -I validation/0.5.3/handbook-check.gradle --no-parallel
python3 tools/verify_prototypes.py
./gradlew :directional:runClient -PwithFoci -I validation/0.5.3/handbook-preview.gradle
```

The optional disposable Linux display wrapper is `capture_handbook.py`, using the existing host-only setup under `directional/validation/0.2.3/host-setup/`. No host adapter, preview helper or selector is distributed in either runtime JAR. Live microphone playback is outside these checks.
