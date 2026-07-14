package com.footballay.footballaydataqualityservice.pipeline

import java.time.Instant

data class QualityResultEvent(
    val resultId: String,
    val rawEventId: String,
    val source: QualityResultSource,
    val hasIssue: Boolean,
    val issueCount: Int,
    val maxSeverity: QualityMaxSeverity,
    val checkedAt: Instant,
    val rawJsonObjectKey: String,
)

data class QualityResultSource(
    val provider: String,
    val endpointKey: String,
    val parameters: List<QualityParameter>,
)

fun QualityResult.toEvent(): QualityResultEvent =
    QualityResultEvent(
        resultId = resultId,
        rawEventId = rawEventId,
        source = QualityResultSource(provider, endpointKey, parameters),
        hasIssue = hasIssue,
        issueCount = issueCount,
        maxSeverity = maxSeverity,
        checkedAt = checkedAt,
        rawJsonObjectKey = rawJsonObjectKey,
    )
