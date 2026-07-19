package com.footballay.dataquality.pipeline.result

import com.footballay.dataquality.inspection.QualityIssueType
import java.time.Instant

data class QualityIssue(
    val issueInstanceId: String, // Issue별 Unique ID
    val suggestedTypeCode: QualityIssueType, // Inspector에 의해서 제안됨
    /**
     * provider, endpoint, Inspector, 규칙에서 문제가 발견된 곳을 추적하기 위한 안정적인 식별 힌트다.
     * 로그, 관리자 화면, 검색, 집계에 사용하며, `:`로 분해해 구조를 복원하는 직렬화 포맷이 아니다.
     * 정확한 원본 위치는 [responseLocation]이 담당한다.
     */
    val inspectionCode: String,
    val confirmedTypeCode: QualityIssueType? = null, // Admin에 의해 기록됨
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
