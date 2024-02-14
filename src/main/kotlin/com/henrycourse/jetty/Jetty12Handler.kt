package com.henrycourse.jetty

import com.henrycourse.coroutines.LoomDispatcher
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationEnvironment
import io.ktor.server.engine.DefaultUncaughtExceptionHandler
import io.ktor.server.engine.EnginePipeline
import io.ktor.server.engine.logError
import io.ktor.util.cio.ChannelIOException
import io.ktor.util.pipeline.execute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.eclipse.jetty.http.HttpStatus
import org.eclipse.jetty.server.Handler
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Response
import org.eclipse.jetty.util.Callback
import java.util.concurrent.CancellationException
import kotlin.coroutines.CoroutineContext

internal class Jetty12Handler(
    private val environment: ApplicationEnvironment,
    private val pipeline: EnginePipeline,
    private val applicationProvider: () -> Application
) : Handler.Abstract(), CoroutineScope {

    private val handlerJob = SupervisorJob(applicationProvider().parentCoroutineContext[Job])

    override val coroutineContext: CoroutineContext
        get() =
        applicationProvider().parentCoroutineContext +
                handlerJob +
                DefaultUncaughtExceptionHandler(environment.log) +
                LoomDispatcher

    override fun destroy() {
        try {
            super.destroy()
        } finally {
            handlerJob.cancel()
        }
    }

    override fun handle(
        request: Request,
        response: Response,
        callback: Callback,
    ): Boolean {
        try {

            launch(coroutineContext) {

                val call = Jetty12ApplicationCall(applicationProvider(), request, response, coroutineContext)

                try {
                    pipeline.execute(call)
                    callback.succeeded()
                } catch (cancelled: CancellationException) {
                    Response.writeError(request, response, callback, HttpStatus.GONE_410, cancelled.message, cancelled)
                } catch (channelFailed: ChannelIOException) {
                    Response.writeError(
                        request,
                        response,
                        callback,
                        HttpStatus.INTERNAL_SERVER_ERROR_500,
                        channelFailed.message,
                        channelFailed
                    )
                } catch (error: Throwable) {
                    logError(call, error)
                    Response.writeError(
                        request,
                        response,
                        callback,
                        HttpStatus.INTERNAL_SERVER_ERROR_500,
                        error.message,
                        error
                    )
                }
            }
        } catch (ex: Throwable) {
            environment.log.error("Application cannot fulfill the request", ex)
            Response.writeError(request, response, callback, HttpStatus.INTERNAL_SERVER_ERROR_500, ex.message, ex)
        }

        return true
    }
}
