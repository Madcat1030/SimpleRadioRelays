# Terrain and elevation — Repeaters 0.7.0

Repeaters estimates the Overworld landscape between antenna tips without loading or generating world chunks. Physical terrain profiles are independent of frequency, power and the radio graph, so ordinary tower edits reuse the expensive work.

## Modes

Set `terrain.mode` in `config/simpleradio-repeaters.json`:

| Mode | Behavior |
| --- | --- |
| `off` | Original pre-terrain propagation; no terrain sampling or elevation bonus |
| `relay_mapping` | Default: cached terrain attenuation between Overworld relays, plus the capped elevation bonus |
| `full` | Relay Mapping plus ordinary transmitter/receiver, handheld and relay/device links in the Overworld |

The Nether, the End and focus projections retain their existing propagation rules. Foci 0.7.0 is a metadata-only compatibility build; Directional 0.2.9 is unchanged.

## Attenuation

Each sample compares estimated ground height with the straight line between the endpoints. Relay endpoints use the top face of the verified mast tip. Positive clearance is above ground; negative clearance is below ground.

| Clearance | Extra distance at that sample |
| --- | ---: |
| 8 blocks or more above ground | 0% |
| Ground level | 1% |
| 8 blocks below ground | 10% |
| 16 below | 25% |
| 32 below | 50% |
| 64 below | 100% |
| 96 below | 150% |
| 128 below or deeper | 200% |

Values interpolate linearly between anchors. Sample penalties are averaged over the path using trapezoidal distance weighting; they are not multiplied or added once per sample. The RF calculation uses `distance × (1 + average penalty)` before the native reception-floor deduction, in both reach and travel calculations.

A completely clear path between ten-block-high masts pays no terrain penalty. A path entirely 64 blocks below the estimated surface behaves as twice its actual distance, giving about half its otherwise available range; at 128 blocks below, it behaves as three times its distance, giving about one-third. Antenna reception, signal power and audio-quality thresholds still apply normally.

For a 2,000-block path with a 500-block mountain section 30 blocks above the signal line, that section has a 46.875% penalty. If the rest clears the terrain by at least eight blocks, the average is approximately 11.72%, making the link behave like 2,234 blocks. Sparse sample placement makes this an estimate rather than an exact cliff-edge calculation.

## Mapping and persistence

`ChunkGenerator.getBaseHeight` is queried with `OCEAN_FLOOR_WG` and the world's `RandomState`. This estimates solid base terrain, excluding the water surface. It does not request a world chunk, perform decoration or carving, tick blocks, or write chunk terrain. Buildings, trees, excavation and exact cave geometry are outside this approximation. Unsupported generators fall back to ordinary propagation and log the failure once.

Samples normally lie 64 blocks apart, including both endpoints. Very long paths widen their spacing to stay within `maxSamplesPerPath`. Ground samples are saved in `data/simpleradio_repeater_terrain.dat` and shared across directions and channels. Active paths have no LRU eviction limit. Paths are pruned when an endpoint mast column is no longer registered; another tower sharing that mast keeps its profiles alive. A completed replacement resolution retires only the old resolution of the same physical path.

- Adding or moving a tower horizontally requests only new paths that can pass the ordinary radio calculation.
- Growing or trimming a mast recalculates clearance using the existing ground samples.
- Changing cards, catalysts, amplifiers, antenna strength or receiver direction reuses the terrain samples.
- Removing a tower needs no new height queries.
- `/reload` preserves measured terrain profiles and still refreshes radio resources and the catalyst catalog.

Geometry and completed relay profiles publish on the Overworld's `gameTime % 200 == 0` boundary. Sampling runs between these boundaries. New paths show **SCAN** while waiting for their first verified terrain result. Existing paths retain their previous environmental penalty during remapping and show **CHECK**. This display does not disable the relay. Real wire cuts, tower removal and channel changes still affect eligibility immediately.

Explicit `terrain clear` keeps the last in-memory penalties while measuring replacements. Changing sample resolution can also use the saved previous resolution until its replacement completes. A normal restart already has the saved profiles; if a restart interrupts a remap and no usable profile exists, the first check must finish again. Old records without a verified mast tip retain ordinary propagation until visited and scanned.

## Work budget

The default is a **one-millisecond soft budget per server tick**, with a safety ceiling of 64 columns. The allowance decreases as recent average tick time rises from 40 to 50 milliseconds, reaching 10% of the configured budget on an overloaded server. Relay mapping receives three out of four sampling opportunities when both queues have work; optional endpoint checks receive the remaining opportunity. Either queue can use the whole allowance when the other is empty.

A single generator call cannot be interrupted and may exceed the budget. Diagnostics report total sampling time, the largest sampling tick, budget overruns, remaining queued columns and oldest relay-job age. There is no terrain generation on an audio-routing call.

A 4,000-block path needs 64 columns. Actual completion time depends on generator cost and other queued work; the ten-second publication boundary is separate from sampling throughput. Unchanged established links do not run recurring generator scans.

## Full-mode device links

Links at or below **256 blocks** of environmental endpoint separation bypass terrain checks entirely. Outside that radius, ordinary reach/travel is checked before terrain work is requested, using the actual packet power and applicable directional reception.

Each recently active ordered endpoint pair retains only its latest scalar penalty, sampled endpoint positions and a pending flag. No device height array or path profile is retained or saved. A new pair waits for its first result; an existing pair keeps its last result until a replacement is ready. Resampling occurs only after either end moves **more than 16 blocks** from its completed sample positions. Stationary active pairs have no resample timer.

Active and pending results are not evicted by packet traversal order. Idle entries expire after 30 seconds. At the protective ceiling of 16,384 retained pairs, additional pairs use ordinary propagation rather than evict live jobs or become permanently silent; `device_capacity_fallbacks` reports this condition. The sampling queue is separately bounded and drains as jobs finish, allowing waiting pairs to be admitted without losing their state.

## Configuration and commands

```json
"terrain": {
  "formatVersion": 2,
  "mode": "relay_mapping",
  "sampleSpacing": 64,
  "maxSamplesPerPath": 1024,
  "samplesPerTick": 64,
  "sampleBudgetMs": 1.0,
  "nearGroundHeight": 8.0,
  "nearGroundLoss": 0.01,
  "undergroundLosses": [0.10, 0.25, 0.50, 1.0, 1.5, 2.0],
  "maxElevationAntennaBonus": 0.10,
  "elevationReferenceY": 64,
  "elevationFullBonusY": 192
}
```

`undergroundLosses` sets extra-distance fractions at depths 8, 16, 32, 64, 96 and 128. Its values must be finite, nondecreasing, and no greater than 4. The above-ground fade reaches `nearGroundLoss` at ground level and zero at `nearGroundHeight`.

The elevation bonus increases only the antenna contribution, by up to 10%, ramping from tip Y=64 to Y=192. It does not multiply the radio's whole base power. The antenna base remains the RF coordinate origin; the tip is used for environmental clearance and elevation.

On upgrade from 0.6.0, unchanged old defaults migrate from `nearGroundLoss: 0.10` to `0.01` and from `samplesPerTick: 4` to `64`. Other custom values are retained. The previous `clearance` and `buriedLoss` settings are replaced by the explicit curve above.

Operator commands:

| Command | Effect |
| --- | --- |
| `/radiorepeaters terrain` | Show mode and work statistics |
| `/radiorepeaters terrain off` | Restore original propagation and stop sampling |
| `/radiorepeaters terrain relay_mapping` | Enable cached relay terrain and elevation |
| `/radiorepeaters terrain full` | Also enable endpoint checks |
| `/radiorepeaters terrain reload` | Reload JSON; report failure and keep current settings if invalid |
| `/radiorepeaters terrain clear` | Remap profiles while existing links retain their previous penalty |

The three mode commands save their changes. `/radiorepeaters test` captures terrain settings and cumulative work statistics. `/radiorepeaters diagnose` inspects existing results without starting height sampling.
