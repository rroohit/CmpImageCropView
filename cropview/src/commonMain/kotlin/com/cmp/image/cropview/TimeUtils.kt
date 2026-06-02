package com.cmp.image.cropview

import kotlin.time.TimeSource

internal fun getCurrentTimeMillis(): Long =
    TimeSource.Monotonic.markNow().elapsedNow().inWholeMilliseconds