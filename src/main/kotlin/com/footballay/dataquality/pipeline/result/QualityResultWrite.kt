package com.footballay.dataquality.pipeline.result

import com.footballay.dataquality.pipeline.input.DataProvider
import com.footballay.dataquality.pipeline.input.QualityParameter
import java.time.Instant

/** Store가 식별자와 요약 정보를 완성하기 전에 전달받는 QualityResult 쓰기 입력이다. */
data class QualityResultWrite(
    val rawEventId: String,
    val provider: DataProvider,
    val endpointKey: String,
    val parameters: List<QualityParameter>,
    val canonicalHash: String,
    val rawJsonObjectKey: String,
    val collectedAt: Instant,
    val checkedAt: Instant,
    val inspectionVersion: Int,
    val issues: List<QualityIssueWrite>,
)
