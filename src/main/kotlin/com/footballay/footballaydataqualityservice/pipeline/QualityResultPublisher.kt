package com.footballay.footballaydataqualityservice.pipeline

fun interface QualityResultPublisher {
    fun publish(result: QualityResult)
}
