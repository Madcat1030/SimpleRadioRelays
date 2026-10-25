# Repeaters 0.6.1

Terrain mapping now preserves established links during remapping, with SCAN for initial mapping and CHECK while an existing result is retained. Normal datapack reloads preserve saved terrain. Broken wires and removed hardware still invalidate their radio connections immediately.

Full mode rejects unreachable native, relay-ingress and relay-egress links before requesting terrain work. Pending jobs and recently active scalar results are no longer evicted by the old 512-entry LRU traversal. The separate bounded work queue can drain without losing the states it is measuring. Saved relay profiles are retained by registered mast columns instead of a 1,024-profile LRU limit.

Sampling uses a configurable one-millisecond soft tick budget, a 64-column ceiling, priority for relay work, and a reduced allowance under server load. Results still publish on the native 200-tick antenna cadence. Added counters expose queue size, remaining columns, wait age, peak sampling-tick time and overload fallback.

Near-ground and underground attenuation use the requested extra-distance anchors: +8/0%, ground/1%, -8/10%, -16/25%, -32/50%, -64/100%, -96/150%, -128/200%, with linear interpolation and a 200% deep-ground cap. Samples are distance-weighted rather than compounded. A mast ten blocks above flat terrain pays no terrain penalty. Existing unchanged 0.6.0 configuration defaults migrate to the new settings; malformed reloads now report command failure and retain live settings.

Dimensional Foci 0.6.1 changes compatibility metadata only. Directional Antenna 0.2.9 remains unchanged and compatible. No block, recipe, amplifier, dimensional, directional-beam or relay-decay changes are included.
