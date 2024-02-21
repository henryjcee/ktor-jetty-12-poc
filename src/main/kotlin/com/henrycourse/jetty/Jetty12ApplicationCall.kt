package com.henrycourse.jetty


import io.ktor.server.application.Application
import io.ktor.server.engine.BaseApplicationCall
import io.ktor.server.engine.BaseApplicationRequest
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Response
import kotlin.coroutines.CoroutineContext

class Jetty12ApplicationCall(
    application: Application,
    request: Request,
    response: Response,
    coroutineContext: CoroutineContext,
    managedByEngineHeaders: Set<String> = emptySet(),
) : BaseApplicationCall(application) {

    override val request: BaseApplicationRequest = Jetty12ApplicationRequest(this, request)
    override val response: Jetty12ApplicationResponse = Jetty12ApplicationResponse(this, request, response, managedByEngineHeaders, coroutineContext)

    init {
        putResponseAttribute()
    }
}
