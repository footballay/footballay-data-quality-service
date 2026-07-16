package com.footballay.footballaydataqualityservice.pipeline.port

import com.footballay.footballaydataqualityservice.pipeline.result.QualityResult

fun interface QualityResultPublisher {
    fun publish(result: QualityResult)
}
