package com.footballay.footballaydataqualityservice.pipeline

import java.time.Instant

data class QualityResult(
    val resultId: String,
    val rawEventId: String,
    val provider: DataProvider,
    val endpointKey: String,
    val parameters: List<QualityParameter>,
    val canonicalHash: String,
    val rawJsonObjectKey: String,
    val checkedAt: Instant,
    val scannerVersion: String,
    val issues: List<QualityIssue>,
    val hasIssue: Boolean,
    val issueCount: Int,
    val maxSeverity: QualityMaxSeverity,
    val checkStatus: QualityCheckStatus,
)

enum class QualityMaxSeverity {
    NONE,
    INFO,
    WARN,
    ERROR,
    CRITICAL,
}

enum class QualityCheckStatus {
    NO_ISSUE,
    NEED_CHECK,
    PARTIALLY_CHECKED,
    ALL_CHECKED,
    CHECK_HOLD,
}
