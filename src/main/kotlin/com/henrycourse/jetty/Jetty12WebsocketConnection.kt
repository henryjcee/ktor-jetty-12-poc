package com.henrycourse.jetty

import com.henrycourse.coroutines.loomLaunch
import io.ktor.util.cio.ChannelWriteException
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.close
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.eclipse.jetty.io.AbstractConnection
import org.eclipse.jetty.io.EndPoint
import org.eclipse.jetty.util.Callback
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


class Jetty12WebsocketConnection(
    private val endpoint: EndPoint,
    override val coroutineContext: CoroutineContext
) : AbstractConnection(endpoint, Executors.newVirtualThreadPerTaskExecutor()),
    org.eclipse.jetty.io.Connection.UpgradeTo, CoroutineScope {

    private val inputBuffer = bufferPool.borrow().flip()
    private val outputBuffer = bufferPool.borrow()

    val inputChannel = ByteChannel(true)
    val outputChannel = ByteChannel(false)

    init {

        fillInterested()

        loomLaunch {

            while (true) {

                if (outputChannel.isClosedForRead) {
                    return@loomLaunch
                }
                val outputBytes = outputChannel.readAvailable(outputBuffer.rewind())

                if (outputBytes < 0 || !endpoint.isOpen) {
                    outputChannel.close()
                    bufferPool.recycle(outputBuffer)
                    return@loomLaunch
                } else {
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
                }
            }
        }
    }

    override fun onFillInterestedFailed(cause: Throwable) {
        endpoint.close()
        inputChannel.close()
        bufferPool.recycle(inputBuffer)
    }

    override fun onFillable() {

        if (endpoint.fill(inputBuffer.rewind().flip()) > -1) {
            runBlocking {
                inputChannel.writeFully(inputBuffer)
            }
            try {
                fillInterested()
            } catch (e: Throwable) {
                onFillInterestedFailed(e)
            }
        } else {
            endpoint.close()
        }
    }

    override fun onUpgradeTo(buffer: ByteBuffer?) = TODO()
}
