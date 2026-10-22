# 0.5.3 — Relays for Dummies

Adds one in-game handbook, **Relays for Dummies**. Craft it shapelessly with a vanilla book and Simple Radio copper wire, or take it from the Repeaters creative tab. Right-click to read. No additional book mod is required.

The core chapters cover first-tower setup, cards and controls, radio range and audio quality, catalyst ceilings and relay decay, the three amplifier tiers, transponders and troubleshooting. The Dimensional Foci chapter appears only with `simpleradio_repeaters_foci`; Extenders appears only with `simpleradio_directional`. The latter describes Directional Antenna 0.2.6's current profiles. The contents page links to every available chapter, and every chapter page links back.

The reader rebuilds its chapter list and pages whenever opened. It uses the client's current language and actual font metrics to paginate into vanilla's 114-pixel-wide book area. The item stores no page NBT, so installed content can change without recrafting old copies. Dedicated servers do not load client reader classes. Both item and recipe belong to core; optional sections use no optional-addon class references.

Install core **0.5.3** on server and clients. If Dimensional Foci is installed, update it to its matching **0.5.3** JAR too; its implementation and rules are unchanged. Directional Antenna remains **0.2.6**. This release changes no radio routing, propagation, antenna models or device identity behavior.

Validation: all 53 core/Foci unit tests pass, including chapter selection for the four addon combinations and translation coverage. A dedicated Minecraft server verifies the registered recipe and a non-consuming read action with no stale page NBT. A real Minecraft client with both optional addons checks all four content combinations, validates every page against the native font/height limits and every page target, exercises native page navigation, resolves the item model, and records six raw framebuffer captures. The book has 25 small native pages with core alone and 38 with both addons (roughly 1,000 words overall). The native book's small page area determines that count.

Runtime prototypes exclude all addon GameTests, QA selectors and preview helpers, and retain readable names and debug data. The packaging audit verifies retained methods/resources against the development build. Evidence and reproduction helpers are under `validation/0.5.3/`.
