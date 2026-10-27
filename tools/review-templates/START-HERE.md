# Simple Radio Addons — 0.9r1 review pack

Edit these files and send back whichever ones you change for 0.9r2. No compiling is needed to suggest wording or layout changes. Keep the keys/IDs beside your edits.

1. **01-Names-and-Tooltips.md** — every addon name, tooltip and translated message.
2. **02-Manual.md** — the complete book in chapter order, with recipe-page markers. Edit paragraphs, reorder entries or add layout notes.
3. **03-Screens-and-Commands.md** — in-world display labels, command feedback and other code-built text.
4. **04-Blocks-and-Recipes.md** — all six player blocks, seven items, two internal footprint parts and thirteen exact recipes.
5. **05-Layout-and-Models.md** — display/font positions and editable model references.

The same content is available as exact JSON and asset files under `editable/`. Markdown edits are enough; there is no need to edit both forms. If a phrase occurs in several places, say whether the change should apply everywhere. Preserve `%s` placeholders in messages. Braces in examples stand for changing game values.

`references/` contains retained native Minecraft captures, labelled with their original version. They show the models and existing book style; they may contain older wording. `Reference-Captures.md` lists their provenance. These are not new 0.9r1 screenshots.

The book is paginated by Minecraft's font at runtime. Text and recipe entries each start a new page; paragraph breaks are preserved. There are no fixed page numbers in the editorial copy. Optional chapters are included here for review and appear in-game only with the corresponding addon installed.

The export covers the addons' English text and assets. It does not replace Minecraft or Simple Radio's own UI. `Code-Literals.json` is a complete, source-referenced inventory of remaining Java strings, including diagnostic and internal strings, for checking anything not represented by a normal language key. `manifest.json` records coverage and original file hashes.

Version label: **0.9r1**. Fabric version: **0.9.0-rc.1**. Foci API: **1**. Directional still works with Simple Radio alone.

Notes for 0.9r2:

