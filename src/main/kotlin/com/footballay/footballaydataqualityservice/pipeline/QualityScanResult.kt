package com.footballay.footballaydataqualityservice.pipeline

data class QualityScanResult(
    val scannerVersion: String,
    val issues: List<QualityIssue> = emptyList(),
)

data class QualityIssue(
    val suggestedTypeCode: String,
    val severity: QualityIssueSeverity,
    val title: String,
    val section: String,
    val path: String,
)

enum class QualityIssueSeverity {
    INFO,
    WARN,
    ERROR,
    CRITICAL,
}
