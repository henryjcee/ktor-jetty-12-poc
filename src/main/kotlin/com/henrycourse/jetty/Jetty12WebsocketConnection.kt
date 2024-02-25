package com.henrycourse.jetty

import com.henrycourse.coroutines.loomLaunch
import io.ktor.util.cio.ChannelWriteException
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.close
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import org.eclipse.jetty.io.AbstractConnection
import org.eclipse.jetty.io.EndPoint
import org.eclipse.jetty.util.Callback
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// TODO: Needs sensible error handling
class Jetty12WebsocketConnection(
    private val endpoint: EndPoint,
    override val coroutineContext: CoroutineContext
) : AbstractConnection(endpoint, Executors.newVirtualThreadPerTaskExecutor()),
    org.eclipse.jetty.io.Connection.UpgradeTo, CoroutineScope {

    private val inputBuffer = bufferPool.borrow().flip()
    private val outputBuffer = bufferPool.borrow()

    val inputChannel = ByteChannel(true)
    val outputChannel = ByteChannel(false)

    private val channel = Channel<Boolean>(Channel.RENDEZVOUS)

    init {

//        Input job
        loomLaunch {

//            TODO: Handle errors
            while (true) {

                fillInterested()
                channel.receive()

                inputBuffer.clear().flip()

                val read = endpoint.fill(inputBuffer)

                if (read > 0) {
                    inputChannel.writeFully(inputBuffer)
                } else if (read == -1) {
                    endpoint.close()
                }
            }
        }

//        Output job
        loomLaunch {

//            TODO: Handle errors
            while (true) {

                if (outputChannel.isClosedForRead) {
                    return@loomLaunch
                }
                val outputBytes = outputChannel.readAvailable(outputBuffer.rewind())

                if (outputBytes > -1) {
                    suspendCancellableCoroutine<Unit> {
                        endpoint.write(
                            object : Callback {
                                override fun succeeded() {
                                    it.resume(Unit)
                                }
                                override fun failed(cause: Throwable) {
                                    it.resumeWithException(ChannelWriteException(exception = cause))
                                }
                            },
                            outputBuffer.flip()
                        )
                    }
                } else {
                    outputChannel.close()
                    bufferPool.recycle(outputBuffer)
                    return@loomLaunch
                }
            }
        }
    }

//    TODO: Handle errors
    override fun onFillInterestedFailed(cause: Throwable) {
        throw cause
    }

    override fun onFillable() {
        channel.trySend(true)
    }

    override fun onUpgradeTo(buffer: ByteBuffer?) = TODO()
}
