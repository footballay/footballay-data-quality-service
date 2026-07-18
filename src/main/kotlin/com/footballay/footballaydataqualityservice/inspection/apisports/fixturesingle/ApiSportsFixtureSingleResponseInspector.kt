package com.footballay.footballaydataqualityservice.inspection.apisports.fixturesingle

import com.footballay.footballaydataqualityservice.inspection.InspectionEvidence
import com.footballay.footballaydataqualityservice.inspection.FoundIssue
import com.footballay.footballaydataqualityservice.inspection.InspectionCode
import com.footballay.footballaydataqualityservice.inspection.InspectionSeverity
import com.footballay.footballaydataqualityservice.inspection.QualityInspector

/** API Sports Fixture Single 최상위 응답 구조를 검사한다. */
class ApiSportsFixtureSingleResponseInspector : QualityInspector<ApiSportsFixtureSingleRootDto> {
    override fun inspect(target: ApiSportsFixtureSingleRootDto): List<FoundIssue> {
        val root = target
        val findings = mutableListOf<FoundIssue>()

        when {
            root.get == null ->
                findings += issue(
                    ApiSportsFixtureSingleInspectionCodes.GET_FIELD_NULL,
                    InspectionSeverity.WARN,
                    "Response get field is null",
                    "root",
                    "$.get",
                    evidence("actualGet" to null),
                )
            root.get.isBlank() ->
                findings += issue(
                    ApiSportsFixtureSingleInspectionCodes.GET_FIELD_BLANK,
                    InspectionSeverity.WARN,
                    "Response get field is blank",
                    "root",
                    "$.get",
                    evidence("actualGet" to root.get),
                )
            root.get != "fixtures" ->
                findings += issue(
                    ApiSportsFixtureSingleInspectionCodes.GET_FIELD_UNEXPECTED,
                    InspectionSeverity.ERROR,
                    "Response get field is not fixtures",
                    "root",
                    "$.get",
                    evidence("actualGet" to root.get),
                )
        }

        if (root.parameters == null) {
            findings +=
                issue(
                        ApiSportsFixtureSingleInspectionCodes.PARAMETERS_NULL,
                    InspectionSeverity.WARN,
                    "Response parameters are null",
                    "root",
                    "$.parameters",
                )
        } else {
            if (root.parameters.id != null && root.parameters.id.toLongOrNull() == null) {
                findings +=
                    issue(
                        ApiSportsFixtureSingleInspectionCodes.PARAMETER_FIXTURE_ID_INVALID,
                        InspectionSeverity.WARN,
                        "Response fixture ID parameter is invalid",
                        "root",
                        "$.parameters.id",
                        evidence("actualFixtureId" to root.parameters.id),
                    )
            }
        }

        when {
            root.errors == null ->
                findings +=
                    issue(
                        ApiSportsFixtureSingleInspectionCodes.ERRORS_NULL,
                        InspectionSeverity.WARN,
                        "Response errors are null",
                        "root",
                        "$.errors",
                    )
            hasErrors(root.errors) ->
                findings +=
                    issue(
                        ApiSportsFixtureSingleInspectionCodes.ERRORS_PRESENT,
                        InspectionSeverity.ERROR,
                        "Response contains errors",
                        "root",
                        "$.errors",
                    )
        }

        when {
            root.results == null ->
                findings +=
                    issue(
                        ApiSportsFixtureSingleInspectionCodes.RESULTS_NULL,
                        InspectionSeverity.ERROR,
                        "Response results are null",
                        "root",
                        "$.results",
                    )
            root.results < 0 ->
                findings +=
                    issue(
                        ApiSportsFixtureSingleInspectionCodes.RESULTS_NEGATIVE,
                        InspectionSeverity.ERROR,
                        "Response results are negative",
                        "root",
                        "$.results",
                        evidence("actualResults" to root.results.toString()),
                    )
        }

        val paging = root.paging
        if (paging == null) {
            findings +=
                issue(
                    ApiSportsFixtureSingleInspectionCodes.PAGING_NULL,
                    InspectionSeverity.INFO,
                    "Response paging is null",
                    "root",
                    "$.paging",
                )
        } else {
            if (paging.current != null && paging.current < 1) {
                findings +=
                    issue(
                        ApiSportsFixtureSingleInspectionCodes.PAGING_CURRENT_INVALID,
                        InspectionSeverity.WARN,
                        "Response paging current is invalid",
                        "root",
                        "$.paging.current",
                        evidence("actualCurrent" to paging.current.toString()),
                    )
            }
            if (paging.total != null && paging.total < 1) {
                findings +=
                    issue(
                        ApiSportsFixtureSingleInspectionCodes.PAGING_TOTAL_INVALID,
                        InspectionSeverity.WARN,
                        "Response paging total is invalid",
                        "root",
                        "$.paging.total",
                        evidence("actualTotal" to paging.total.toString()),
                    )
            }
        }

        val response = root.response
        if (response == null) {
            findings +=
                issue(
                        ApiSportsFixtureSingleInspectionCodes.RESPONSE_NULL,
                    InspectionSeverity.CRITICAL,
                    "Response array is null",
                    "root",
                    "$.response",
                )
        } else {
            if (root.results != null && root.results != response.size) {
                findings +=
                    issue(
                        ApiSportsFixtureSingleInspectionCodes.RESULTS_RESPONSE_SIZE_MISMATCH,
                        InspectionSeverity.ERROR,
                        "Response results do not match response size",
                        "root",
                        "$.response",
                        evidence("results" to root.results.toString(), "responseSize" to response.size.toString()),
                    )
            }
            if (root.results != null && root.results != 1) {
                findings +=
                    issue(
                        ApiSportsFixtureSingleInspectionCodes.SINGLE_FIXTURE_RESULTS_NOT_ONE,
                        InspectionSeverity.ERROR,
                        "Single fixture response results are not one",
                        "root",
                        "$.results",
                        evidence("actualResults" to root.results.toString()),
                    )
            }
            if (response.isEmpty()) {
                findings +=
                    issue(
                        ApiSportsFixtureSingleInspectionCodes.RESPONSE_EMPTY,
                        InspectionSeverity.ERROR,
                        "Single fixture response is empty",
                        "root",
                        "$.response",
                    )
            }
            if (response.size > 1) {
                findings +=
                    issue(
                        ApiSportsFixtureSingleInspectionCodes.RESPONSE_MULTIPLE_ITEMS,
                        InspectionSeverity.ERROR,
                        "Single fixture response has multiple items",
                        "root",
                        "$.response",
                    )
            }
            response.forEachIndexed { index, item ->
                if (item == null) {
                    findings +=
                        issue(
                            ApiSportsFixtureSingleInspectionCodes.RESPONSE_ITEM_NULL,
                            InspectionSeverity.CRITICAL,
                            "Response item is null",
                            "root",
                            "$.response[$index]",
                            evidence("responseIndex" to index.toString()),
                        )
                }
            }
        }

        return findings
    }

    private fun issue(
        code: InspectionCode,
        severity: InspectionSeverity,
        title: String,
        section: String,
        path: String,
        evidence: InspectionEvidence? = null,
    ): FoundIssue = fixtureSingleFoundIssue(code, severity, title, section, path, evidence)

    private fun hasErrors(errors: com.fasterxml.jackson.databind.JsonNode): Boolean =
        !errors.isContainerNode || errors.size() > 0

    private fun evidence(vararg entries: Pair<String, String?>): InspectionEvidence = fixtureSingleEvidence(*entries)
}
