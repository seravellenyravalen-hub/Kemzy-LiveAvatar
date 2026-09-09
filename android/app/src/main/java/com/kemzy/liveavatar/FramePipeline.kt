package com.kemzy.liveavatar

import java.util.concurrent.ArrayBlockingQueue

/** Bounded latest-frame queue: live preview never grows an unbounded backlog. */
class FramePipeline(capacity: Int = 2) {
    private val queue = ArrayBlockingQueue<Frame>(capacity.coerceAtLeast(1))

    @Synchronized
    fun offer(frame: Frame) {
        if (!queue.offer(frame)) {
            queue.poll()
            queue.offer(frame)
        }
    }

    fun poll(): Frame? = queue.poll()
    fun clear() = queue.clear()
}
