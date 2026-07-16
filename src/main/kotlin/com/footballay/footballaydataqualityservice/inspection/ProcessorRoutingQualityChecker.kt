package com.footballay.footballaydataqualityservice.inspection

import com.fasterxml.jackson.databind.JsonNode
import com.footballay.footballaydataqualityservice.pipeline.QualityChecker
import com.footballay.footballaydataqualityservice.pipeline.QualityScanResult
import com.footballay.footballaydataqualityservice.pipeline.input.QualityCheckRequest

/** Provider와 endpoint에 맞는 단일 검사 Processor를 선택한다. */
class ProcessorRoutingQualityChecker(
    private val processors: List<QualityInspectionProcessor>,
) : QualityChecker {
    override fun check(
        request: QualityCheckRequest,
        rawJson: JsonNode,
    ): QualityScanResult {
        val matchedProcessors = processors.filter { it.supports(request.provider, request.endpointKey) }
        check(matchedProcessors.size == 1) {
            "Expected exactly one quality inspection processor for provider=${request.provider}, endpointKey=${request.endpointKey}, but found ${matchedProcessors.size}"
        }
        return matchedProcessors.single().inspect(request, rawJson)
    }
}
