package com.footballay.footballaydataqualityservice.pipeline

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory

fun interface RawResponseReader {
    fun read(rawJsonObjectKey: String): JsonNode
}

class NoopRawResponseReader : RawResponseReader {
    override fun read(rawJsonObjectKey: String): JsonNode = JsonNodeFactory.instance.objectNode()
}
