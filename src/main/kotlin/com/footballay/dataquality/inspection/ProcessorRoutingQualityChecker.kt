package com.footballay.dataquality.inspection

import com.fasterxml.jackson.databind.JsonNode
import com.footballay.dataquality.pipeline.QualityChecker
import com.footballay.dataquality.pipeline.input.QualityCheckRequest

/** Provider와 endpoint에 맞는 단일 검사 Processor를 선택한다. */
class ProcessorRoutingQualityChecker(
    private val processors: List<QualityInspectionProcessor>,
) : QualityChecker {
    override val inspectionVersion: Int = 1

    override fun check(
        request: QualityCheckRequest,
        rawJson: JsonNode,
    ): List<FoundIssue> {
        val matchedProcessors = processors.filter { it.supports(request.provider, request.endpointKey) }
        check(matchedProcessors.size == 1) {
            "Expected exactly one quality inspection processor for provider=${request.provider}, endpointKey=${request.endpointKey}, but found ${matchedProcessors.size}"
        }
        return matchedProcessors.single().inspect(request, rawJson)
    }
}
