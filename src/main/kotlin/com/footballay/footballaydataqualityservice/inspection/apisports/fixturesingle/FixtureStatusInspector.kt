package com.footballay.footballaydataqualityservice.inspection.apisports.fixturesingle

import com.footballay.footballaydataqualityservice.inspection.FoundIssue
import com.footballay.footballaydataqualityservice.inspection.InspectionCode
import com.footballay.footballaydataqualityservice.inspection.InspectionEvidence
import com.footballay.footballaydataqualityservice.inspection.InspectionSeverity
import com.footballay.footballaydataqualityservice.inspection.QualityInspector

/** API Sports Fixture Single fixture status의 기본 무결성을 검사한다. */
class FixtureStatusInspector : QualityInspector<ApiSportsFixtureSingleRootDto> {
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
                        ApiSportsFixtureSingleInspectionCodes.STATUS_OBJECT_NULL,
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
                            ApiSportsFixtureSingleInspectionCodes.STATUS_LONG_NULL,
                            InspectionSeverity.ERROR,
                            "Fixture status long is null",
                            "$path.long",
                            evidence("statusLong" to null),
                        )
                status.long.isBlank() ->
                    findings +=
                        issue(
                            ApiSportsFixtureSingleInspectionCodes.STATUS_LONG_BLANK,
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
                            ApiSportsFixtureSingleInspectionCodes.STATUS_SHORT_NULL,
                            InspectionSeverity.ERROR,
                            "Fixture status short is null",
                            "$path.short",
                            evidence("statusShort" to null),
                        )
                status.short.isBlank() ->
                    findings +=
                        issue(
                            ApiSportsFixtureSingleInspectionCodes.STATUS_SHORT_BLANK,
                            InspectionSeverity.ERROR,
                            "Fixture status short is blank",
                            "$path.short",
                            evidence("statusShort" to status.short),
                        )
            }
            if (status.elapsed != null && status.elapsed < 0) {
                findings +=
                    issue(
                        ApiSportsFixtureSingleInspectionCodes.STATUS_ELAPSED_NEGATIVE,
                        InspectionSeverity.WARN,
                        "Fixture status elapsed is negative",
                        "$path.elapsed",
                        evidence("elapsed" to status.elapsed.toString()),
                    )
            }
            if (status.extra != null && status.extra < 0) {
                findings +=
                    issue(
                        ApiSportsFixtureSingleInspectionCodes.STATUS_EXTRA_NEGATIVE,
                        InspectionSeverity.ERROR,
                        "Fixture status extra is negative",
                        "$path.extra",
                        evidence("extra" to status.extra.toString()),
                    )
            }
            if (status.extra != null && status.elapsed == null) {
                findings +=
                    issue(
                        ApiSportsFixtureSingleInspectionCodes.STATUS_EXTRA_WITHOUT_ELAPSED,
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
        code: InspectionCode,
        severity: InspectionSeverity,
        title: String,
        path: String,
        evidence: InspectionEvidence? = null,
    ): FoundIssue = fixtureSingleFoundIssue(code, severity, title, "fixture.status", path, evidence)

    private fun evidence(vararg entries: Pair<String, String?>): InspectionEvidence = fixtureSingleEvidence(*entries)
}
