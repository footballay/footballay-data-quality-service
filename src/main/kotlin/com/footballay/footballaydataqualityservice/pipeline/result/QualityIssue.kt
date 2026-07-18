package com.footballay.footballaydataqualityservice.pipeline.result

import java.time.Instant

data class QualityIssue(
    val issueInstanceId: String, // Issue별 Unique ID
    val suggestedTypeCode: String, // Inspector에 의해서 제안됨
    val confirmedTypeCode: String? = null, // Admin에 의해 기록됨
    val checkStatus: QualityIssueCheckStatus, // Store에서 초기화되며 Admin에 의해 수정됨
    val severity: QualityIssueSeverity, // Inspector에 의해서 제안되면 Admin에 의해 수정 가능
    val title: String, // Inspector 제안 / Admin 수정 가능
    val responseLocation: QualityResponseLocation, // Inspector 제안
    val evidence: QualityIssueEvidence? = null, // Inspector 제안
    val createdAt: Instant, // MongoDB audit
    val updatedAt: Instant, // MongoDB audit
)

data class QualityIssueEvidence(
    val values: Map<String, String?>,
)

data class QualityResponseLocation(
    val section: String,
    val path: String,
)

enum class QualityIssueCheckStatus {
    NEED_CHECK,
    ALL_CHECKED,
    CHECK_HOLD,
}

enum class QualityIssueSeverity {
    INFO,
    WARN,
    ERROR,
    CRITICAL,
}
