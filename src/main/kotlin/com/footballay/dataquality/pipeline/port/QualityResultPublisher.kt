package com.footballay.dataquality.pipeline.port

import com.footballay.dataquality.pipeline.result.QualityResult

/** 저장된 품질 결과의 best-effort 발행을 요청하며, 발행 실패를 호출자에게 전파하지 않는다. */
fun interface QualityResultPublisher {
    fun publish(result: QualityResult)
}
