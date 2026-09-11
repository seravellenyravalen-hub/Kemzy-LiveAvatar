package com.kemzy.liveavatar

import java.util.concurrent.ArrayBlockingQueue

/** Bounded latest-frame queue: live preview never grows an unbounded backlog. */
class FramePipeline(capacity: Int = 2) {
    private val queue = ArrayBlockingQueue<Frame>(capacity.coerceAtLeast(1))

    @Synchronized
    fun offer(frame: Frame) {
        if (!queue.offer(frame)) {
            queue.poll()?.bitmap?.recycle()
            queue.offer(frame)
        }
    }

    fun poll(): Frame? = queue.poll()

    @Synchronized
    fun clear() {
        while (true) {
            val frame = queue.poll() ?: break
            frame.bitmap.recycle()
        }
    }
}
