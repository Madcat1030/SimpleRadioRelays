# Relay Monitor — Repeaters 0.7.6

The Relay Monitor is a passive attachment for the top of a Relay. It replaces a Repeater Bank or Transponder and never forwards audio. The underlying Relay still needs a catalyst, a valid core and an antenna.

Insert one tuned Frequency Card through the front. The selected frequency and AM/FM modulation determine every prediction. An empty slot or untuned card shows only: **Insert tuned card for Relay diagnostics**.

## Overview and pages

The overview shows the antenna base coordinates and dimension, effective antenna strength, TX and RX counts, and other active nodes on the channel.

- **TX:** configured nodes this tower can directly transmit to.
- **RX:** configured nodes this tower can directly hear.
- **Other nodes:** operational nodes on this frequency/modulation that are not heard directly. This includes TX-only nodes and nodes outside direct reach. It does not imply a working route through the relay network.

A TX/RX node counts in both direction totals. Nodes are counted once even if a Transponder has several matching ports. The monitor itself is not an active forwarding node. Saved unloaded nodes count when persistent unloaded relays are enabled.

Right-click the screen with an empty hand to cycle through heard towers, then TX-only entries, then back to the overview:

| Page | Information |
| --- | --- |
| TX/RX | Destination antenna coordinates, dimension, direct distance, terrain extra-distance percentage |
| RX only | The same details, with RX only identifying the missing return direction |
| TX only | Dimension, a 16-point bearing such as SSE or WNW, and approximate distance; no exact coordinates or terrain figures |

TX-only distances round to the nearest 500 blocks. Below 500 blocks they round to 50 blocks to avoid reporting a zero-distance target. Exact TX-only coordinates are omitted from client display data, not merely hidden by the renderer.

For focus-projected or cross-dimensional links, the distance is labelled **Mapped** and uses the normal effective radio distance. Coordinates identify the physical destination antenna. Interdimensional links do not acquire Overworld terrain attenuation.

Click the inserted card with an empty hand to remove it. Card actions work only through the front face. Hoppers have no inventory access; direct container integrations remain available as with existing card banks.

## Compass binding

Use an ordinary compass on the screen to bind it without changing the page:

- A TX/RX or RX-only page binds to that tower's actual antenna base.
- A TX-only page binds to the centre of its displayed 16-point bearing at the displayed rounded distance. Scaled radio distances are converted to blocks in the destination dimension. The target height uses your antenna's height, clamped to that world's limits, rather than revealing the remote tower's height.
- The overview binds to this tower.
- One compass is bound at a time; remaining items in a stack stay unchanged.
- An offhand compass works when the main hand is empty.

The compass uses vanilla lodestone targeting without requiring a lodestone. It keeps the fixed coordinate through saves and reloads and does not load the target chunk. It works in the target dimension. Moving or rebuilding an antenna does not update an already-bound compass; bind it again if needed. A compass bound from a TX-only page stays approximate when the tower later becomes hearable. Rebinding while it is heard replaces the estimate with the exact antenna location. Existing bound compasses are not rewritten. Repeating the same approximate binding gives the same point, rather than random offsets.

## What the numbers mean

Predictions use fresh first-relay departure power, the installed catalyst/amplifier, directional reception and the existing dimensional policy. Input and output channel roles are tested separately for Transponders. A message already weakened by earlier relay hops can fail a nominal direct link.

Survey results are reused while the channel, antenna and network revision remain unchanged. Updates run on the server and at most once per second during ordinary ticking, with immediate checks for card changes and screen interactions. The renderer only draws the current synchronized text page; it performs no network or terrain work.

Surveying reuses the existing cached terrain profiles and budgeted sampling queues. Unknown paths show a SCAN count. Established paths keep their previous environmental penalty during remapping. No survey loads remote chunks, emits a packet or adds a forwarding port.

## Recipe

| | | |
| --- | --- | --- |
| Iron ingot | Glass | Iron ingot |
| Receiving Module | Compass | Receiving Module |
| Iron ingot | Simple Radio copper wire | Iron ingot |

A stone-tier or better pickaxe harvests the monitor. Breaking it drops its card and, with the proper tool, the block.
