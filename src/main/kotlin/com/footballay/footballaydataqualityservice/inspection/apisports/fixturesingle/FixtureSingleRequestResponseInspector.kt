package com.footballay.footballaydataqualityservice.inspection.apisports.fixturesingle

import com.footballay.footballaydataqualityservice.inspection.FoundIssue
import com.footballay.footballaydataqualityservice.inspection.InspectionRuleCode
import com.footballay.footballaydataqualityservice.inspection.InspectionSeverity
import com.footballay.footballaydataqualityservice.inspection.InspectionName
import com.footballay.footballaydataqualityservice.inspection.QualityInspector
import com.footballay.footballaydataqualityservice.inspection.QualityIssueType

/** API Sports Fixture Single 요청과 응답 식별자의 정합성을 검사한다. */
class FixtureSingleRequestResponseInspector : QualityInspector<FixtureSingleRequestResponse> {
    override val inspectionName = InspectionName.of("REQUEST_RESPONSE")

    override fun inspect(target: FixtureSingleRequestResponse): List<FoundIssue> {
        val requestedFixtureId = target.request.fixtureId ?: return emptyList()
        val findings = mutableListOf<FoundIssue>()

        if (target.response.parameters != null && target.response.parameters.id == null) {
            findings +=
                fixtureSingleFoundIssue(
                    suggestedType = Code.PARAMETER_ID_MISSING.suggestedType,
                    inspectionName = inspectionName,
                    inspectionRuleCode = Code.PARAMETER_ID_MISSING,
                    severity = InspectionSeverity.WARN,
                    title = "Response parameters do not contain fixture ID",
                    section = "root",
                    path = "$.parameters.id",
                    evidence = fixtureSingleEvidence("requestedFixtureId" to requestedFixtureId.toString()),
                )
        }

        val responseParameterFixtureId = target.response.parameters?.id?.toLongOrNull()
        if (responseParameterFixtureId != null && responseParameterFixtureId != requestedFixtureId) {
            findings +=
                fixtureSingleFoundIssue(
                    suggestedType = Code.PARAMETER_ID_MISMATCH.suggestedType,
                    inspectionName = inspectionName,
                    inspectionRuleCode = Code.PARAMETER_ID_MISMATCH,
                    severity = InspectionSeverity.ERROR,
                    title = "Response parameter fixture ID does not match request",
                    section = "root",
                    path = "$.parameters.id",
                    evidence =
                        fixtureSingleEvidence(
                            "requestedFixtureId" to requestedFixtureId.toString(),
                            "responseParameterFixtureId" to responseParameterFixtureId.toString(),
                        ),
                )
        }

        target.response.response.orEmpty().forEachIndexed { index, response ->
            val fixtureId = response?.fixture?.id ?: return@forEachIndexed
            if (fixtureId > 0 && fixtureId != requestedFixtureId) {
                findings +=
                    fixtureSingleFoundIssue(
                        suggestedType = Code.FIXTURE_ID_MISMATCH.suggestedType,
                        inspectionName = inspectionName,
                        inspectionRuleCode = Code.FIXTURE_ID_MISMATCH,
                        severity = InspectionSeverity.ERROR,
                        title = "Fixture ID does not match request",
                        section = "fixture",
                        path = "$.response[$index].fixture.id",
                        evidence =
                            fixtureSingleEvidence(
                                "requestedFixtureId" to requestedFixtureId.toString(),
                                "actualFixtureId" to fixtureId.toString(),
                            ),
                    )
            }
        }

        return findings
    }

    private enum class Code(
        val suggestedType: QualityIssueType,
    ) : InspectionRuleCode {
        PARAMETER_ID_MISSING(QualityIssueType.UNEXPECTED_NULL),
        PARAMETER_ID_MISMATCH(QualityIssueType.INCONSISTENT_INFORMATION),
        FIXTURE_ID_MISMATCH(QualityIssueType.INCONSISTENT_INFORMATION),
        ;

        override val value: String
            get() = name
    }
}
