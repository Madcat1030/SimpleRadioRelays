# Transponder checks for Simple Radio Repeaters 0.5.0

Two regression cases were added after an AM channel-conversion test failed in game and recovered when the player disconnected and reconnected. This bundle contains the test patch and automated results. It does not change gameplay or provide an updated release.

## What was checked

- A physical Relay with a supported antenna, gilded blackstone and a simplex Transponder sends a nearby walkie-talkie transmission to an actual placed native radio on the converted channel. A second native radio confirms the direct input transmission. Both deliveries are counted, and encoded audio bytes, activity and channel identity survive conversion.
- Three towers at 0, 4,000 and 8,000 blocks have raw antenna score 16 and gilded blackstone. RT1 and RT2 repeat both AM channels; RT3 converts the input to the output. Ender amplifiers on all towers allow the converted signal to return to the placed radio near RT1, using five relay visits with decay reset to 1. RT2 and RT3 use serialized saved records; their chunks remain unloaded during routing. Removing the amplifiers makes this 4,000-block return route fail under the default power calculation.

The source-side chunk is explicitly loaded in the saved-route case. The separate physical-transponder case covers a real loaded Relay and module. Test audio is synthetic: the placed radios retain their native frequency subscriptions, but their speaker destination is replaced with a capture router so the tests do not decode invalid test bytes. These are packet-delivery checks, not live microphone or client-playback tests.

## Results

| Installation | Addon Minecraft tests passed | Addon failures |
| --- | ---: | ---: |
| Repeaters core | 43 | 0 |
| Repeaters core and Dimensional Foci | 66 | 0 |

Both new cases pass in both installations. Four previously recorded native headless tests still fail in each run: `SystemTest.shortCircuit`, `SystemTest.garbageCollection`, `SystemTest.closedSystem` and `SystemTest.openSystem`. Consequently the full GameTest commands return a nonzero status. The XML reports include those failures rather than concealing them.

The rebuilt runtime-only core JAR is byte-for-byte identical to the distributed 0.5.0 prototype. Its SHA-256 is `f8bc17d391a1b99469538e50e21ca8540edc1f729edba86fa88c68f491afd07c`. Only test code and the development GameTest entrypoint changed. There is no new JAR to install.

## Interpretation

A Transponder can deliver its output channel to ordinary radios directly and to other relays. It does not require another Repeater Bank for endpoint delivery. A nearby RT1 bank repeating the output channel can receive RT2's carrier using tower reception gain and regenerate it locally. This can improve the final leg substantially, but it retains the worst audio degradation already accumulated.

The initial failure that cleared on reconnect was not reproduced. These results establish correct delivery with valid initialized state; they do not identify the cause of the intermittent failure. No speculative gameplay fix was made.

## Applying the tests

Apply `transponder-regression.patch` from the source workspace root with `patch -p1`. It adds the test class under `core/src/main/java` and registers its development GameTest entrypoint. Use the existing JDK 21 build setup; release classes target Java 17.

```sh
./gradlew :core:runGameTest
./gradlew :foci:runGameTest
```

The existing prototype packaging task excludes the added GameTest class and removes its entrypoint from the distribution descriptor.
