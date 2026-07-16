package com.footballay.footballaydataqualityservice.pipeline.port

import com.footballay.footballaydataqualityservice.pipeline.result.QualityResult

fun interface QualityResultStore {
    fun save(result: QualityResult): QualityResult
}

class NoopQualityResultStore : QualityResultStore {
    override fun save(result: QualityResult): QualityResult = result
}
