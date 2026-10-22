# Directional Antenna 0.2.6 — reception profiles

Angles are horizontal offsets from the receiver's aimed bearing. The two types share one maximum-bonus scale:

| Angle | Parabolic | Wide-angle |
| ---: | ---: | ---: |
| 0–30° | 100% | 75% |
| 45° | 50% | 75% |
| 52.5° | 15.625% | 62.5% |
| 60° | 0% | 50% |
| 67.5° | 0% | 25% |
| 75° and beyond | 0% | 0% |

The parabolic uses a smoothstep shoulder over 30–60°. The array uses smoothstep shoulders over 45–60° and 60–75°. Both sides are symmetric; ordinary omnidirectional reception is retained. Directional bonuses select the strongest applicable antenna and never stack. Maximum tower-scaled allowance remains unchanged; percentages affect the additional allowance only.

The build and all 18 unit checks passed. All seven focused Minecraft GameTests passed with Repeaters 0.5.2 and Dimensional Foci 0.5.2 installed: native reach and travel, angular falloff, inherited weak power, transmitter isolation, all seven aims, optional relay reception/cache invalidation, and physical wide-array shoulder values. The QA-only selector limits the run to these tests. No live microphone playback or graphical capture is claimed.

`release-verification.json`, the four JUnit XML files, `reception-gametests.xml` and `reception-server.log` retain the evidence. The prototype differs from 0.2.5 in exactly four runtime entries: the two reception-math classes, English tooltips and mod metadata. All models, textures, mounts and other runtime entries are byte-identical. No tests, QA selection helper, preview helper, core addon or Foci addon are bundled.

Reproduce:

```sh
./gradlew :directional:build :directional:prototypeJar :directional:runGameTest -PwithFoci -I directional/validation/0.2.6/server-check.gradle --no-parallel
```

Use the Directional Antenna prototype on server and clients; no replacement core or Foci JAR is needed.
