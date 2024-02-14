package com.henrycourse.jetty

import io.ktor.events.Events
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationEnvironment
import io.ktor.server.engine.ApplicationEngineFactory

/**
 * An [ApplicationEngineFactory] providing a Jetty 12-based [ApplicationEngine]
 */
object Jetty12 : ApplicationEngineFactory<Jetty12ApplicationEngine, Jetty12ApplicationEngineBase.Configuration> {

    override fun configuration(
        configure: Jetty12ApplicationEngineBase.Configuration.() -> Unit
    ): Jetty12ApplicationEngineBase.Configuration {
        return Jetty12ApplicationEngineBase.Configuration().apply(configure)
    }

    override fun create(
        environment: ApplicationEnvironment,
        monitor: Events,
        developmentMode: Boolean,
        configuration: Jetty12ApplicationEngineBase.Configuration,
        applicationProvider: () -> Application
    ): Jetty12ApplicationEngine {
        return Jetty12ApplicationEngine(environment, monitor, developmentMode, configuration, applicationProvider)
    }
}

