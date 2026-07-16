package com.footballay.footballaydataqualityservice.pipeline

import com.footballay.footballaydataqualityservice.inspection.InspectionFinding
import java.time.Instant

data class QualityScanResult(
    val scannerVersion: String,
    val findings: List<InspectionFinding> = emptyList(),
)

data class QualityIssue(
    val issueInstanceId: String,
    val suggestedTypeCode: String,
    val confirmedTypeCode: String? = null,
    val checkStatus: QualityIssueCheckStatus,
    val severity: QualityIssueSeverity,
    val title: String,
    val responseLocation: QualityResponseLocation,
    val evidence: QualityIssueEvidence? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
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
