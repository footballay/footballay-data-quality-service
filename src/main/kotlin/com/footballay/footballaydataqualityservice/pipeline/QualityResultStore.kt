package com.footballay.footballaydataqualityservice.pipeline

fun interface QualityResultStore {
    fun save(result: QualityResult): QualityResult
}

class NoopQualityResultStore : QualityResultStore {
    override fun save(result: QualityResult): QualityResult = result
}
