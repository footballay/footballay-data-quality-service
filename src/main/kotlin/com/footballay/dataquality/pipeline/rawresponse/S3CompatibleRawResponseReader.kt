package com.footballay.dataquality.pipeline.rawresponse

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import java.io.IOException
import java.util.zip.GZIPInputStream

/** S3 API를 통해 gzip JSON 원본 응답을 읽어 검사 입력으로 변환합니다. */
class S3CompatibleRawResponseReader(
    private val objectMapper: ObjectMapper,
    private val s3Client: S3Client,
    private val bucket: String,
) : RawResponseReader {
    override fun read(rawJsonObjectKey: String): JsonNode {
        require(rawJsonObjectKey.isNotBlank()) {
            "rawJsonObjectKey must not be blank"
        }

        return s3Client.getObject(
            GetObjectRequest.builder()
                .bucket(bucket)
                .key(rawJsonObjectKey)
                .build(),
        ).use { response ->
            GZIPInputStream(response).use { gzip ->
                objectMapper.readTree(gzip) ?: throw IOException("Raw response object is empty")
            }
        }
    }
}
