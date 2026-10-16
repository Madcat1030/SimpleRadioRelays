# Relay and Repeater Bank models — 0.3.1

Open `blockbench/Relay.bbmodel` and `blockbench/Repeater-Bank.bbmodel` as **Java Block/Item** projects. Each project embeds its textures, and every face has an explicit UV rectangle. All supplied block textures are 16×16. The master projects show the inserted components so you can edit them in place.

The Bank incorporates the supplied `blockbench/Repeater-Bank-import.json` geometry. Version 0.2.7 makes the cards three units tall and centers the label strip in their middle row. Its updated `.bbmodel` keeps the embedded textures and separated component groups. The import JSON is retained unchanged for reference.

The housing uses the original placeholder's slate-green shell, dark recesses, brass frame, olive accents and gold fasteners. The Relay remains a full block with two recessed top sockets. The Bank is a shallow rack with two columns of three cards. Its connector pins extend one model pixel below the Bank, into the Relay's sockets when stacked.

## Static housing and inserted parts

| Project group | Export destination | Displayed when |
| --- | --- | --- |
| Relay `base` | `models/block/relay.json` and `relay_active.json` | Always |
| Relay `inserted_catalyst` | Java transform in `RelayRenderer` | Installed native catalyst block/item |
| Bank `base` | `models/block/channel_bank.json` and `channel_bank_connected.json` | Always |
| Bank `inserted_card_1` … `inserted_card_6` | `models/block/bank_card_0.json` … `bank_card_5.json` | That card slot is occupied |

Hide a group with its eye icon to inspect the empty housing. Hiding a group does not reliably exclude it from a Java model export. For each exported JSON, explicitly disable **Export** on the groups that do not belong in that file. Alternatively, use a copy of the project containing only the required group. Keep the master project intact.

Do not move a partial model to the origin or center it on its own cube. Every model shares the same block origin and NORTH-facing coordinates. The Java renderer applies the block's facing to the whole component, so an independently recentered card would appear in the wrong place.

`make_assets.py` regenerates the supplied geometry, textures, JSON and Blockbench files. It is useful for rebuilding the original design, but it overwrites hand edits and restores the original 0.2.0 Bank geometry; it does not reproduce the 0.2.6 import. After editing in Blockbench, export directly into the resource files instead of running the generator.

## Coordinates and hit regions

Java models use 16 model units per block. NORTH is the front, at Z=0. When viewed from the front, larger X values appear on the left. The base artwork uses one texture pixel per model unit on every face.

| Part | X | Y | Z |
| --- | --- | --- | --- |
| Relay housing | 0–16 | 0–16 | 0–16 |
| Screen bezel | 3–13 | 7–13 | −1–0 |
| Screen glass | 4–12 | 8–12 | Front of bezel |
| Inserted catalyst | 2–6 | 2–6 | −2–1 |
| Top socket 1 | 4–6 | 15–16 | 6–10 |
| Top socket 2 | 10–12 | 15–16 | 6–10 |
| Bank housing | 1–15 | 1–16 | 1–10 |
| Bank connector pins | 4–6 and 10–12 | −1–2 | 6–10 |
| Card faces | See below | See below | 0–1 |

| Card slot | Front-view position | X | Visible card Y | Clickable slot Y |
| --- | --- | --- | --- | --- |
| 1 | Top left | 9–15 | 11–14 | 11–15 |
| 2 | Top right | 1–7 | 11–14 | 11–15 |
| 3 | Middle left | 9–15 | 6–9 | 6–10 |
| 4 | Middle right | 1–7 | 6–9 | 6–10 |
| 5 | Bottom left | 9–15 | 1–4 | 1–5 |
| 6 | Bottom right | 1–7 | 1–4 | 1–5 |

The upper brass lip belongs to the static housing. The card itself fills the three model pixels below that lip. Its middle pixel carries a centered four-pixel dark label strip. Each strip is a NORTH-facing overlay in the corresponding conditional card model, sampling an existing dark texture pixel; no texture bitmap was changed. For both insertion and removal, the clickable slot includes one model unit above the visible card, with no padding below. The bands are explicit rather than nearest-match targeting; a full model unit remains between adjacent row bands. If you move or resize the slots, update `BankConnection.slotX`, `slotY` and `cardAt`, plus the text placement in `ChannelBankRenderer`. The block's selection shape lives in `ChannelBankBlock.getShape`.

## Text is rendered in Java

The screen and card labels are not baked into a texture. `RelayRenderer` draws three lines on the glass: antenna score, status and outgoing relay link count. `ChannelBankRenderer` draws one cached line per occupied card: for example `000.01 AM`. Blank cards display `BLANK --`.

Both renderers use Minecraft's font. `DeviceRendering.text` retains the Relay screen placement. `DeviceRendering.centeredText` centers the Bank label horizontally and vertically, shrinking it to fit both the available width and height. Text sits slightly in front of its surface to prevent depth flickering. The screen and labels use full-bright light values; the housing and inserted parts use the world's light.

The card label centers are X=12/4 and Y=12.5/7.5/2.5 in NORTH-facing model coordinates. In `ChannelBankRenderer`, `LABEL_WIDTH = 3.75`, `LABEL_HEIGHT = .8`, `LABEL_SCALE = .0065f` and `LABEL_COLOR = 0xffe0d8b8` control the label. Width and height cap the preferred scale, so a longer frequency still stays centered. Change `slotX(slot) + 3` or `slotY(slot) + 1.5` to move its center. The four-pixel strip spans X=`slotX + 1` through `slotX + 5`, Y=`slotY + 1` through `slotY + 2`, and Z=−.004. If you move it, update the matching card model and `.bbmodel` group too. The frequency and modulation are joined when the display cache is rebuilt, rather than each frame.

If you move the screen, update its X/Y/Z positions and width in `RelayRenderer`. If you change card depth, update the label Z coordinate in `ChannelBankRenderer`. Keep the text outside the cube face rather than exactly coplanar with it.

The inactive and active Relay models have identical geometry; only the front LED texture changes. The Bank's connected variant changes its top indicator. Apply corresponding texture changes to both variants when replacing the artwork.

## Where to put exported files

The runtime resource root is `src/main/resources/assets/simpleradio_repeaters/`. Models go under `models/block/`, textures under `textures/block/`, and facing variants under `blockstates/`. Item models reference the empty housing, so inserted cards and the catalyst appear only on placed blocks.

`RepeatersClient` explicitly registers all seven partial models with Fabric's model loader. If you rename a partial JSON, update the matching resource ID in `RelayRenderer` or `ChannelBankRenderer` and its registration. The six physical slots and their serialized inventory size are defined in `ChannelBankBlockEntity`; changing the slot count also requires changing the renderer and cached card list.

The Bank must be directly above the Relay, with its pins aligned to the top sockets. Normal placement requires a Relay below and copies its facing. Side and bottom connections are not supported.

## Catalyst rendering in 0.3

The Relay renders the actual installed block/item. Its Blockbench catalyst group remains a placement reference and is excluded from the base housing. The fixed `relay_catalyst.json` model and catalyst bitmap are removed. Block catalysts render inside X/Y=2–6, Z=−2–1 through the translation and scale in `RelayRenderer`; non-block catalysts use an item transform centered in that socket. Change these Java transforms if you move or resize the socket. Native gilded blackstone and amethyst textures follow the active resource pack.
