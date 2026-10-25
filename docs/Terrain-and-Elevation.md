# Terrain and elevation

Repeaters 0.6.0 adds a coarse Overworld terrain model with three modes. **Off** restores pre-terrain propagation, including removal of the elevation bonus and all height-query work. **Relay Mapping** is the default on a fresh install and enables cached inter-relay terrain attenuation plus the capped elevation bonus. **Full** additionally checks ordinary radio/device links. Terrain loss excludes the Nether, End, cross-dimensional links and projected focus sites. An Overworld relay's elevation bonus remains part of its antenna power when a focus projects it elsewhere.

## Server settings

Settings live under `terrain` in `config/simpleradio-repeaters.json`:

```json
{
  "mode": "relay_mapping",
  "sampleSpacing": 64,
  "maxSamplesPerPath": 1024,
  "samplesPerTick": 4,
  "clearance": 32.0,
  "nearGroundLoss": 0.10,
  "buriedLoss": 0.35,
  "maxElevationAntennaBonus": 0.10,
  "elevationReferenceY": 64,
  "elevationFullBonusY": 192
}
```

Commands require operator permission:

| Command | Effect |
| --- | --- |
| `/radiorepeaters terrain` | Show mode, saved profile count, pending work and sampling time |
| `/radiorepeaters terrain off` | Restore 0.5.8 propagation and stop height-query work |
| `/radiorepeaters terrain relay_mapping` | Use cached inter-relay terrain attenuation and the elevation bonus |
| `/radiorepeaters terrain full` | Also enable ordinary device-link checks |
| `/radiorepeaters terrain reload` | Reload the JSON settings |
| `/radiorepeaters terrain clear` | Discard saved profiles and resample active relay paths |

The three mode commands save their changes. The JSON values are `off`, `relay_mapping` and `full`. Clear profiles after intentionally replacing the world's terrain generator or seed.

## Relay links

The existing guarded antenna scan also records the highest antenna block in the main vertical mast, including a wired extension's remote mast. Incomplete scans retain the last verified tip. Existing 0.5.8 records acquire a tip when visited and scanned; records without a verified tip retain ordinary propagation until then.

The engine's `ChunkGenerator.getBaseHeight` is queried with `OCEAN_FLOOR_WG` and the world's `RandomState`. This estimates solid base terrain, excluding the water surface. It does not request a world chunk, run decoration or carving, tick blocks, or write chunk terrain. Buildings, trees, tunnels, excavation, and exact cave geometry are outside this approximation. Unusual generators that cannot answer the height query fall back to ordinary propagation and log the failure once.

Horizontal samples are normally 64 blocks apart, including both endpoints. Very long paths use a wider spacing to stay within `maxSamplesPerPath`. The terrain line is compared with a ray between the top faces of the two verified mast tips. Full clearance of 32 blocks has no extra loss; touching the estimated ground has a 10% distance penalty; being 32 or more blocks below it has a 35% penalty. Smooth transitions connect these anchors. The final fraction is the distance-weighted average along the path, so sampling density does not multiply attenuation. Extra distance participates in both RF reach and travel loss; inherited audio degradation is still preserved.

Only relay pairs that can pass the ordinary RF check are scheduled. Work runs on the server thread with a column-count budget and a two-millisecond soft time budget per tick. A single engine query cannot be interrupted, so this is not a hard upper bound on an unusual generator's query time. Audio routing only requests work and reads results.

Terrain samples are saved separately in `data/simpleradio_repeater_terrain.dat`, bounded to 1,024 physical paths. Opposite directions and different frequency graphs share samples. Cards, catalysts, power, amplifier and dish changes reuse this data. A mast-height change reuses the ground samples and recalculates clearance; moving its horizontal position requires a new profile. New profiles and changed mast heights become visible on the Overworld's `gameTime % 200 == 0` boundary. Sample jobs are spread across intervening ticks. An unmeasured new relay link waits for its first profile instead of temporarily bypassing terrain loss.

The elevation bonus increases the relay's antenna contribution by up to 10%, ramping from zero at tip Y=64 to the cap at Y=192. It does not multiply the radio's entire base power. It uses the same committed tip heights and is disabled in `off` mode. The existing antenna base remains the RF coordinate origin for distance, wires and dimensional policies; the tip is used for environmental clearance and elevation only.

Ordinary unchanged antenna scans and chunk unloading do not discard profiles. Broken wires, removed relays and other hardware invalidations retain their immediate behavior. Server datapack reloads clear terrain profiles so generator-related changes can be picked up.

## Optional device links

`full` mode covers native transmitter/receiver links, handheld links, relay ingress and relay egress in the Overworld. Links at or below **256 blocks** of endpoint separation bypass the terrain system entirely. For relay ingress and egress, the committed mast tip is the environmental endpoint. Ordinary native device links use their native endpoint locations.

Outside that radius, the first check is queued. Each active ordered endpoint pair retains only its latest loss fraction, the positions sampled, and a pending-work flag. No device sample array or path profile is retained or saved. The scalar result is reused until either end moves **more than 16 blocks** from the positions of the completed check. The previous result remains in use while a replacement is computed; a brand-new remote pair waits for its first result. There is no recurring resample timer for a stationary active pair.

Idle scalar results expire after 30 seconds, and the active-state map is capped at 512 pairs. A later connection then starts with a fresh check. Entering the free radius immediately removes the pair's scalar state and cancels its pending work. Both the native reach and travel hooks apply the same result through composable MixinExtras expression modifications. Wired transfers are excluded.

`/radiorepeaters test` captures terrain settings and cumulative work counters at the start and end. Compare those counters to measure generator work separately from per-packet routing. `/radiorepeaters diagnose` includes verified tips, elevation multipliers and existing device results without starting new terrain jobs.
