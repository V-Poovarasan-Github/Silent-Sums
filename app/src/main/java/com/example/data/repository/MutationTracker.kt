package com.example.data.repository

import java.util.concurrent.atomic.AtomicLong

object MutationTracker {
    private val _mutationStamp = AtomicLong(System.currentTimeMillis())

    val currentStamp: Long
        get() = _mutationStamp.get()

    fun bump(): Long {
        return _mutationStamp.incrementAndGet()
    }

    fun initFrom(stamp: Long) {
        _mutationStamp.updateAndGet { current -> if (stamp > current) stamp else current }
    }
}
