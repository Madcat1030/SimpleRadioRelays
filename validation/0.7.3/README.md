# Repeaters / Foci 0.7.3 validation

101 unit tests passed (79 core, 5 Foci, 17 Directional). The ordinary native server suites passed 83 core tests and 129 combined core/Foci/Directional tests. Reports are retained here. The early startup log confirmed Foci API 1 acceptance before game initialization.

Three real Fabric loader probes used the same Foci 0.7.3 development jar and temporarily altered core-jar metadata to version 0.99.0. API 1 was accepted. API 2 and a missing marker were rejected with explicit compatibility errors. A QA-only preLaunch sentinel deliberately stopped every probe before game initialization; therefore the BUILD FAILED / nonzero exits in those three smoke logs are expected. The test requires the correct compatibility decision, sentinel and absence of world startup. All temporary jar bytes were restored and verified. No probe entrypoint or class is shipped in either production jar.

The Foci unit tests parse actual processed metadata with Fabric Loader, checking the minimum core dependency, independent release versions, mismatched epochs and invalid/missing markers. Core and Foci API build properties are separate, and Foci has its own release-version property. Source metadata stays valid JSON; processResources stamps each module's authoritative API property into its output.

The core jar differs from 0.7.2 only in fabric.mod.json. Existing Foci classes/resources are byte-identical: only the FociCompatibility class and mod metadata are added/changed. Directional 0.2.9 is byte-identical, SHA-256 dd686cb9668b49b29575de8faa231a585539517fb1fe3f49941afbf09093cbef. Gameplay, routing, terrain, saved data and rendering code are unchanged.

Production/prototype verification checks identical unmodified runtime copies, Java 17 class versions, module boundaries and test exclusion. Builds use JDK 21, Gradle 9.5.0 and Loom 1.17.21. The host-only unsupported-Unix-socket agent is not distributed. No live microphone playback or new client capture was performed for this compatibility/packaging change. See docs/Foci-Compatibility.md for release rules and the API contract.
