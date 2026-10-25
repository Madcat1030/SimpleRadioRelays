# Repeaters 0.7.2

Updates the Overworld terrain attenuation curve. Extra distance is still averaged across the whole path, with smooth interpolation between these points:

| Clearance above/below estimated ground | Extra effective distance |
| --- | ---: |
| +8 blocks or higher | 0% |
| +2 blocks | 1% |
| Ground level | 5% |
| -8 blocks | 10% |
| -16 blocks | 25% |
| -32 blocks | 50% |
| -48 blocks | 100% |
| -64 blocks | 200% |
| -128 blocks or deeper | 300% |

A uniformly buried path has about half its ordinary range at 48 blocks deep, one-third at 64, and one-quarter at 128 or deeper. A partly obstructed path uses its average penalty, rather than the worst depth. Ten-block-high masts across a clear flat landscape still receive no terrain penalty.

The config now uses `terrain.clearanceCurve`, a list of `[clearance, extra-distance fraction]` points. Existing default configs adopt this new balance automatically; deliberately customized old curves are converted into equivalent points at their old depths. Other settings, including Off mode, are retained. Invalid curves reject a reload without replacing the live settings. The in-game handbook and terrain guide reflect the new defaults.

Saved generator-height profiles are reused. Sampling budgets, the 200-tick publication cadence, the 256-block Full-mode free radius, relay routing and dimensional behavior are unchanged. Changing only the curve recalculates penalties without requesting new relay ground samples.

Dimensional Foci 0.7.2 updates compatibility metadata only. Directional Antenna remains 0.2.9 and needs no update.
