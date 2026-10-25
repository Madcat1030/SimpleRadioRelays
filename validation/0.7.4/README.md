# 0.7.4 validation

The production change distinguishes confirmed antenna block replacements from chunk restoration. Four new real server fixtures cover break/rebuild/trim, unloaded boxes, ordinary unloading, and missing historical chunks. The earlier microphone-activity test now expects confirmed core removal to invalidate immediately.

All 101 unit tests, 87 core server tests, and 133 combined server tests passed. Server runs use real Simple Radio 4.5.7.9. Tests and host tooling do not ship in the production jars. This is automated server validation, not a claim of a live voice round trip.

RF math, terrain math, Foci API 1, Foci 0.7.3 and Directional 0.2.9 are unchanged. See the XML summaries, payload comparison, and prototype verification.
