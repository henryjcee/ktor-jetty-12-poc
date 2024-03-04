# Ktor Jetty 12 POC

I've spent long enough faffing around with reactive database drivers etc. and inspired
by [this issue](https://youtrack.jetbrains.com/issue/KTOR-6734/Jetty-engine-Upgrade-Jetty-dependencies-to-the-latest-version-12)
I have decided to build a POC that demonstrates **virtual thread support in Ktor using Jetty 12**. Most of this is code
adapted from the existing Ktor Jetty engine but I've completely dropped servlet support which should be the fastest way
to run Jetty. **Websockets and HTTP3 seem to working** but I haven't tested either in anger.

It should be pretty easy to use, add the dep and start a server like:

```kotlin
fun main(args: Array<String>) {
    val config = CommandLineConfig(args)
    EmbeddedServer(config.applicationProperties, Jetty12) { takeFrom(config.engineConfig) }.start(true)
}
```

This package includes the machinery for integrating Jetty 12 with Ktor and a thread-per-coroutine dispatcher that
dispatches on virtual threads. This lets you do lots of weird looking but fun stuff like using `runBlocking {}` without
blocking OS threads and using libs that make use of `ThreadLocal`.

## Known limitations

- Built on Ktor `3.0.0-beta1`. This makes it incompatible with `2.x.x` and also doesn't support the new kotlinx IO libs
  that are going to used in Kotlin 4.
- This seems to work for most HTTP 1.1 requests that I've tried ~but the HTTPX/websocket support almost certainly
  doesn't
  work~ and HTTP3 and websockets seem to be working.
- The vthread dispatcher works but I have no idea if it's a good idea.
- Java 21+ only (of course).
- I suspect there are problems in how concurrency is structured in the websockets logic.

## Benchmarks

Tested by running a minimal Ktor server in a VM and a client on the same box but separate VM. Each run lasted for 10
seconds and involved 100 clients firing HTTP 1.1 GET requests to the server as fast as possible. The first row hit an
endpoint that calls delay(100), the second row hit an endpoint that calls Thread.sleep(100) and the third row hit an
endpoint that calls a triply nested loop and does some maths to simulate a CPU-bound workload. The axis on the left is
latency and the axis on the right is throughput.

Performance is similar when calling the suspending and CPU-bound endpoints but a clear latency and throughput advantage
for the Jetty 12-based engine when calling an endpoint that calls blocking code. I haven't got any charts for mem use
but I've had a look with JMC and they look very similar.

![Jetty vs Jetty 12 engine benchmark results](https://github.com/henryjcee/ktor-jetty-12-poc/blob/main/assets/latency_benchmark_results.png?raw=true)

## Changelog

### 0.0.2

- Added a new dispatcher implementation that sends the termination token to the vthread task queue allowing it to be
  cleaned up.
- Few other minor tweaks

### 0.0.3

- No content response support
- Implemented byte-array response support
-

### 0.0.4

- Websockets support (needs work)
- HTTP3 support

### 0.1.0

- Switched dispatcher to unbounded task queue instead of a rendezvous. Not sure why I did that to start with as it (
  obviously) leads to deadlocks
- Added `loomAsync {}` to go with `loomLaunch {}`
- TLS is working (see Ktor docs for config)
- Websockets support has been worked on and I've got it running over http and https

## Contributing

Please do contribute and I'm aiming to be responsive to any PRs as I am already addicted to calling blocking APIs
from coroutines (feels so wrong but so right) and don't want to wait for official support.

## Get in touch

- henry dot course at gmail
