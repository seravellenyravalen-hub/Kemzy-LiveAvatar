package com.kemzy.liveavatar

import java.util.concurrent.ArrayBlockingQueue

/** Bounded latest-item queue: live processing never grows an unbounded backlog. */
class FramePipeline<T>(capacity: Int = 2) {
    private val queue = ArrayBlockingQueue<T>(capacity.coerceAtLeast(1))

    @Synchronized
    fun offer(frame: T) {
        if (!queue.offer(frame)) {
            queue.poll()
            queue.offer(frame)
        }
    }

    fun poll(): T? = queue.poll()
    fun clear() = queue.clear()
}
