package com.footballay.footballaydataqualityservice.inspection

import com.fasterxml.jackson.databind.JsonNode
import com.footballay.footballaydataqualityservice.pipeline.DataProvider
import com.footballay.footballaydataqualityservice.pipeline.QualityScanResult

interface QualityInspectionProcessor {
    fun supports(
        provider: DataProvider,
        endpointKey: String,
    ): Boolean

    fun inspect(rawJson: JsonNode): QualityScanResult
}
