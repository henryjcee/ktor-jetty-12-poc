package com.henrycourse.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import java.util.concurrent.ArrayBlockingQueue
import kotlin.coroutines.CoroutineContext

// N.B. this is not a singleton unlike most dispatchers. It feels pretty dumb but it seems to work
val LoomDispatcher: CoroutineDispatcher get() = LoomDispatcherImpl()

internal class LoomDispatcherImpl : CoroutineDispatcher() {

    private val taskQueue = ArrayBlockingQueue<Runnable>(1)

    init {
        Thread.ofVirtual().start {
            while (true) {
                taskQueue.take().run()
            }
        }
    }

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        taskQueue.put(block)
    }
}
