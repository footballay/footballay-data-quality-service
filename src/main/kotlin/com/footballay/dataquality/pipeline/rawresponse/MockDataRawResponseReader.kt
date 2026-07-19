package com.footballay.dataquality.pipeline.rawresponse

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.core.io.Resource
import java.util.zip.GZIPInputStream

class MockDataRawResponseReader(
    private val objectMapper: ObjectMapper,
    private val resource: Resource,
) : RawResponseReader {
    override fun read(rawJsonObjectKey: String): JsonNode =
        resource.inputStream.use { input ->
            GZIPInputStream(input).use(objectMapper::readTree)
        }
}
