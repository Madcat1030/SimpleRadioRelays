# Layout and model notes — 0.9r1

These measurements describe the current implementation. Edit the review prose or annotate requested changes; no need to change Java yourself.

## Handbook

Source: `core/.../client/HandbookBook.java` and `HandbookScreen.java`.

- Native Minecraft book, 114-pixel text width and 14 lines per page.
- Chapter titles are bold. Each text entry starts a fresh page and automatically continues when necessary.
- Paragraph breaks are preserved. Two line positions are reserved around the heading/footer; actual body capacity depends on wrapped heading height.
- Recipe entries get their own page with a 3x3 crafting grid, output and native item tooltips.
- Contents and Introduction links are generated; installed addons supply their own titles and chapters.
- Recipes use the server's current recipe manager, including datapack overrides; this export shows the bundled default recipes.
- Chapter order lives in `editable/{addon}/resources/assets/{namespace}/radio_handbook/chapters/*.json` as `order`. Within a chapter, the `entries` list sets text/recipe order.
- English prose lives in the corresponding `lang/en_us.json`. Resource packs can override chapters and translations; this editorial export is the stock release.

## In-world labels

Coordinates below use model units, 16 per block, as passed to `DeviceRendering`.

| Surface | Position/size |
| --- | --- |
| Relay ANT | centre X 8, Y 11.8, Z -1; max width 8; scale .008 |
| Relay status | X 8, Y 10.6, Z -1; max width 8; scale .008 |
| Relay LINK | X 8, Y 9.4, Z -1; max width 8; scale .008 |
| Bank cards | X `slotX + 3`, Y `slotY + 1.5`, Z 0; max width 3.75, height .8; scale .0065 |
| Transponder cards | `slotX + 2.5`, `slotY + 1.5`, Z 0; max width 3, height .8; scale .0065 |
| Monitor card | X 8, Y 2.5, Z 0; max width 4, height .8; scale .0065 |
| Monitor display | X 8, centred about Y 10; max width 11.6, per-line height .85; scale .0065 |

Monitor spacing is `min(1.05, 7.25 / lineCount)`. Its update payload permits eight lines. The new overview uses all eight. `DeviceRendering.centeredText` shrinks long text to fit its width/height limits; the current monitor strings can therefore be longer than card labels. Request wording and size changes together if readability needs adjustment.

Bank slots 0–5 have `slotX = 9, 1, 9, 1, 9, 1` and `slotY = 11, 11, 6, 6, 1, 1` in the model's north-facing frame. Thus text centres are `(12,12.5), (4,12.5), (12,7.5), (4,7.5), (12,2.5), (4,2.5)`. Front-facing transforms can mirror the apparent left/right order. The visible card is three units high; its click region includes one unit above and no extra unit below.

The Transponder's Simplex arrow is at X 8, Y 8. Duplex arrows use Y 8.8 and 7.2. Multicast uses `*` at Y 8. Panel width is 3.25, height 1.3, scale .012.

## Models and textures

All current model, blockstate and texture files are copied under `editable/{addon}/resources/`. JSON is authoritative. Available Blockbench projects are in `editable/blockbench/`; treat retained projects/alternates as starting points and compare against the shipped JSON before replacing a model.

Core block models are ordinary Java block JSON. Inserted cards are separate models drawn only when present; catalysts/upgrades are rendered from the actual inserted item. Text positions live in the renderers and do not move automatically with edited card/screen geometry. Card hit regions are defined by the corresponding block class and also need to follow any slot moves.

For Directional, the whole-dish model is authoritative. Gradle splits `mount_` geometry, shared clamp/bolt geometry and the aimable head at build time. A source edit to the whole model needs rebuilding; changing only the copied authoring JSON is not a complete installable resource-pack override. Opposite receivers share one set of mounting brackets. The 3x3 placement rules and beam behavior are code, independent of the model.

Keep texture pixels and model units at 1:1, and avoid coincident faces. Existing alternative parabolic styles remain available through the built-in resource packs.

Native captures are listed in `Reference-Captures.md`. They are existing geometry references, not current 0.9r1 UI proofs. No new model redesign or screenshot render was needed for the housekeeping release.

Requested layout / model changes:

