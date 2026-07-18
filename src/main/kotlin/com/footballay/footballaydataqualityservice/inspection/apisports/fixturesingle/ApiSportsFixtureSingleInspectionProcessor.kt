package com.footballay.footballaydataqualityservice.inspection.apisports.fixturesingle

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.footballay.footballaydataqualityservice.inspection.FoundIssue
import com.footballay.footballaydataqualityservice.inspection.InspectionEvidence
import com.footballay.footballaydataqualityservice.inspection.InspectionResponseLocation
import com.footballay.footballaydataqualityservice.inspection.InspectionSeverity
import com.footballay.footballaydataqualityservice.inspection.InspectionCode
import com.footballay.footballaydataqualityservice.inspection.InspectionScope
import com.footballay.footballaydataqualityservice.inspection.QualityInspectionProcessor
import com.footballay.footballaydataqualityservice.inspection.QualityInspector
import com.footballay.footballaydataqualityservice.inspection.validateInspectionIdentifier
import com.footballay.footballaydataqualityservice.logger
import com.footballay.footballaydataqualityservice.pipeline.input.DataProvider
import com.footballay.footballaydataqualityservice.pipeline.input.QualityCheckRequest

/** API Sports Fixture Single 응답을 관측 DTO로 변환하고 전용 Inspector를 실행한다. */
class ApiSportsFixtureSingleInspectionProcessor(
    private val objectMapper: ObjectMapper,
    private val responseInspectors: List<QualityInspector<ApiSportsFixtureSingleRootDto>>,
    private val requestResponseInspectors: List<QualityInspector<FixtureSingleRequestResponse>>,
) : QualityInspectionProcessor {
    private val log = logger()

    init {
        validateInspectionScopes()
    }

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
                log.error(
                    "Cannot map API Sports Fixture Single response. provider={}, endpointKey={}",
                    request.provider,
                    request.endpointKey,
                    exception,
                )
                return listOf(mappingFailed(exception))
            } ?: run {
                log.error(
                    "API Sports Fixture Single response mapping returned null. provider={}, endpointKey={}",
                    request.provider,
                    request.endpointKey,
                )
                return listOf(mappingFailed())
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

        return responseIssues + requestResponseIssues
    }

    private fun rootResponseNull(): FoundIssue =
        FoundIssue(
            inspectionScope = FixtureSingleInspectionScope.RAW_MAPPING,
            inspectionCode = ProcessorCode.ROOT_NULL,
            severity = InspectionSeverity.CRITICAL,
            title = "Root response is null",
            responseLocation = InspectionResponseLocation("root", "$"),
        )

    private fun mappingFailed(exception: Exception? = null): FoundIssue =
        FoundIssue(
            inspectionScope = FixtureSingleInspectionScope.RAW_MAPPING,
            inspectionCode = ProcessorCode.MAPPING_FAILED,
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

    private fun validateInspectionScopes() {
        val scopes =
            responseInspectors.map(QualityInspector<*>::inspectionScope) +
                requestResponseInspectors.map(QualityInspector<*>::inspectionScope) +
                FixtureSingleInspectionScope.RAW_MAPPING
        scopes.forEach { validateInspectionIdentifier("Inspection scope", it.value) }
        val duplicatedScopes = scopes.groupBy(InspectionScope::value).filterValues { it.size > 1 }.keys
        check(duplicatedScopes.isEmpty()) {
            "Duplicated inspection scopes for APISPORTS:FIXTURE_SINGLE: ${duplicatedScopes.sorted().joinToString(", ")}"
        }
    }

    private companion object {
        const val FIXTURE_ID_PARAMETER = "fixtureId"
    }

    private enum class ProcessorCode : InspectionCode {
        ROOT_NULL,
        MAPPING_FAILED,
        ;

        override val value: String
            get() = name
    }
}
