# Ktor Jetty 12 POC

I've spent long enough faffing around with reactive database drivers etc. and inspired
by [this issue](https://youtrack.jetbrains.com/issue/KTOR-6734/Jetty-engine-Upgrade-Jetty-dependencies-to-the-latest-version-12)
I have decided to build a POC that demonstrates virtual thread support in Ktor using Jetty 12. Most of this is code
adapted from the existing Ktor Jetty engine but I've completely dropped servlet support which should be the fastest way
to run Jetty.

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
- This seems to work for most HTTP 1.1 requests that I've tried but the HTTPX/websocket support almost certainly doesn't
  work.
- The vthread dispatcher works but I have no idea if it's a good idea.
- Java 21+ only
- `initializeServer()` is a mess

## Changelog

### 0.0.2

- Added a new dispatcher implementation that sends the termination token to the vthread task queue allowing it to be
  cleaned up.
- Few other minor tweaks

## Contributing

Please do contribute and I'm aiming to be responsive to any PRs as I am already addicted to calling blocking APIs
from coroutines (feels so wrong but so right) and don't want to wait for official support.

## Get in touch

- henry dot course at gmail
