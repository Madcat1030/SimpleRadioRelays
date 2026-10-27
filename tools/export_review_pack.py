"""Export editable player text, handbook order and block assets without changing resources."""
from pathlib import Path
import argparse
import hashlib
import json
import re
import shutil

ROOT = Path(__file__).resolve().parents[1]
MODULES = {"core": "Repeaters", "foci": "Dimensional Foci", "directional": "Directional Antenna"}
BLOCKS = {
    "simpleradio_repeaters:relay": ("block", "core", "Attach beside a Simple Radio antenna core. Catalyst socket, optional upgrade socket and top module connector."),
    "simpleradio_repeaters:channel_bank": ("block", "core", "Six front card slots; mounts directly above a Relay and repeats each tuned channel separately."),
    "simpleradio_repeaters:transponder": ("block", "core", "One left RX slot and two right TX slots. Centre panel cycles Simplex, Duplex and Multicast."),
    "simpleradio_repeaters:relay_monitor": ("block", "core", "One card slot, passive survey display. Empty-hand screen click cycles targets; compass click binds."),
    "simpleradio_repeaters:frequency_card": ("item", "core", "Tune frequency and AM/FM at the Radiosmithing Station. One card per slot."),
    "simpleradio_repeaters:handbook": ("item", "core", "Opens the live resource-defined guide; installed addons contribute their chapters."),
    "simpleradio_repeaters:copper_amplifier": ("item", "core", "Upgrade socket: +25% antenna contribution. Does not change raw ANT or repair audio."),
    "simpleradio_repeaters:redstone_amplifier": ("item", "core", "Upgrade socket: +50% antenna contribution. Does not change raw ANT or repair audio."),
    "simpleradio_repeaters:relay_amplifier": ("item", "core", "Ender Amplifier: +75% antenna contribution and relay power-decay reset; existing audio damage remains."),
    "simpleradio_repeaters:nether_focus": ("item", "foci", "Overworld: projects high into the Nether. Nether: improves local reception/reach; does not project back to the Overworld."),
    "simpleradio_repeaters:end_focus": ("item", "foci", "Projects between Overworld and End. Inert in the Nether."),
    "simpleradio_directional:parabolic_receiver": ("block", "directional", "Mast-mounted receiving dish, 3x3 mounting area. Aim -45 to +45 degrees in 15-degree steps. Full boost through 30 degrees, 50% at 45, zero at 60."),
    "simpleradio_directional:wide_angle_receiver": ("block", "directional", "Mast-mounted array, same aiming controls. 66% boost through 45 degrees, 44% at 60, 22% at 75, zero at 90."),
    "simpleradio_directional:parabolic_part": ("block", "directional", "Internal multiblock footprint part. No player item or crafting recipe; not a separate antenna."),
    "simpleradio_directional:wide_angle_part": ("block", "directional", "Internal multiblock footprint part. No player item or crafting recipe; not a separate array."),
}
CAPTURES = [
    "validation/0.4.4/Relay-Sockets-0.4.4-In-World.png",
    "art/Repeater-Bank-0.2.7-Card-Detail.png",
    "art/Relay-Expansion-0.4.0.png",
    "validation/0.7.0/Relay-Monitor-0.7.0-Native-Overview.png",
    "validation/0.7.0/Relay-Monitor-0.7.0-Native-Pages.png",
    "directional/art/Parabolic-Receiver-0.2.5-Native-Preview.png",
    "directional/art/Wide-Angle-Receiver-0.2.5-Native-Preview.png",
    "directional/art/Directional-Receivers-0.2.5-Shared-Mounts.png",
    "validation/0.5.6/screenshots/Simple-Radio-Relays-Cover.png",
    "validation/0.5.6/screenshots/Simple-Radio-Relays-Contents.png",
    "validation/0.5.6/screenshots/Simple-Radio-Relays-Transponder-Recipe.png",
]


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(value, encoding="utf-8")


def json_text(value):
    return json.dumps(value, ensure_ascii=False, indent=2) + "\n"


def ingredient(value):
    if isinstance(value, list):
        return " OR ".join(ingredient(v) for v in value)
    return value.get("item", "#" + value.get("tag", "unknown"))


def export(destination):
    destination.mkdir(parents=True, exist_ok=True)
    for template in (ROOT / "tools/review-templates").glob("*.md"):
        shutil.copy2(template, destination / template.name)
    strings, chapters, recipes, literals = {}, [], {}, []
    for module in MODULES:
        resources = ROOT / module / "src/main/resources"
        for lang in sorted(resources.glob("assets/*/lang/en_us.json")):
            current = json.loads(lang.read_text())
            assert not strings.keys() & current.keys(), "Duplicate English translation keys across addons"
            strings.update(current)
        for chapter in resources.glob("assets/*/radio_handbook/chapters/*.json"):
            data = json.loads(chapter.read_text())
            data.update(module=module, source=chapter.relative_to(ROOT).as_posix(),
                        id=chapter.parts[-4] + ":" + chapter.name)
            chapters.append(data)
        for recipe in resources.glob("data/*/recipes/*.json"):
            data = json.loads(recipe.read_text())
            assert data["result"]["item"] not in recipes, "Unexpected alternate recipe; export each variant explicitly"
            recipes[data["result"]["item"]] = (recipe, data)
        for source in sorted((ROOT / module / "src/main/java").rglob("*.java")):
            text = source.read_text()
            # Keep every Java literal, including fragments, so assembled UI text is traceable.
            pattern = r'//[^\n]*|/\*[\s\S]*?\*/|"(?:\\.|[^"\\])*"'
            for match in re.finditer(pattern, text):
                token = match.group()
                if not token.startswith('"'):
                    continue
                try:
                    value = json.loads(token)
                except json.JSONDecodeError:
                    value = token[1:-1]
                if value:
                    literals.append({"source": source.relative_to(ROOT).as_posix(),
                                     "line": text.count("\n", 0, match.start()) + 1, "text": value})
        # Copy exact authoring resources, not generated or historical test resources.
        for source in sorted(resources.rglob("*")):
            if not source.is_file():
                continue
            relative = source.relative_to(resources)
            if relative.parts[0] in ("assets", "resourcepacks") or "/recipes/" in source.as_posix() or source.name == "fabric.mod.json":
                target = destination / "editable" / module / "resources" / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(source, target)
    assert len(BLOCKS) == 15 and len(recipes) == 13
    assert set(recipes) == {k for k in BLOCKS if not k.endswith(("parabolic_part", "wide_angle_part"))}
    write(destination / "Code-Literals.json", json_text(literals))

    text = ["# Names, tooltips and messages — 0.9r1", "",
            "Edit the prose beneath a key. Handbook prose is in `02-Manual.md`. Keep runtime `%s` placeholders.", ""]
    translations = 0
    for module, name in MODULES.items():
        text += [f"## {name}", ""]
        metadata = json.loads((ROOT / module / "src/main/resources/fabric.mod.json").read_text())
        text += [f"Mod name: {metadata['name']}", "", f"Mod description: {metadata['description']}", ""]
        for lang in sorted((ROOT / module / "src/main/resources").glob("assets/*/lang/en_us.json")):
            for key, value in json.loads(lang.read_text()).items():
                translations += 1
                if key.startswith("handbook."):
                    continue
                text += [f"### `{key}`", "", value, "", "Proposed wording / notes:", ""]
    write(destination / "01-Names-and-Tooltips.md", "\n".join(text))

    handbook_keys = set()
    text = ["# Complete manual — 0.9r1", "", "All addons shown. Foci and Directional chapters appear only when installed.", "",
            "Edit prose in place; retain the keys and recipe markers. Chapter order and paragraph/layout notes can be changed here.", "",
            "## Cover and navigation", ""]
    for key in ("item.simpleradio_repeaters.handbook", "handbook.simpleradio_repeaters.intro",
                "handbook.simpleradio_repeaters.introduction", "handbook.simpleradio_repeaters.contents",
                "handbook.simpleradio_repeaters.recipe_missing"):
        text += [f"`{key}`", "", strings[key], ""]
        handbook_keys.add(key)
    chapters.sort(key=lambda c: (c.get("order", 100), c["id"]))
    for chapter in chapters:
        assert chapter["title"] in strings
        handbook_keys.add(chapter["title"])
        text += [f"## {strings[chapter['title']]}", "", f"Addon: {MODULES[chapter['module']]} | Order: {chapter.get('order', 100)}",
                 "", f"Title key: `{chapter['title']}`", "", f"Chapter file: `{chapter['source']}`", ""]
        for index, entry in enumerate(chapter["entries"]):
            if "recipe" in entry:
                text += [f"[Recipe page: `{entry['recipe']}`]", ""]
            else:
                key = entry["text"]
                assert key in strings
                handbook_keys.add(key)
                text += [f"Text entry {index + 1}: `{key}`", "", strings[key], ""]
        text += ["Layout / wording notes:", ""]
    remaining = {k for k in strings if k.startswith("handbook.")} - handbook_keys
    assert not remaining, f"Unexposed handbook text: {remaining}"
    write(destination / "02-Manual.md", "\n".join(text))

    text = ["# Blocks, items and exact recipes — 0.9r1", "",
            "13 craftable player entries: six usable blocks and seven items. Two extra block IDs are internal dish footprint parts.", "",
            "Names and recipes below are read from the shipped resources. Recipe alternatives are shown explicitly. `_` is an empty slot.", ""]
    for identifier, (kind, module, purpose) in BLOCKS.items():
        namespace, key = identifier.split(":")
        name = strings[f"{kind}.{namespace}.{key}"]
        text += [f"## {name}", "", f"ID: `{identifier}` | {MODULES[module]} | {kind}", "", purpose, ""]
        model = ROOT / module / f"src/main/resources/assets/{namespace}/models/{kind}/{key}.json"
        if model.exists():
            text += [f"Model: `editable/{module}/resources/assets/{namespace}/models/{kind}/{key}.json`", ""]
        if identifier in recipes:
            path, data = recipes[identifier]
            text += [f"Recipe: `{path.relative_to(ROOT).as_posix()}` | Output: {data['result'].get('count', 1)}", ""]
            if "pattern" in data:
                rows = [row.ljust(3).replace(" ", "_") for row in data["pattern"]]
                text += ["| Left | Centre | Right |", "| --- | --- | --- |"]
                text += ["| " + " | ".join(row) + " |" for row in rows]
                text += [""]
                text += [f"- `{symbol}` = {ingredient(value)}" for symbol, value in data["key"].items()]
            else:
                text += ["Shapeless ingredients:", ""] + ["- " + ingredient(v) for v in data["ingredients"]]
            text += [""]
        else:
            text += ["Not craftable; no independent player item or inventory slot.", ""]
        text += ["Proposed changes / notes:", ""]
    write(destination / "04-Blocks-and-Recipes.md", "\n".join(text))

    for folder in ("art/blockbench", "directional/art/model-kit"):
        for source in (ROOT / folder).glob("*.bbmodel"):
            target = destination / "editable" / "blockbench" / source.name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, target)
    captures = []
    for relative in CAPTURES:
        source = ROOT / relative
        assert source.is_file(), source
        target = destination / "references" / source.name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)
        captures.append(f"- [{source.name}](references/{source.name}) — retained native capture from `{relative}`")
    write(destination / "Reference-Captures.md", "# Native geometry references\n\nThese are retained Minecraft framebuffer captures. Their filename versions are the capture versions; they are not fresh 0.9r1 screenshots. Models were not redesigned for this release. Older screen wording and ANT values are superseded by the text review.\n\n" + "\n".join(captures) + "\n")
    for name in ("LICENSE",):
        shutil.copy2(ROOT / name, destination / name)

    counts = {"release": "0.9r1", "fabric_version": "0.9.0-rc.1", "translation_keys": translations,
              "handbook_chapters": len(chapters), "recipes": len(recipes), "registered_blocks_and_items": len(BLOCKS),
              "java_string_literals": len(literals), "historical_native_captures": len(captures)}
    files = sorted(p for p in destination.rglob("*") if p.is_file() and p.name != "manifest.json")
    counts["files"] = [{"path": p.relative_to(destination).as_posix(), "bytes": p.stat().st_size,
                        "sha256": hashlib.sha256(p.read_bytes()).hexdigest()} for p in files]
    write(destination / "manifest.json", json_text(counts))
    print(json_text({k: v for k, v in counts.items() if k != "files"}), end="")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("destination", type=Path)
    args = parser.parse_args()
    export(args.destination.resolve())
