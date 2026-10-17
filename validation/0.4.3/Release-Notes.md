# Simple Radio Repeaters 0.4.3

Fabric Minecraft 1.20.1; requires Simple Radio 1.20.1-4.5.7.9 exactly. Install the test-free prototype on the server and all clients, replacing the previous addon JAR.

## Controls

Ordinary empty-hand clicks eject a component only on its matching front socket. Both socket regions include one model unit around the visible insert: catalyst X=1–7, attachment X=9–15, both Y=1–7 in the north-facing model frame. Background and other faces do nothing; an empty socket never falls back to removing the other component. Sneak + empty hand still toggles power. Insertion controls are unchanged.

The Nether Focus tooltip now reads: Attach to an overworld Relay to project a weak signal high into the Nether. Attach to a Nether relay to improve signal reliability and reach.

## Compatibility

Both native distance hooks now use MixinExtras ModifyExpressionValue, multiplying the supplied expression result instead of replacing the call with @Redirect. The unused dimensional-field redirect is removed entirely: native dimensional mode already requires the untouched native penalty. Focus-leg custom attenuation stays in NativeRadioAdapter. MixinExtras is supplied by the existing Fabric Loader requirement; the addon adds no duplicate runtime dependency.

Banks and Transponders expose no sided inventory slots, blocking vanilla hopper insertion and extraction. Direct unsided Container access remains available for mod integrations. Other integrations that honor sided access are blocked too; this is not a promise that every integration ignores those restrictions. Manual card interactions are unchanged.

## Clarifications

The first-transmitter report is still real. The existing audio/power tie-breaker compares direct and relayed paths from one transmitter. A receiver can still accept a weaker copy before a later transmitter offers a stronger one. Resolving that needs collecting competing deliveries before playback; it is not changed by this controls/compatibility update.

Quiet removal already has a routing safeguard: a loaded Relay rereads the mounted Bank every tick and drops the removed channel on the next tick. A new regression confirms that behavior. removeItemNoUpdate deliberately remains quiet; integration callers must still invoke setChanged after batch edits for inventory persistence and client synchronization.

## Validation

Standalone build passes. All 51 unit tests and 60 addon Minecraft tests pass. Coverage exercises the composable hooks across native/optional dimension settings, socket ejection and ignored background/other-face hits in every horizontal facing, actual hopper transfer helpers against both modules, retained direct inventory access, and next-tick quiet-removal recovery. Prior routing, harvesting, feature-command, conversion and persistence checks still pass.

The combined run also includes seven native Simple Radio tests. Four previously recorded headless failures remain unchanged: shortCircuit, garbageCollection, openSystem and closedSystem. Their cause remains unresolved; they are not established live-server bugs. Separated reports accompany the source.

The prototype excludes addon test fixtures and audited test-only helpers, with retained methods/debugging data compared against the full build. No addon obfuscation is added. Tests use synthetic packets; live multiplayer microphone playback was not exercised.
