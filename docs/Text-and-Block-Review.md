# Text and block review — 0.9r1

The review pack is an editable snapshot of all three addons. Start with its `START-HERE.md`. Change prose directly in the Markdown files, or edit the matching JSON resources if preferred. Keep the translation keys and recipe identifiers attached to your edits so they can be applied to 0.9r2.

- `01-Names-and-Tooltips.md`: every English translation outside the handbook, grouped by addon.
- `02-Manual.md`: the full introduction and every chapter, in book order, including recipe positions and optional-addon sections.
- `03-Screens-and-Commands.md`: text assembled in code, status labels, command feedback and resource-pack names.
- `04-Blocks-and-Recipes.md`: all player-facing blocks/items, internal dish parts, and the exact shipped recipes.
- `05-Layout-and-Models.md`: current book/display measurements, model sources and editing notes.
- `editable/`: the exact language files, chapter definitions, recipes, blockstates, model JSON, textures and available Blockbench projects.
- `references/`: retained native Minecraft captures for geometry reference. They are labelled with their original version and may show older wording.

The book wraps text using Minecraft's current font and language. Every text entry starts on a new page; recipe entries have their own page. Paragraph breaks are preserved, but there are no manually numbered pages to keep in sync. Reorder chapters or recipe/text entries, request a different page size, or mark desired paragraph splits in the review copy. Rendering constants are listed for reference; nothing in the pack needs compiling to propose an edit.

`%s` placeholders are substituted at runtime. Keep them in translated messages unless the corresponding code is also being changed. Values in braces in screen/command examples stand for live numbers, frequencies or coordinates. AM/FM tuning values, directions and numeric diagnostic output are generated data.

The pack contains the addons' text, not Simple Radio's own translations or Minecraft's standard UI. A source-referenced literal inventory covers remaining strings embedded in addon code, including technical diagnostics, so none need to be guessed from screenshots.

To regenerate from source:

```sh
python3 tools/export_review_pack.py /absolute/output/directory
```

The tool reads `src/main` resources. It does not include test messages or alter the release.
