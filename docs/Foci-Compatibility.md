# Foci compatibility

Starting with Repeaters 0.7.3 and Dimensional Foci 0.7.3, compatibility uses an integration API number rather than matching release numbers. Both jars advertise an integer `custom["simpleradio_repeaters:foci_api"]` in `fabric.mod.json`. Core offers that API; Foci requires it. The initial value is **1**.

Foci checks the installed metadata in its `preLaunch` entrypoint. This class depends only on Fabric Loader, Java and logging; it does not load Minecraft, core gameplay classes or the Foci initializer. It reads the installed jar's marker rather than an inlined Java constant. A matching API allows startup even when release numbers differ. A missing, malformed or different API fails before game initialization with the installed versions and the required/offered API. There is no packet-time compatibility check and no operator config that bypasses it.

Foci also declares `simpleradio_repeaters: >=0.7.3`, the first core release containing the marker. That lower bound does not require Foci to be rebuilt for later core releases. Older Foci jars retain their exact dependency and must be replaced once to adopt the scheme. Simple Radio's separate exact dependency is unchanged.

## Release rules

The root `gradle.properties` separates four values:

| Property | Owner | Change when |
| --- | --- | --- |
| `mod_version` | Core | Publishing a core release |
| `foci_version` | Foci | Publishing a changed Foci release |
| `foci_api_version` | Core | Breaking the integration contract Foci consumes |
| `foci_required_api_version` | Foci | Updating Foci to a different core integration contract |

Ordinary bug fixes, terrain balance, rendering, recipes and internal routing changes retain API 1 while that contract remains compatible. Leave `foci_version` unchanged when only core changes; the existing Foci jar remains usable. The offered and required API properties are deliberately separate, so increasing core's API does not automatically approve an unchanged addon.

Bump the API when changing the signatures or behavior Foci relies on: attachment registration/items, `RadioPolicy`, `RadioSpace` sites and coordinate/projection rules, `RadioPropagation` profiles, the `TowerRecord` accessors Foci uses, resource invalidation, or any shared initialization/network contract. Adapt and test Foci before advertising support for that new API. This is a maintained compatibility contract; a number alone cannot detect a developer accidentally changing behavior without bumping it.

Adding a compatible API feature can retain the same API number. If a newer Foci release starts using that feature, raise its minimum core dependency to the release that added it; older Foci builds can continue using the older API surface.

Core remains optional with respect to Foci: it does not depend on or load the addon. Directional's existing optional integration and standalone support are unchanged.

## Checks

`FociCompatibilityTest` parses the actual processed mod metadata with Fabric Loader. It tests the dependency lower bound, different core/Foci releases with one API, mismatched APIs, and missing/malformed markers, including fractional numbers that must not be truncated to an accepted integer.

Run the ordinary unit and combined server suites, then `python3 tools/check_foci_compatibility.py`. Set `RADIO_GRADLE_COMMAND` when using a host-specific Gradle wrapper. The smoke check launches real Fabric with core metadata changed to 0.99.0 and an unchanged Foci release. It checks API 1 acceptance, API 2 rejection and missing-marker rejection. A QA-only sentinel deliberately stops each probe in pre-launch; nonzero exits are expected. The checks require the expected compatibility decision and that no game world starts. Temporary metadata is restored, and no probe code is included in production jars.
