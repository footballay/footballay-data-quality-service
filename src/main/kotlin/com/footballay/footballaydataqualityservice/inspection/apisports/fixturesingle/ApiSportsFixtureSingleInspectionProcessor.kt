package com.footballay.footballaydataqualityservice.inspection.apisports.fixturesingle

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.footballay.footballaydataqualityservice.inspection.QualityInspectionProcessor
import com.footballay.footballaydataqualityservice.inspection.QualityInspector
import com.footballay.footballaydataqualityservice.pipeline.DataProvider
import com.footballay.footballaydataqualityservice.pipeline.QualityIssue
import com.footballay.footballaydataqualityservice.pipeline.QualityIssueSeverity
import com.footballay.footballaydataqualityservice.pipeline.QualityScanResult

/** API Sports Fixture Single 응답을 관측 DTO로 변환하고 전용 Inspector를 실행한다. */
class ApiSportsFixtureSingleInspectionProcessor(
    private val objectMapper: ObjectMapper,
    private val inspectors: List<QualityInspector<ApiSportsFixtureSingleRootDto>>,
) : QualityInspectionProcessor {
    override fun supports(
        provider: DataProvider,
        endpointKey: String,
    ): Boolean = provider == DataProvider.API_SPORTS && endpointKey == "fixtureSingle"

    override fun inspect(rawJson: JsonNode): QualityScanResult {
        val root =
            try {
                objectMapper.treeToValue(rawJson, ApiSportsFixtureSingleRootDto::class.java)
                    ?: return mappingFailed()
            } catch (_: JsonProcessingException) {
                return mappingFailed()
            }

        return QualityScanResult(
            scannerVersion = SCANNER_VERSION,
            issues = inspectors.flatMap { it.inspect(root) },
        )
    }

    private fun mappingFailed(): QualityScanResult =
        QualityScanResult(
            scannerVersion = SCANNER_VERSION,
            issues =
                listOf(
                    QualityIssue(
                        suggestedTypeCode = "RAW_JSON_MAPPING_FAILED",
                        severity = QualityIssueSeverity.CRITICAL,
                        title = "Raw JSON cannot be mapped to API Sports Fixture Single DTO",
                        section = "root",
                        path = "\$",
                    ),
                ),
        )

    private companion object {
        const val SCANNER_VERSION = "apisports-fixture-single-skeleton-v1"
    }
}
