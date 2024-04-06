package com.henrycourse.jetty

import com.henrycourse.coroutines.LoomDispatcher
import com.henrycourse.coroutines.launchLoomChannel
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.PipelineCall
import io.ktor.server.engine.BaseApplicationResponse
import io.ktor.server.response.ApplicationSendPipeline
import io.ktor.server.response.ResponseHeaders
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.ReaderJob
import io.ktor.utils.io.close
import io.ktor.utils.io.pool.ByteBufferPool
import kotlinx.coroutines.CoroutineScope
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Response
import org.eclipse.jetty.util.Callback
import java.nio.ByteBuffer
import kotlin.coroutines.CoroutineContext

internal val bufferPool = ByteBufferPool()
internal val emptyBuffer = ByteBuffer.allocate(0)

class Jetty12ApplicationResponse(
    call: PipelineCall,
    private val request: Request,
    private val response: Response,
    private val managedByEngineHeaders: Set<String>,
    override val coroutineContext: CoroutineContext
) : BaseApplicationResponse(call), CoroutineScope {

    init {
        pipeline.intercept(ApplicationSendPipeline.Engine) {
            if (responseJob.isInitialized()) {
                responseJob.value.apply {
                    channel.close()
                    join()
                }
            }
        }
    }

    override val headers: ResponseHeaders = object : ResponseHeaders() {
        override val managedByEngineHeaders = this@Jetty12ApplicationResponse.managedByEngineHeaders

        override fun engineAppendHeader(name: String, value: String) {
            response.headers.add(name, value)
        }

        override fun getEngineHeaderNames(): List<String> = response.headers.fieldNamesCollection.toList()
        override fun getEngineHeaderValues(name: String): List<String> = response.headers.getValuesList(name)
    }

    override suspend fun respondUpgrade(upgrade: OutgoingContent.ProtocolUpgrade) {

        val connection = request.connectionMetaData.connection
        val endpoint = connection.endPoint
        endpoint.idleTimeout = 6000 * 1000

        val websocketConnection = Jetty12WebsocketConnection(endpoint, coroutineContext)
        response.write(true, emptyBuffer, Callback.from { endpoint.upgrade(websocketConnection) })

//        TODO: Check what needs to happen with contexts here
        val upgradeJob = upgrade.upgrade(
            websocketConnection.inputChannel,
            websocketConnection.outputChannel,
            LoomDispatcher(),
            coroutineContext,
        )

        upgradeJob.invokeOnCompletion {
            websocketConnection.inputChannel.close()
            websocketConnection.outputChannel.close()
        }

        upgradeJob.join()
    }

    private val responseJob: Lazy<ReaderJob> = lazy {

        launchLoomChannel {

            val buffer = bufferPool.borrow()

            while (channel.readAvailable(buffer) > -1) {
                response.write(false, buffer.flip(), Callback.from { buffer.rewind() })
            }

            response.write(true, emptyBuffer, Callback.from { bufferPool.recycle(buffer) })
        }
    }

    override suspend fun respondFromBytes(bytes: ByteArray) {

        val buffer = bufferPool.borrow()

        response.write(true, buffer.put(bytes).flip(), Callback.from {
            bufferPool.recycle(buffer)
        })
    }

    override suspend fun respondNoContent(content: OutgoingContent.NoContent) {

        response.write(true, emptyBuffer, Callback.NOOP)
    }

    private val responseChannel = lazy { responseJob.value.channel }

    override suspend fun responseChannel(): ByteWriteChannel = responseChannel.value

    override fun setStatus(statusCode: HttpStatusCode) {
        response.status = statusCode.value
    }
}
