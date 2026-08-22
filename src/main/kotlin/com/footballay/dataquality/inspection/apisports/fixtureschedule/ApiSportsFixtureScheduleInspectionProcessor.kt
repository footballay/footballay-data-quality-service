package com.footballay.dataquality.inspection.apisports.fixtureschedule

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.footballay.dataquality.inspection.FoundIssue
import com.footballay.dataquality.inspection.InspectionEvidence
import com.footballay.dataquality.inspection.InspectionName
import com.footballay.dataquality.inspection.InspectionResponseLocation
import com.footballay.dataquality.inspection.InspectionRuleCode
import com.footballay.dataquality.inspection.InspectionSeverity
import com.footballay.dataquality.inspection.QualityInspectionProcessor
import com.footballay.dataquality.inspection.QualityInspector
import com.footballay.dataquality.inspection.QualityIssueType
import com.footballay.dataquality.pipeline.input.DataProvider
import com.footballay.dataquality.pipeline.input.QualityCheckRequest

/** API Sports 리그 fixture schedule 원본 응답을 검사한다. */
class ApiSportsFixtureScheduleInspectionProcessor(
    private val objectMapper: ObjectMapper,
) : QualityInspectionProcessor {
    private val rawMappingInspectionName = InspectionName.of("RAW_MAPPING")

    override fun inspectors(): List<QualityInspector<*>> = emptyList()

    override fun inspectionNames(): List<InspectionName> =
        super<QualityInspectionProcessor>.inspectionNames() + rawMappingInspectionName

    override fun supports(
        provider: DataProvider,
        endpointKey: String,
    ): Boolean = provider == DataProvider.API_SPORTS && endpointKey == "fixturesOfLeague"

    override fun inspect(
        request: QualityCheckRequest,
        rawJson: JsonNode,
    ): List<FoundIssue> {
        if (rawJson.isNull) return listOf(rootNull())

        try {
            objectMapper.treeToValue(rawJson, ApiSportsFixtureScheduleRootDto::class.java)
                ?: return listOf(mappingFailed())
        } catch (exception: JsonProcessingException) {
            return listOf(mappingFailed(exception))
        }

        return emptyList()
    }

    private fun rootNull(): FoundIssue =
        FoundIssue(
            suggestedType = QualityIssueType.UNEXPECTED_NULL,
            inspectionName = rawMappingInspectionName,
            inspectionRuleCode = ProcessorCode.ROOT_NULL,
            severity = InspectionSeverity.CRITICAL,
            title = "Root response is null",
            responseLocation = InspectionResponseLocation("root", "$"),
        )

    private fun mappingFailed(exception: Exception? = null): FoundIssue =
        FoundIssue(
            suggestedType = QualityIssueType.UPSTREAM_ERROR,
            inspectionName = rawMappingInspectionName,
            inspectionRuleCode = ProcessorCode.MAPPING_FAILED,
            severity = InspectionSeverity.CRITICAL,
            title = "Raw JSON cannot be mapped to API Sports Fixture Schedule DTO",
            responseLocation = InspectionResponseLocation("root", "$"),
            evidence = exception?.let { InspectionEvidence.of("exceptionType" to it.javaClass.simpleName, "message" to it.message) },
        )

    private enum class ProcessorCode : InspectionRuleCode {
        ROOT_NULL,
        MAPPING_FAILED,
        ;

        override val value: String
            get() = name
    }
}
