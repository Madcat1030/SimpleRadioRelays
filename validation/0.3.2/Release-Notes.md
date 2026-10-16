# Simple Radio Repeaters 0.3.2

Relays always require a native registered catalyst with finite, positive strength and a verified antenna on a supported core. An empty socket no longer supplies neutral output, and unusable installed items cannot leave a tower eligible. The normal receiver base gain is retained for valid towers. The obsolete requireCatalyst option is removed.

Configuration fingerprints run outside audio-packet lookups, with immediate reload/refresh invalidation and a 100-tick fallback. Client catalogs use a join request/reply and connection-scoped readiness; stale packets and disconnects cannot overwrite a newer session.

The final build passes 43 unit tests, 38 addon Minecraft tests, and an actual local client/dedicated-server join, reload and reconnect check. Four native headless GameTest failures remain identical in name to the earlier addon-disabled control; their cause is unresolved. The combined GameTest task exits 1 for those four tests; the standalone build succeeds.

The range-edge bridge fixture was corrected to use a supported mast height before measuring its cutoff. This changes test setup only. Production attenuation, universal catalyst decay, routing dominance, caching, models, renderers and saved network schema are unchanged. The QA mod is temporary and absent from the release JAR. No actual microphone playback was tested.
