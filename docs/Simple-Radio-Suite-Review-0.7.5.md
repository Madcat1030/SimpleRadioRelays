**Simple Radio suite review**

Reviewed: Repeaters 0.7.5, Dimensional Foci 0.7.3 and Directional Antenna 0.2.9. The requested compass behavior is implemented separately in core 0.7.6. The remaining findings below are proposals; their gameplay behavior has not been changed.

**Overall assessment**

The suite is manageable, with sensible module boundaries. It does not need a rewrite or a shared framework. Its complexity is concentrated in lifecycle recovery, radio routing and terrain scheduling, where the features actually require it. The main risks are that these central classes become harder to change, and that players cannot always distinguish a hardware state from a prediction or an environmental measurement.

The baseline has 99 runtime Java files and approximately 7,000 lines: 5,555 in core, 251 in Foci and 1,192 in Directional. The three production jars total about 640 KiB. Much of the source tree's bulk is historical validation evidence, model work and captures rather than shipped gameplay code. Size alone does not establish quality, but these figures do not suggest uncontrolled runtime growth.

**Reported issues**

| Report | Accuracy and impact | Recommended action |
| --- | --- | --- |
| A survey makes nearby relays show SCAN/CHECK | Confirmed. Surveying uses the same terrain-request function that marks operational relay pairs pending. A passive monitor can therefore affect another relay's display, despite adding no forwarding node. The status itself does not disable that relay. Surveys can also consume the shared sampling budget. | Separate survey requests from operational pending markers while continuing to share measured height profiles and the bounded queue. Show survey progress on the monitor. |
| A TX-only compass reveals the exact position | Confirmed. This was previously intentional and explicitly tested, but conflicts with the revised requirement. The screen omitted exact coordinates while the compass wrote the real antenna base. | Implemented in 0.7.6: use the displayed direction sector and distance bucket for TX-only binding. Keep exact binding for heard towers. |
| The “Mapped” label | A clarity problem. Its boolean means the selected radio leg is dimensional, not that terrain mapping has finished. The value is radio-scaled distance. Local Nether distances can also be scaled while receiving the plain “Distance” label. | Use “Radio distance” or “Scaled distance” consistently, or show physical distance separately where meaningful. No attenuation change is required. |
| One invalid terrain curve value rejects the whole startup config | Confirmed. Curve validation occurs before the new configuration is installed. A startup failure leaves the defaults active, potentially ignoring valid `terrain.mode: off` or `antennaDependentBasePower: false` settings. Reload failures keep the already-active configuration; the bad file is not overwritten. | Recover at the field/section boundary: apply valid unrelated settings, retain the previous curve on reload or use the default curve on startup, and report the fallback explicitly. Malformed JSON should still be rejected. |
| The block-change hook allocates and looks up a key with no towers | Confirmed, with narrower scope than “every block change.” The mixin filters to successful server-side block-type replacements; ordinary property changes are skipped. Once a manager exists, an empty antenna index still reaches key construction. | Add an empty-index return before constructing the dimension/chunk key. This is a small housekeeping optimization; the code already avoids a full tower/world scan per block edit. No measured TPS regression was established in this review. |

There is an important implementation detail in the first fix: switching surveys to the existing read-only terrain lookup alone would be insufficient. A passive monitor does not join the forwarding graph, so its unknown paths might never be requested by real routing. It should be allowed to request measurements without marking those paths as operational relay work.

**The compass change**

For a TX-only page displaying ENE and approximately 3,500 blocks, a new binding targets the centre of the ENE sector at that rounded distance. The exact bearing and unrounded range are not used to choose a finer point. Repeating the same visible reading produces the same target, without random offsets that could be averaged through repeated clicks.

Radio distance is converted into destination-world blocks, including Nether scaling and Foci projections. The estimated target does not copy the remote tower's Y coordinate. TX/RX and RX-only pages retain exact physical antenna binding; the overview still binds the local antenna.

A compass stores a fixed position. Gaining RX does not change a previously estimated compass. Rebinding from a now-heard page replaces it with the exact antenna base. Existing compasses are not migrated or rewritten, including compasses bound by earlier versions.

**Development strengths**

- Core, Foci and Directional have distinct responsibilities. Foci owns its items and propagation policy; core owns forwarding and tower state; Directional owns physical dishes and reception. Directional's Repeaters integration is optional and loaded through an entrypoint. It can continue to run with Simple Radio alone.
- Saved tower records and published directional views keep live block entities out of cached RF paths. No-op tower snapshots avoid invalidating the graph. Guarded geometry scans distinguish incomplete restoration from confirmed destruction and avoid loading missing chunks.
- Relay path alternatives are bounded and pruned, rather than expanding every possible repeated walk into deliveries. Terrain generation is queued with a time budget and reused independently of RF power and card changes.
- Native power hooks and the virtual tower adapter share the new base-power rule. The independent native-object parity tests cover reach as well as travel, including float-sensitive boundaries. This is more useful than testing a copied formula against itself.
- Client card labels are cached in `displayCards()`. Renderers draw synchronized data rather than making server requests or repeatedly parsing card NBT. Shared rendering and card-container code already prevent substantial duplication.
- The baseline's retained evidence records 105 passing unit tests, 91 core server tests and 137 combined server tests. Production verification excludes test/preview classes and confirms ordinary/prototype jars are identical.

**Maintenance work worth doing**

`TowerManager` is the clearest future pressure point: 797 lines cover lifecycle reconciliation, indexes, persistence updates and graph construction. `RelayRouter` is 421 lines, with delivery selection, native callbacks, wire handling and several nested candidate loops. These classes have real responsibilities, but a future feature could easily entangle them further. Extract a graph-construction helper and a per-receiver choice helper when those areas next need substantial work. Keep the current lifecycle guarantees and regression fixtures around those moves.

Formatting is uneven. Several small classes compress multiple actions and branches onto one line, while other files are extensively expanded. Consistent formatting and explicit imports would improve reviewability more than adding interfaces. Keep a formatting pass separate from behavioral fixes.

Concrete small leftovers found:

- Unused imports: `RelayAttachment` in `RelayRenderer`, `Nullable` in `RelayBlock`, and `BlockState` in Directional's `DishGeometry`.
- `CardDisplay` caches `frequencyLabel` and `modulationLabel`, but current runtime callers use only the combined `label` and `present`. Those extra fields can be removed if they are not intended as a rendering extension surface.
- The long `Simple-Radio-Repeaters-Documentation.md` still describes exact matching Foci/core releases, contradicting the current API-number contract. It also describes a missing `tools/PrototypeJar.java` shrinker; current `tools/prototype.gradle` simply copies the production jar. These are stale instructions, not an orphaned runtime shrinker.

I did not find a clear unused private-helper subsystem in the runtime review. Some native/virtual RF math and guarded mast scanning are intentionally duplicated across boundaries: speculative virtual links need numeric math, and Directional must remain standalone. Preserve parity tests and comparable restoration tests rather than creating a hard Repeaters dependency just to remove similar code.

The alternate parabolic designs are not orphaned: `DirectionalClient` registers their built-in resource packs. Historical previews, screenshots and build evidence are outside runtime source sets. They may be archived more neatly, but they should not be deleted as dead gameplay code.

One old integration edge remains: `removeItemNoUpdate` clears the visual card cache but does not mark the bank dirty or notify the relay. Hoppers are blocked, and normal player removal uses the notifying method. A direct-container integration must currently call `setChanged()` after quiet edits. Document that contract or add an explicit notification if all external removals are supposed to update immediately.

The first accepted copy of a frame also remains the winner across separately invoked transmitters. Within one routing invocation, candidate selection compares direct and relay options; that is different from collecting copies from independent transmitters before delivery. Keep this as a known behavior unless a real use case justifies delayed aggregation and its extra state.

**Player assessment**

The core build loop is coherent: core and antenna, relay and catalyst, top-mounted module, then tuned cards. The same front-face insertion/removal rules carry between attachments. Banks keep channels separate, transponders offer explicit conversion modes, and the monitor offers diagnostics without becoming a hidden repeating node. Amplifiers and directional designs give players meaningful alternatives to simply making every mast enormous.

The main player difficulty is explaining what a “working” connection means. READY confirms valid hardware; a monitor link predicts a fresh first-relay signal; neither guarantees that an already-degraded message from farther along the network can cross the same link. The handbook already explains much of this, but the survey-induced SCAN/CHECK transitions and “Mapped” wording undermine that explanation. Fix those before adding more routing features.

The new ANT threshold also needs a clearer display. The relay renders amplifier-adjusted ANT, and the monitor includes elevation as well, while base-power recovery uses the raw physical score. For example, a raw ANT 10 mast with an Ender Amplifier displays approximately ANT 18, but retains only 81.25% of its base power. A player can reasonably think they have passed the ANT 16 threshold. Prefer showing raw ANT for construction progress, with effective strength separately on the monitor, or make the distinction explicit beside the displayed value.

Terrain Off and antenna balancing are independent switches. Avoid wording that implies Terrain Off restores every aspect of older station behavior. Foci's installation also intentionally replaces native dimensional access regardless of its old setting; the current README communicates that, and it should remain prominent.

The physical slots and small displays suit the mod's style, though their size makes the handbook and a few clear diagnostic labels important. Many status/page strings remain hardcoded English; this is a localization/readability cleanup opportunity rather than a reason to change the mechanics.

This player assessment uses the code, current handbook/resources and retained server fixtures. It does not claim a new live microphone playtest or a fresh FPS/TPS benchmark.

**Suggested order**

1. Keep the requested compass correction isolated in 0.7.6.
2. Fix per-field config recovery and separate survey/operational terrain status.
3. Clarify raw versus boosted ANT and radio-distance labels; correct stale documentation and remove small unused fields/imports.
4. Refactor the two largest core responsibilities gradually, alongside the next changes that touch them. Keep Directional standalone and retain Foci API 1 while its contract is unchanged.

**Code evidence in the reviewed 0.7.5 source**

| Finding | Location |
| --- | --- |
| Survey requests operational terrain work | `monitor/RelaySurvey.java:62`; `terrain/TerrainManager.java:100`, `116`, `146` |
| Exact TX-only compass and ambiguous label | `block/RelayMonitorBlockEntity.java:122`, `96` |
| Whole-config curve validation | `RepeatersConfig.java:78`, `106`, `107`, `111` |
| Empty-index lookup overhead | `network/TowerManager.java:203`; `mixin/AntennaBlockChangeMixin.java` |
| Boosted display versus raw recovery | `client/RelayRenderer.java:43`; `radio/NativeRadioAdapter.java:19`, `32` |
| Quiet removal and blocked sided automation | `block/ChannelBankBlockEntity.java:91`, `100` |
| Unused display fields | `block/ChannelBankBlockEntity.java:49` |
| Stale compatibility/packaging instructions | `docs/Simple-Radio-Repeaters-Documentation.md:14`, `208` |
| Active alternate packs | `directional/.../client/DirectionalClient.java:17`, `20`, `23` |

Core paths above are relative to `core/src/main/java/dev/paul/simpleradiorepeaters/` unless a different root is written.


**Validation of the compass update**

Core 0.7.6 passed 109 unit tests, 93 core server tests and 139 combined server tests. Coverage includes identical points for identical visible estimates, all sixteen bearings, saved/stacked/offhand compasses, no automatic upgrade after gaining RX, precise rebinding, Nether coordinate scaling, and both directions of Foci projection. The existing Foci 0.7.3 and Directional 0.2.9 jars remain byte-identical. Production archives contain no test classes. The other reported behaviors remain unchanged, as described above.
