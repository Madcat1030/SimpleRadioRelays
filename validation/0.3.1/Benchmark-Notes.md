# Graph-only routing comparison

The reference is the exact 0.3.0 RelayGraph, renamed only to compile beside the current class. Both versions receive fully connected directed graphs with constant 15-point noise and a 64-visit budget. One scenario has one entrance; the other has an entrance at every tower. These synthetic links isolate search/candidate growth; they do not model a specific in-game tower layout.

Each measurement uses 20 warm-up calls, then the median of seven batches. Old search batches use 100 calls; new warm batches use 300; new cold batches use 100 calls (10 at 100 towers). New cold measurements include construction of a fresh graph and empty caches. Run with JDK 21 and -Xmx512m. The volatile sink consumes result counts. This is a small local harness, not JMH or an end-to-end server throughput benchmark.

Receiver loops, packet/source copying, Minecraft world operations, voice codecs and networking are excluded. The results demonstrate reduced numeric search work; they do not promise a particular TPS gain. The production router separately delays ordinary repeated source creation until after winner selection.
