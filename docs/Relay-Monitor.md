# Relay Monitor

The Relay Monitor is a passive attachment for the top of a Relay. It replaces a Repeater Bank or Transponder and never forwards audio. The underlying Relay still needs a catalyst, a valid core and an antenna.

Insert one tuned Frequency Card through the front. The selected frequency and AM/FM modulation determine every prediction. An empty slot or untuned card shows only: **Insert tuned card for Relay diagnostics**.

## Overview and pages

The overview shows the antenna base coordinates and dimension, physical mast strength (ANT), effective antenna strength including amplifier and elevation bonuses, TX and RX counts, and other active nodes on the channel. ANT is the raw score used for base-power recovery through ANT 16.

- **TX:** configured nodes this tower can directly transmit to.
- **RX:** configured nodes this tower can directly hear.
- **Other nodes:** operational nodes on this frequency/modulation that are not heard directly. This includes TX-only nodes and nodes outside direct reach. It does not imply a working route through the relay network.

A TX/RX node counts in both direction totals. Nodes are counted once even if a Transponder has several matching ports. The monitor itself is not an active forwarding node. Saved unloaded nodes count when persistent unloaded relays are enabled.

Right-click the screen with an empty hand to cycle from the overview to the map, then through the direct-link pages and back to the overview. The direct-link pages list heard towers first:

| Page | Information |
| --- | --- |
| TX/RX | Destination antenna coordinates, dimension, radio distance, terrain extra-distance percentage |
| RX only | The same details, with RX only identifying the missing return direction |
| TX only | Dimension, a 16-point bearing such as SSE or WNW, and approximate distance; no exact coordinates or terrain figures |

TX-only distances round to the nearest 500 blocks. Below 500 blocks they round to 50 blocks to avoid reporting a zero-distance target. Exact TX-only coordinates are omitted from client display data, not merely hidden by the renderer.

**Radio distance** includes the selected link's dimensional and propagation scaling, before terrain extra distance. In an ordinary Overworld link it equals direct distance. Coordinates identify the physical destination antenna. Interdimensional links do not acquire Overworld terrain attenuation.

Click the inserted card with an empty hand to remove it. Card actions work only through the front face. Hoppers have no inventory access; direct container integrations remain available as with existing card banks.

## Network map

The map puts this antenna at **O** in the center, with north up. Positions are relative and scale to fit the screen. The map draws tower markers only; terrain still affects reach.

| Marker | Meaning |
| --- | --- |
| Green label | The monitor can reach and hear this tower. |
| White label | The monitor can hear this tower, but none of the outbound paths on the map reaches it. |
| Orange `0-?`, `A-?`, etc. | The first unheard tower on an outbound branch. The prefix identifies the last known tower; the position is estimated from its rounded bearing and distance. |

The nearest direct neighbors are labeled **O-A** through **O-N**. Up to nine towers in the next layer use their parent's letter, such as **A-1** or **B-2**. A tower appears once, under its best outbound route if it has one, or its best return route otherwise. The map stops after two layers or at the first unheard tower in a branch, whichever comes first.

The map follows the card's frequency and modulation within this dimension and accounts for relay power decay and Ender resets. Use the direct-link pages to inspect channel conversions and cross-dimension links.

## Compass binding

Use an ordinary compass on the overview or a direct-link page to bind it without changing the page:

- A TX/RX or RX-only page binds to that tower's actual antenna base.
- A TX-only page binds to the centre of its displayed 16-point bearing at the displayed rounded distance. Scaled radio distances are converted to blocks in the destination dimension. The target height uses your antenna's height, clamped to that world's limits, rather than revealing the remote tower's height.
- The overview binds to this tower.
- One compass is bound at a time; remaining items in a stack stay unchanged.
- An offhand compass works when the main hand is empty.

The compass uses vanilla lodestone targeting without requiring a lodestone. It keeps the fixed coordinate through saves and reloads and does not load the target chunk. It works in the target dimension. Moving or rebuilding an antenna does not update an already-bound compass; bind it again if needed. A compass bound from a TX-only page stays approximate when the tower later becomes hearable. Rebinding while it is heard replaces the estimate with the exact antenna location. Existing bound compasses are not rewritten. Repeating the same approximate binding gives the same point, rather than random offsets.

## What the numbers mean

Predictions use fresh first-relay departure power, the installed catalyst/amplifier, directional reception and the existing dimensional policy. Input and output channel roles are tested separately for Transponders. A message already weakened by earlier relay hops can fail a nominal direct link.

Survey results are reused while the channel, antenna and network revision remain unchanged. Updates run on the server and at most once per second during ordinary ticking, with immediate checks for card changes and screen interactions. The map is calculated only when it is displayed. The renderer only draws the synchronized page; it performs no network or terrain work.

Surveying reuses the existing cached terrain profiles and budgeted sampling queues. Unknown paths show a Surveying count on the monitor. Survey requests do not add operational SCAN/CHECK markers to either relay. Established paths keep their previous environmental penalty during remapping. No survey loads remote chunks, emits a packet or adds a forwarding port.

## Recipe

| | | |
| --- | --- | --- |
| Iron ingot | Glass | Iron ingot |
| Receiving Module | Compass | Receiving Module |
| Iron ingot | Simple Radio copper wire | Iron ingot |

A stone-tier or better pickaxe harvests the monitor. Breaking it drops its card and, with the proper tool, the block.
