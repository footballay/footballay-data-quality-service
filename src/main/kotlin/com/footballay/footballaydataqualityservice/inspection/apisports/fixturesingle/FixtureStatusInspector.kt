package com.footballay.footballaydataqualityservice.inspection.apisports.fixturesingle

import com.footballay.footballaydataqualityservice.inspection.FoundIssue
import com.footballay.footballaydataqualityservice.inspection.InspectionEvidence
import com.footballay.footballaydataqualityservice.inspection.InspectionRuleCode
import com.footballay.footballaydataqualityservice.inspection.InspectionSeverity
import com.footballay.footballaydataqualityservice.inspection.InspectionName
import com.footballay.footballaydataqualityservice.inspection.QualityInspector
import com.footballay.footballaydataqualityservice.inspection.QualityIssueType

/** API Sports Fixture Single fixture status의 기본 무결성을 검사한다. */
class FixtureStatusInspector : QualityInspector<ApiSportsFixtureSingleRootDto> {
    override val inspectionName = InspectionName.of("FIXTURE_STATUS")

    override fun inspect(target: ApiSportsFixtureSingleRootDto): List<FoundIssue> {
        val root = target
        val findings = mutableListOf<FoundIssue>()
        root.response.orEmpty().forEachIndexed { index, response ->
            val fixture = response?.fixture ?: return@forEachIndexed
            val path = "$.response[$index].fixture.status"
            val status = fixture.status
            if (status == null) {
                findings +=
                    issue(
                        Code.OBJECT_NULL,
                        InspectionSeverity.CRITICAL,
                        "Fixture status is null",
                        path,
                        evidence("fixtureId" to fixture.id?.toString()),
                    )
                return@forEachIndexed
            }
            when {
                status.long == null ->
                    findings +=
                        issue(
                            Code.LONG_NULL,
                            InspectionSeverity.ERROR,
                            "Fixture status long is null",
                            "$path.long",
                            evidence("statusLong" to null),
                        )
                status.long.isBlank() ->
                    findings +=
                        issue(
                            Code.LONG_BLANK,
                            InspectionSeverity.ERROR,
                            "Fixture status long is blank",
                            "$path.long",
                            evidence("statusLong" to status.long),
                        )
            }
            when {
                status.short == null ->
                    findings +=
                        issue(
                            Code.SHORT_NULL,
                            InspectionSeverity.ERROR,
                            "Fixture status short is null",
                            "$path.short",
                            evidence("statusShort" to null),
                        )
                status.short.isBlank() ->
                    findings +=
                        issue(
                            Code.SHORT_BLANK,
                            InspectionSeverity.ERROR,
                            "Fixture status short is blank",
                            "$path.short",
                            evidence("statusShort" to status.short),
                        )
            }
            if (status.elapsed != null && status.elapsed < 0) {
                findings +=
                    issue(
                        Code.ELAPSED_NEGATIVE,
                        InspectionSeverity.WARN,
                        "Fixture status elapsed is negative",
                        "$path.elapsed",
                        evidence("elapsed" to status.elapsed.toString()),
                    )
            }
            if (status.extra != null && status.extra < 0) {
                findings +=
                    issue(
                        Code.EXTRA_NEGATIVE,
                        InspectionSeverity.ERROR,
                        "Fixture status extra is negative",
                        "$path.extra",
                        evidence("extra" to status.extra.toString()),
                    )
            }
            if (status.extra != null && status.elapsed == null) {
                findings +=
                    issue(
                        Code.EXTRA_WITHOUT_ELAPSED,
                        InspectionSeverity.WARN,
                        "Fixture status extra has no elapsed",
                        "$path.extra",
                        evidence("extra" to status.extra.toString(), "elapsed" to null),
                    )
            }
        }
        return findings
    }

    private fun issue(
        code: Code,
        severity: InspectionSeverity,
        title: String,
        path: String,
        evidence: InspectionEvidence? = null,
    ): FoundIssue = fixtureSingleFoundIssue(code.suggestedType, inspectionName, code, severity, title, "fixture.status", path, evidence)

    private fun evidence(vararg entries: Pair<String, String?>): InspectionEvidence = fixtureSingleEvidence(*entries)

    private enum class Code(
        val suggestedType: QualityIssueType,
    ) : InspectionRuleCode {
        OBJECT_NULL(QualityIssueType.UNEXPECTED_NULL),
        LONG_NULL(QualityIssueType.UNEXPECTED_NULL),
        LONG_BLANK(QualityIssueType.UNEXPECTED_BLANK),
        SHORT_NULL(QualityIssueType.UNEXPECTED_NULL),
        SHORT_BLANK(QualityIssueType.UNEXPECTED_BLANK),
        ELAPSED_NEGATIVE(QualityIssueType.OUT_OF_RANGE),
        EXTRA_NEGATIVE(QualityIssueType.OUT_OF_RANGE),
        EXTRA_WITHOUT_ELAPSED(QualityIssueType.INCONSISTENT_INFORMATION),
        ;

        override val value: String
            get() = name
    }
}
