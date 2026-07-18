package com.footballay.footballaydataqualityservice.inspection.apisports.fixturesingle

import com.footballay.footballaydataqualityservice.inspection.FoundIssue
import com.footballay.footballaydataqualityservice.inspection.InspectionCode
import com.footballay.footballaydataqualityservice.inspection.InspectionSeverity
import com.footballay.footballaydataqualityservice.inspection.InspectionScope
import com.footballay.footballaydataqualityservice.inspection.QualityInspector

/** API Sports Fixture Single 요청과 응답 식별자의 정합성을 검사한다. */
class FixtureSingleRequestResponseInspector : QualityInspector<FixtureSingleRequestResponse> {
    override val inspectionScope: InspectionScope = FixtureSingleInspectionScope.REQUEST_RESPONSE

    override fun inspect(target: FixtureSingleRequestResponse): List<FoundIssue> {
        val requestedFixtureId = target.request.fixtureId ?: return emptyList()
        val findings = mutableListOf<FoundIssue>()

        if (target.response.parameters != null && target.response.parameters.id == null) {
            findings +=
                fixtureSingleFoundIssue(
                    inspectionScope = inspectionScope,
                    inspectionCode = Code.PARAMETER_ID_MISSING,
                    severity = InspectionSeverity.WARN,
                    title = "Response parameters do not contain fixture ID",
                    section = "root",
                    path = "$.parameters.id",
                    evidence = fixtureSingleEvidence("requestedFixtureId" to requestedFixtureId.toString()),
                )
        }

        target.response.response.orEmpty().forEachIndexed { index, response ->
            val fixtureId = response?.fixture?.id ?: return@forEachIndexed
            if (fixtureId > 0 && fixtureId != requestedFixtureId) {
                findings +=
                    fixtureSingleFoundIssue(
                        inspectionScope = inspectionScope,
                        inspectionCode = Code.FIXTURE_ID_MISMATCH,
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

    private enum class Code : InspectionCode {
        PARAMETER_ID_MISSING,
        FIXTURE_ID_MISMATCH,
        ;

        override val value: String
            get() = name
    }
}
