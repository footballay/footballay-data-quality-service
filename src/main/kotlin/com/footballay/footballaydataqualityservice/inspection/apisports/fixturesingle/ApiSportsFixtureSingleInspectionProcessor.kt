package com.footballay.footballaydataqualityservice.inspection.apisports.fixturesingle

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.footballay.footballaydataqualityservice.inspection.InspectionEvidence
import com.footballay.footballaydataqualityservice.inspection.InspectionFinding
import com.footballay.footballaydataqualityservice.inspection.InspectionResponseLocation
import com.footballay.footballaydataqualityservice.inspection.InspectionSeverity
import com.footballay.footballaydataqualityservice.inspection.QualityInspectionProcessor
import com.footballay.footballaydataqualityservice.inspection.QualityInspector
import com.footballay.footballaydataqualityservice.logger
import com.footballay.footballaydataqualityservice.pipeline.DataProvider
import com.footballay.footballaydataqualityservice.pipeline.QualityCheckRequest
import com.footballay.footballaydataqualityservice.pipeline.QualityScanResult

/** API Sports Fixture Single 응답을 관측 DTO로 변환하고 전용 Inspector를 실행한다. */
class ApiSportsFixtureSingleInspectionProcessor(
    private val objectMapper: ObjectMapper,
    private val responseInspectors: List<QualityInspector<ApiSportsFixtureSingleRootDto>>,
    private val requestResponseInspectors: List<QualityInspector<FixtureSingleRequestResponse>>,
) : QualityInspectionProcessor {
    private val log = logger()

    override fun supports(
        provider: DataProvider,
        endpointKey: String,
    ): Boolean = provider == DataProvider.API_SPORTS && endpointKey == "fixtureSingle"

    override fun inspect(
        request: QualityCheckRequest,
        rawJson: JsonNode,
    ): QualityScanResult {
        if (rawJson.isNull) {
            log.warn("API Sports Fixture Single response root is null. provider={}, endpointKey={}", request.provider, request.endpointKey)
            return result(listOf(rootResponseNull()))
        }

        val root =
            try {
                objectMapper.treeToValue(rawJson, ApiSportsFixtureSingleRootDto::class.java)
            } catch (exception: JsonProcessingException) {
                log.error(
                    "Cannot map API Sports Fixture Single response. provider={}, endpointKey={}",
                    request.provider,
                    request.endpointKey,
                    exception,
                )
                return result(listOf(mappingFailed(exception)))
            } ?: run {
                log.error(
                    "API Sports Fixture Single response mapping returned null. provider={}, endpointKey={}",
                    request.provider,
                    request.endpointKey,
                )
                return result(listOf(mappingFailed()))
            }

        val responseIssues = responseInspectors.flatMap { it.inspect(root) }
        val requestResponseTarget =
            FixtureSingleRequestResponse(
                request =
                    FixtureSingleRequest(
                        fixtureId = request.parameters[FIXTURE_ID_PARAMETER]?.singleOrNull()?.toLongOrNull(),
                    ),
                response = root,
            )
        val requestResponseIssues = requestResponseInspectors.flatMap { it.inspect(requestResponseTarget) }

        return result(responseIssues + requestResponseIssues)
    }

    private fun result(findings: List<InspectionFinding>): QualityScanResult =
        QualityScanResult(
            scannerVersion = SCANNER_VERSION,
            findings = findings,
        )

    private fun rootResponseNull(): InspectionFinding =
        InspectionFinding(
            suggestedTypeCode = "ROOT_RESPONSE_NULL",
            severity = InspectionSeverity.CRITICAL,
            title = "Root response is null",
            responseLocation = InspectionResponseLocation("root", "$"),
        )

    private fun mappingFailed(exception: Exception? = null): InspectionFinding =
        InspectionFinding(
            suggestedTypeCode = "RAW_JSON_MAPPING_FAILED",
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
        const val SCANNER_VERSION = "apisports-fixture-single-rules-v1"
        const val FIXTURE_ID_PARAMETER = "fixtureId"
    }
}
