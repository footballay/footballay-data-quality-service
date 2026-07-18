package com.footballay.footballaydataqualityservice.inspection

interface QualityInspector<T> {
    val inspectionScope: InspectionScope

    fun inspect(target: T): List<FoundIssue>
}

/** Inspector가 대상 데이터에서 발견한 품질 문제를 표현한다. */
data class FoundIssue(
    val inspectionScope: InspectionScope,
    val inspectionCode: InspectionCode,
    val severity: InspectionSeverity,
    val title: String,
    val responseLocation: InspectionResponseLocation,
    val evidence: InspectionEvidence? = null,
)

data class InspectionResponseLocation(
    val section: String,
    val path: String,
)

data class InspectionEvidence(
    val values: Map<String, String?>,
) {
    companion object {
        private const val MAX_VALUE_COUNT = 8
        private const val MAX_VALUE_LENGTH = 256

        fun of(vararg entries: Pair<String, String?>): InspectionEvidence =
            InspectionEvidence(
                values = entries.take(MAX_VALUE_COUNT).associate { (key, value) -> key to value?.take(MAX_VALUE_LENGTH) },
            )
    }
}

enum class InspectionSeverity {
    INFO,
    WARN,
    ERROR,
    CRITICAL,
}
