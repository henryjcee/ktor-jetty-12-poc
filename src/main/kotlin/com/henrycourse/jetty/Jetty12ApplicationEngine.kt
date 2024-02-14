package com.henrycourse.jetty

import io.ktor.events.Events
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationEnvironment

class Jetty12ApplicationEngine(
    environment: ApplicationEnvironment,
    monitor: Events,
    developmentMode: Boolean,
    configuration: Configuration,
    private val applicationProvider: () -> Application
) : Jetty12ApplicationEngineBase(environment, monitor, developmentMode, configuration, applicationProvider) {

    override fun start(wait: Boolean): Jetty12ApplicationEngine {

        server.handler = Jetty12Handler(environment, pipeline, applicationProvider)
        super.start(wait)
        return this
    }
}
