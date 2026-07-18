package com.footballay.footballaydataqualityservice.pipeline.result

/** 검사 결과를 저장 가능한 QualityIssue로 완성하기 전에 전달하는 쓰기 입력이다. */
data class QualityIssueWrite(
    val suggestedTypeCode: String,
    val severity: QualityIssueSeverity,
    val title: String,
    val responseLocation: QualityResponseLocation,
    val evidence: QualityIssueEvidence? = null,
)
