# Adding handbook chapters

Simple Radio Repeaters 0.5.6 reads chapter definitions from client resources. Core contains its own chapters only. Dimensional Foci and Directional Antenna each supply their own definitions and translations; no list of optional addon IDs lives in the reader.

Put a definition at `assets/<your_namespace>/radio_handbook/chapters/<chapter>.json`:

```json
{
  "order": 80,
  "title": "handbook.your_mod.example.title",
  "entries": [
    { "text": "handbook.your_mod.example.introduction" },
    { "recipe": "your_mod:example_item" },
    { "text": "handbook.your_mod.example.instructions" }
  ]
}
```

Supply those translation keys in your own `assets/<your_namespace>/lang/en_us.json`, and any other supported languages. Each entry must have exactly one `text` or `recipe` property. Text entries are translated and paginated automatically; recipe entries receive a crafting illustration page. Keep headings short enough for the book's narrow page.

Chapters sort by numeric `order`, then their full resource identifier for stable ties. Core uses 10–70, Foci uses 80 and Directional uses 90. The contents page, its overflow pages and all page links are generated from the resulting list. Definitions use normal resource-pack priority, so a pack can override a chapter without editing a jar. Invalid definitions are logged and skipped.

The book reloads definitions from the current resource manager each time it opens. Language changes, resource reloads and newly installed addon content therefore apply to existing handbook items without storing pages in item NBT.

Recipe IDs refer to the server's synchronized crafting recipes. Ingredient alternatives cycle and item tooltips identify each ingredient and result. Shaped and shapeless crafting recipes are supported. A disabled, missing or unsupported recipe displays an unavailable message while leaving the rest of the chapter readable. Recipe display does not craft or consume items.

## Optional integration

Resource-only contributions need no Java reference to Repeaters. If core is absent, Directional Antenna continues working and its handbook resources remain unused. For code that needs to detect core explicitly, use:

```java
FabricLoader.getInstance().isModLoaded("simpleradio_repeaters")
```

Keep any classes that reference core behind the corresponding optional integration boundary. Dimensional Foci already requires core; Directional Antenna does not. No additional book mod or networking protocol is required for chapter contributions.
