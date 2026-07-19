package com.footballay.dataquality.pipeline.port

import com.footballay.dataquality.pipeline.result.QualityResult

fun interface QualityResultPublisher {
    fun publish(result: QualityResult)
}
