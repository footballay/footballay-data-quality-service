package com.footballay.dataquality.inspection

import com.fasterxml.jackson.databind.JsonNode
import com.footballay.dataquality.pipeline.input.DataProvider
import com.footballay.dataquality.pipeline.input.QualityCheckRequest

interface QualityInspectionProcessor {
    fun inspectors(): List<QualityInspector<*>>

    /** Processor가 사용하는 Inspector와 Processor 자체 검사 이름을 모두 반환한다. */
    fun inspectionNames(): List<InspectionName> = inspectors().map { it.inspectionName }

    fun supports(
        provider: DataProvider,
        endpointKey: String,
    ): Boolean

    fun inspect(
        request: QualityCheckRequest,
        rawJson: JsonNode,
    ): List<FoundIssue>
}
