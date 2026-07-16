package com.footballay.footballaydataqualityservice.inspection.apisports.fixturesingle

import com.footballay.footballaydataqualityservice.inspection.InspectionEvidence
import com.footballay.footballaydataqualityservice.inspection.InspectionFinding
import com.footballay.footballaydataqualityservice.inspection.InspectionSeverity
import com.footballay.footballaydataqualityservice.inspection.QualityInspector

/** API Sports Fixture Single 최상위 응답 구조를 검사한다. */
class ApiSportsFixtureSingleResponseInspector : QualityInspector<ApiSportsFixtureSingleRootDto> {
    override fun inspect(target: ApiSportsFixtureSingleRootDto): List<InspectionFinding> {
        val root = target
        val issues = mutableListOf<InspectionFinding>()

        when {
            root.get == null -> issues += issue("GET_FIELD_NULL", InspectionSeverity.WARN, "Response get field is null", "root", "$.get", evidence("actualGet" to null))
            root.get.isBlank() -> issues += issue("GET_FIELD_BLANK", InspectionSeverity.WARN, "Response get field is blank", "root", "$.get", evidence("actualGet" to root.get))
            root.get != "fixtures" -> issues += issue("GET_FIELD_UNEXPECTED", InspectionSeverity.ERROR, "Response get field is not fixtures", "root", "$.get", evidence("actualGet" to root.get))
        }

        if (root.parameters == null) {
            issues += issue("PARAMETERS_NULL", InspectionSeverity.WARN, "Response parameters are null", "root", "$.parameters")
        } else {
            if (root.parameters.id != null && root.parameters.id.toLongOrNull() == null) {
                issues += issue("PARAMETER_FIXTURE_ID_INVALID", InspectionSeverity.WARN, "Response fixture ID parameter is invalid", "root", "$.parameters.id", evidence("actualFixtureId" to root.parameters.id))
            }
        }

        when {
            root.errors == null -> issues += issue("ERRORS_NULL", InspectionSeverity.WARN, "Response errors are null", "root", "$.errors")
            hasErrors(root.errors) -> issues += issue("ERRORS_PRESENT", InspectionSeverity.ERROR, "Response contains errors", "root", "$.errors")
        }

        when {
            root.results == null -> issues += issue("RESULTS_NULL", InspectionSeverity.ERROR, "Response results are null", "root", "$.results")
            root.results < 0 -> issues += issue("RESULTS_NEGATIVE", InspectionSeverity.ERROR, "Response results are negative", "root", "$.results", evidence("actualResults" to root.results.toString()))
        }

        val paging = root.paging
        if (paging == null) {
            issues += issue("PAGING_NULL", InspectionSeverity.INFO, "Response paging is null", "root", "$.paging")
        } else {
            if (paging.current != null && paging.current < 1) {
                issues += issue("PAGING_CURRENT_INVALID", InspectionSeverity.WARN, "Response paging current is invalid", "root", "$.paging.current", evidence("actualCurrent" to paging.current.toString()))
            }
            if (paging.total != null && paging.total < 1) {
                issues += issue("PAGING_TOTAL_INVALID", InspectionSeverity.WARN, "Response paging total is invalid", "root", "$.paging.total", evidence("actualTotal" to paging.total.toString()))
            }
        }

        val response = root.response
        if (response == null) {
            issues += issue("RESPONSE_NULL", InspectionSeverity.CRITICAL, "Response array is null", "root", "$.response")
        } else {
            if (root.results != null && root.results != response.size) {
                issues += issue("RESULTS_RESPONSE_SIZE_MISMATCH", InspectionSeverity.ERROR, "Response results do not match response size", "root", "$.response", evidence("results" to root.results.toString(), "responseSize" to response.size.toString()))
            }
            if (root.results != null && root.results != 1) {
                issues += issue("SINGLE_FIXTURE_RESULTS_NOT_ONE", InspectionSeverity.ERROR, "Single fixture response results are not one", "root", "$.results", evidence("actualResults" to root.results.toString()))
            }
            if (response.isEmpty()) {
                issues += issue("RESPONSE_EMPTY", InspectionSeverity.ERROR, "Single fixture response is empty", "root", "$.response")
            }
            if (response.size > 1) {
                issues += issue("RESPONSE_MULTIPLE_ITEMS", InspectionSeverity.ERROR, "Single fixture response has multiple items", "root", "$.response")
            }
            response.forEachIndexed { index, item ->
                if (item == null) {
                    issues += issue("RESPONSE_ITEM_NULL", InspectionSeverity.CRITICAL, "Response item is null", "root", "$.response[$index]", evidence("responseIndex" to index.toString()))
                }
            }
        }

        return issues
    }

    private fun issue(
        code: String,
        severity: InspectionSeverity,
        title: String,
        section: String,
        path: String,
        evidence: InspectionEvidence? = null,
    ): InspectionFinding = fixtureSingleFinding(code, severity, title, section, path, evidence)

    private fun hasErrors(errors: com.fasterxml.jackson.databind.JsonNode): Boolean = !errors.isContainerNode || errors.size() > 0

    private fun evidence(vararg entries: Pair<String, String?>): InspectionEvidence = fixtureSingleEvidence(*entries)
}
