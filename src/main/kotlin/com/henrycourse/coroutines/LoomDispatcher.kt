package com.henrycourse.coroutines

import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ReaderJob
import io.ktor.utils.io.ReaderScope
import io.ktor.utils.io.WriterJob
import io.ktor.utils.io.WriterScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.util.concurrent.LinkedBlockingQueue
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

// Would be nice to get these working with launch {} / async {} but I've already spent too long on this
fun CoroutineScope.loomLaunch(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit
): Job {
    val dispatcher = LoomDispatcher()
    return launch(context + dispatcher, start, block).apply {
        invokeOnCompletion {
            dispatcher.close()
        }
    }
}

fun <T> CoroutineScope.loomAsync(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> T
): Deferred<T> {
    val dispatcher = LoomDispatcher()
    return async(context + dispatcher, start, block).apply {
        dispatcher.close()
    }
}

internal val closeJob: Runnable = Runnable {}

class LoomDispatcher : CoroutineDispatcher() {

    private val taskQueue = LinkedBlockingQueue<Runnable>()

    init {
        Thread.ofVirtual().start {
            while (true) {
                val task = taskQueue.take()
                if (task == closeJob) {
                    return@start
                } else {
                    task.run()
                }
            }
        }
    }

    internal fun close() {
        taskQueue.put(closeJob)
    }

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        taskQueue.put(block)
    }
}

// Replicas of the stuff from ktor-server-jetty
class LoomChannelJob(
    private val delegate: Job,
    override val channel: ByteChannel
) : ReaderJob, WriterJob, Job by delegate

class LoomChannelScope(
    delegate: CoroutineScope,
    override val channel: ByteChannel
) : ReaderScope, WriterScope, CoroutineScope by delegate

fun CoroutineScope.launchLoomChannel(
    context: CoroutineContext = EmptyCoroutineContext,
    channel: ByteChannel = ByteChannel(true),
    block: suspend LoomChannelScope.() -> Unit
): LoomChannelJob {

    val job = loomLaunch(context) {

        val scope = LoomChannelScope(this, channel)

        try {
            block(scope)
        } catch (cause: Throwable) {
            channel.cancel(cause)
        }
    }

    job.invokeOnCompletion { cause ->
        channel.close(cause)
    }

    return LoomChannelJob(job, channel)
}
