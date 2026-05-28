package com.barteqcz.loqa.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackEvents @Inject constructor() {
    private val _requestNext = MutableSharedFlow<Unit>(extraBufferCapacity = 10)
    val requestNext = _requestNext.asSharedFlow()

    private val _requestPrevious = MutableSharedFlow<Unit>(extraBufferCapacity = 10)
    val requestPrevious = _requestPrevious.asSharedFlow()

    fun emitNext() {
        _requestNext.tryEmit(Unit)
    }

    fun emitPrevious() {
        _requestPrevious.tryEmit(Unit)
    }
}
