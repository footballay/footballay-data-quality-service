package com.footballay.footballaydataqualityservice.inspection.apisports.fixturesingle

import com.footballay.footballaydataqualityservice.inspection.FoundIssue
import com.footballay.footballaydataqualityservice.inspection.InspectionEvidence
import com.footballay.footballaydataqualityservice.inspection.InspectionRuleCode
import com.footballay.footballaydataqualityservice.inspection.InspectionSeverity
import com.footballay.footballaydataqualityservice.inspection.InspectionName
import com.footballay.footballaydataqualityservice.inspection.QualityInspector
import com.footballay.footballaydataqualityservice.inspection.QualityIssueType

/** API Sports Fixture Single 최상위 응답 구조를 검사한다. */
class ApiSportsFixtureSingleResponseInspector : QualityInspector<ApiSportsFixtureSingleRootDto> {
    override val inspectionName = InspectionName.of("ROOT")

    override fun inspect(target: ApiSportsFixtureSingleRootDto): List<FoundIssue> {
        val root = target
        val findings = mutableListOf<FoundIssue>()

        when {
            root.get == null ->
                findings += issue(
                    Code.GET_FIELD_NULL,
                    InspectionSeverity.WARN,
                    "Response get field is null",
                    "root",
                    "$.get",
                    evidence("actualGet" to null),
                )
            root.get.isBlank() ->
                findings += issue(
                    Code.GET_FIELD_BLANK,
                    InspectionSeverity.WARN,
                    "Response get field is blank",
                    "root",
                    "$.get",
                    evidence("actualGet" to root.get),
                )
            root.get != "fixtures" ->
                findings += issue(
                    Code.GET_FIELD_UNEXPECTED,
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
                        Code.PARAMETERS_NULL,
                    InspectionSeverity.WARN,
                    "Response parameters are null",
                    "root",
                    "$.parameters",
                )
        } else {
            if (root.parameters.id != null && root.parameters.id.toLongOrNull() == null) {
                findings +=
                    issue(
                        Code.PARAMETER_ID_INVALID,
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
                        Code.ERRORS_NULL,
                        InspectionSeverity.WARN,
                        "Response errors are null",
                        "root",
                        "$.errors",
                    )
            hasErrors(root.errors) ->
                findings +=
                    issue(
                        Code.ERRORS_PRESENT,
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
                        Code.RESULTS_NULL,
                        InspectionSeverity.ERROR,
                        "Response results are null",
                        "root",
                        "$.results",
                    )
            root.results < 0 ->
                findings +=
                    issue(
                        Code.RESULTS_NEGATIVE,
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
                    Code.PAGING_NULL,
                    InspectionSeverity.INFO,
                    "Response paging is null",
                    "root",
                    "$.paging",
                )
        } else {
            if (paging.current != null && paging.current < 1) {
                findings +=
                    issue(
                        Code.PAGING_CURRENT_INVALID,
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
                        Code.PAGING_TOTAL_INVALID,
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
                        Code.RESPONSE_NULL,
                    InspectionSeverity.CRITICAL,
                    "Response array is null",
                    "root",
                    "$.response",
                )
        } else {
            if (root.results != null && root.results != response.size) {
                findings +=
                    issue(
                        Code.RESULTS_RESPONSE_SIZE_MISMATCH,
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
                        Code.SINGLE_FIXTURE_RESULTS_NOT_ONE,
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
                        Code.RESPONSE_EMPTY,
                        InspectionSeverity.ERROR,
                        "Single fixture response is empty",
                        "root",
                        "$.response",
                    )
            }
            if (response.size > 1) {
                findings +=
                    issue(
                        Code.RESPONSE_MULTIPLE_ITEMS,
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
                            Code.RESPONSE_ITEM_NULL,
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
        code: Code,
        severity: InspectionSeverity,
        title: String,
        section: String,
        path: String,
        evidence: InspectionEvidence? = null,
    ): FoundIssue = fixtureSingleFoundIssue(code.suggestedType, inspectionName, code, severity, title, section, path, evidence)

    private fun hasErrors(errors: com.fasterxml.jackson.databind.JsonNode): Boolean =
        !errors.isContainerNode || errors.size() > 0

    private fun evidence(vararg entries: Pair<String, String?>): InspectionEvidence = fixtureSingleEvidence(*entries)

    private enum class Code(
        val suggestedType: QualityIssueType,
    ) : InspectionRuleCode {
        GET_FIELD_NULL(QualityIssueType.UNEXPECTED_NULL),
        GET_FIELD_BLANK(QualityIssueType.UNEXPECTED_BLANK),
        GET_FIELD_UNEXPECTED(QualityIssueType.UNEXPECTED_RESPONSE),
        PARAMETERS_NULL(QualityIssueType.UNEXPECTED_NULL),
        PARAMETER_ID_INVALID(QualityIssueType.INVALID_FORMAT),
        ERRORS_NULL(QualityIssueType.UNEXPECTED_NULL),
        ERRORS_PRESENT(QualityIssueType.UPSTREAM_ERROR),
        RESULTS_NULL(QualityIssueType.UNEXPECTED_NULL),
        RESULTS_NEGATIVE(QualityIssueType.OUT_OF_RANGE),
        PAGING_NULL(QualityIssueType.UNEXPECTED_NULL),
        PAGING_CURRENT_INVALID(QualityIssueType.OUT_OF_RANGE),
        PAGING_TOTAL_INVALID(QualityIssueType.OUT_OF_RANGE),
        RESPONSE_NULL(QualityIssueType.UNEXPECTED_NULL),
        RESULTS_RESPONSE_SIZE_MISMATCH(QualityIssueType.INCONSISTENT_INFORMATION),
        SINGLE_FIXTURE_RESULTS_NOT_ONE(QualityIssueType.UNEXPECTED_RESPONSE),
        RESPONSE_EMPTY(QualityIssueType.UNEXPECTED_RESPONSE),
        RESPONSE_MULTIPLE_ITEMS(QualityIssueType.UNEXPECTED_RESPONSE),
        RESPONSE_ITEM_NULL(QualityIssueType.UNEXPECTED_NULL),
        ;

        override val value: String
            get() = name
    }
}
