package com.henrycourse.jetty

import com.henrycourse.coroutines.LoomDispatcher
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
import io.ktor.utils.io.reader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.eclipse.jetty.server.Response
import org.eclipse.jetty.util.Callback
import java.nio.ByteBuffer
import kotlin.coroutines.CoroutineContext

internal val bufferPool = ByteBufferPool()
internal val emptyBuffer = ByteBuffer.allocate(0)

class Jetty12ApplicationResponse(
    call: PipelineCall,
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
                return@intercept
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

    override suspend fun respondUpgrade(upgrade: OutgoingContent.ProtocolUpgrade) = TODO("Not yet implemented")

    private val responseJob: Lazy<ReaderJob> = lazy {

        reader(LoomDispatcher, false) {

            val buffer = bufferPool.borrow()

//            This looks weird but with vthreads you can block coroutine threads
            fun writeRecursive() {

                runBlocking {

                    buffer.rewind()
                    if (channel.readAvailable(buffer) > 0) {
                        response.write(false, buffer.flip(), Callback.from { writeRecursive() })
                    } else {
                        response.write(true, emptyBuffer, Callback.from { bufferPool.recycle(buffer) })
                    }
                }
            }

            writeRecursive()
        }
    }

    private val responseChannel = lazy { responseJob.value.channel }

    override suspend fun responseChannel(): ByteWriteChannel =  responseChannel.value

    override fun setStatus(statusCode: HttpStatusCode) {
        response.status = statusCode.value
    }
}
