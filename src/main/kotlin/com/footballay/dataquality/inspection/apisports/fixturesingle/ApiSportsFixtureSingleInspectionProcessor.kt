package com.footballay.dataquality.inspection.apisports.fixturesingle

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.footballay.dataquality.inspection.FoundIssue
import com.footballay.dataquality.inspection.InspectionEvidence
import com.footballay.dataquality.inspection.InspectionResponseLocation
import com.footballay.dataquality.inspection.InspectionRuleCode
import com.footballay.dataquality.inspection.InspectionName
import com.footballay.dataquality.inspection.InspectionSeverity
import com.footballay.dataquality.inspection.QualityInspectionProcessor
import com.footballay.dataquality.inspection.QualityInspector
import com.footballay.dataquality.inspection.QualityIssueType
import com.footballay.dataquality.logger
import com.footballay.dataquality.pipeline.input.DataProvider
import com.footballay.dataquality.pipeline.input.QualityCheckRequest

/** API Sports Fixture Single 응답을 관측 DTO로 변환하고 전용 Inspector를 실행한다. */
class ApiSportsFixtureSingleInspectionProcessor(
    private val objectMapper: ObjectMapper,
    private val responseInspectors: List<QualityInspector<ApiSportsFixtureSingleRootDto>>,
    private val requestResponseInspectors: List<QualityInspector<FixtureSingleRequestResponse>>,
) : QualityInspectionProcessor {
    private val log = logger()
    private val rawMappingInspectionName = InspectionName.of("RAW_MAPPING")

    override fun inspectors(): List<QualityInspector<*>> = responseInspectors + requestResponseInspectors

    override fun inspectionNames(): List<InspectionName> =
        super<QualityInspectionProcessor>.inspectionNames() + rawMappingInspectionName

    override fun supports(
        provider: DataProvider,
        endpointKey: String,
    ): Boolean = provider == DataProvider.API_SPORTS && endpointKey == "fixtureSingle"

    override fun inspect(
        request: QualityCheckRequest,
        rawJson: JsonNode,
    ): List<FoundIssue> {
        if (rawJson.isNull) {
            log.warn("API Sports Fixture Single response root is null. provider={}, endpointKey={}", request.provider, request.endpointKey)
            return listOf(rootResponseNull())
        }

        val root =
            try {
                objectMapper.treeToValue(rawJson, ApiSportsFixtureSingleRootDto::class.java)
            } catch (exception: JsonProcessingException) {
                return logAndReturnFailureWhenJsonException(request, exception)
            } ?: run {
                return logAndReturnFailureWhenMappingReturnNull(request)
            }

        val responseIssues = collectIssues(responseInspectors, root)
        val requestResponseTarget = createReqRespTarget(request, root)
        val requestResponseIssues = collectIssues(requestResponseInspectors, requestResponseTarget)

        return responseIssues + requestResponseIssues
    }

    private fun logAndReturnFailureWhenMappingReturnNull(request: QualityCheckRequest): List<FoundIssue> {
        log.error(
            "API Sports Fixture Single response mapping returned null. provider={}, endpointKey={}",
            request.provider,
            request.endpointKey,
        )
        return listOf(mappingFailed())
    }

    private fun logAndReturnFailureWhenJsonException(
        request: QualityCheckRequest,
        exception: JsonProcessingException,
    ): List<FoundIssue> {
        log.error(
            "Cannot map API Sports Fixture Single response. provider={}, endpointKey={}",
            request.provider,
            request.endpointKey,
            exception,
        )
        return listOf(mappingFailed(exception))
    }

    private fun createReqRespTarget(
        request: QualityCheckRequest,
        root: ApiSportsFixtureSingleRootDto,
    ): FixtureSingleRequestResponse =
        FixtureSingleRequestResponse(
            request =
                FixtureSingleRequest(
                    fixtureId = request.parameters[FIXTURE_ID_PARAMETER]?.singleOrNull()?.toLongOrNull(),
                ),
            response = root,
        )

    private fun <T> collectIssues(
        inspectors: List<QualityInspector<T>>,
        target: T,
    ): List<FoundIssue> =
        inspectors.flatMap { inspector ->
            val foundIssues = inspector.inspect(target)
            foundIssues.forEach { foundIssue ->
                check(foundIssue.inspectionName == inspector.inspectionName) {
                    "Inspector returned an issue with a mismatched inspection name. " +
                        "inspectorName=${inspector.inspectionName.value}, " +
                        "foundIssueName=${foundIssue.inspectionName.value}, " +
                        "inspectionRuleCode=${foundIssue.inspectionRuleCode.value}"
                }
            }
            foundIssues
        }

    private fun rootResponseNull(): FoundIssue =
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
            title = "Raw JSON cannot be mapped to API Sports Fixture Single DTO",
            responseLocation = InspectionResponseLocation("root", "$"),
            evidence =
                exception?.let {
                    InspectionEvidence.of(
                        "exceptionType" to it.javaClass.simpleName,
                        "message" to it.message,
                    )
                },
        )

    private companion object {
        const val FIXTURE_ID_PARAMETER = "fixtureId"
    }

    private enum class ProcessorCode : InspectionRuleCode {
        ROOT_NULL,
        MAPPING_FAILED,
        ;

        override val value: String
            get() = name
    }
}
