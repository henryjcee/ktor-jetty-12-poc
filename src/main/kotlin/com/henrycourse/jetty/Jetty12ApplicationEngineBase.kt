package com.henrycourse.jetty

import io.ktor.events.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import kotlinx.coroutines.*
import org.eclipse.jetty.server.*
import org.eclipse.jetty.util.thread.QueuedThreadPool
import java.util.concurrent.Executors
import kotlin.time.*
import kotlin.time.Duration.Companion.seconds

/**
 * [ApplicationEngine] base type for running in a standalone Jetty
 */
open class Jetty12ApplicationEngineBase(
    environment: ApplicationEnvironment,
    monitor: Events,
    developmentMode: Boolean,
    private val configuration: Configuration,
    private val applicationProvider: () -> Application
) : BaseApplicationEngine(environment, monitor, developmentMode) {

    /**
     * Jetty-specific engine configuration
     */
    class Configuration : BaseApplicationEngine.Configuration() {
        /**
         * Property function that will be called during Jetty server initialization
         * with the server instance as receiver.
         */
        var configureServer: Server.() -> Unit = {}

        /**
         * The duration of time that a connection can be idle before the connector takes action to close the connection.
         */
        var idleTimeout: Duration = 30.seconds
    }

    private var cancellationDeferred: CompletableJob? = null

    /**
     * Jetty server instance being configuring and starting
     */
    protected val server: Server = Server().apply {
        configuration.configureServer(this)
        initializeServer(configuration)
    }

    override fun start(wait: Boolean): Jetty12ApplicationEngineBase {
        addShutdownHook(monitor) {
            stop(configuration.shutdownGracePeriod, configuration.shutdownTimeout)
        }

        server.start()
        cancellationDeferred = stopServerOnCancellation(
            applicationProvider(),
            configuration.shutdownGracePeriod,
            configuration.shutdownTimeout
        )

        val connectors = server.connectors.zip(configuration.connectors)
            .map { it.second.withPort((it.first as ServerConnector).localPort) }
        resolvedConnectors.complete(connectors)

        monitor.raiseCatching(ServerReady, environment, environment.log)

        if (wait) {
            server.join()
            stop(configuration.shutdownGracePeriod, configuration.shutdownTimeout)
        }
        return this
    }

    override fun stop(gracePeriodMillis: Long, timeoutMillis: Long) {
        cancellationDeferred?.complete()
        monitor.raise(ApplicationStopPreparing, environment)
        server.stopTimeout = timeoutMillis
        server.stop()
        server.destroy()
    }

    override fun toString(): String {
        return "Jetty($environment)"
    }
}
