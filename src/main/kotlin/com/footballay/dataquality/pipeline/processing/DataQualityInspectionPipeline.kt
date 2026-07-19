package com.footballay.dataquality.pipeline.processing

import com.footballay.dataquality.pipeline.input.RawCollectedEvent

/** 수집된 raw event의 데이터 품질 검사를 시작하는 파이프라인 계약이다. */
fun interface DataQualityInspectionPipeline {
    fun process(event: RawCollectedEvent)
}
