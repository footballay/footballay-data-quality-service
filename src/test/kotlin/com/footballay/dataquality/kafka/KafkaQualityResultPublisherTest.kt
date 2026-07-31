package com.footballay.dataquality.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.footballay.dataquality.pipeline.input.DataProvider
import com.footballay.dataquality.pipeline.input.QualityParameter
import com.footballay.dataquality.pipeline.port.NoopQualityResultStore
import com.footballay.dataquality.pipeline.result.QualityResultWrite
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import java.time.Instant
import java.util.concurrent.CompletableFuture

class KafkaQualityResultPublisherTest {
    @Test
    @Suppress("UNCHECKED_CAST")
    fun `starts Kafka send without waiting for its future`() {
        val kafkaTemplate = mock(KafkaTemplate::class.java) as KafkaTemplate<String, String>
        val sendFuture = CompletableFuture<SendResult<String, String>>()
        val result = result()
        `when`(kafkaTemplate.send(eq("quality-result"), eq(result.resultId), anyString())).thenReturn(sendFuture)
        val publisher = KafkaQualityResultPublisher(kafkaTemplate, ObjectMapper().findAndRegisterModules(), "quality-result")

        publisher.publish(result)

        assertThat(sendFuture).isNotDone()
        verify(kafkaTemplate).send(eq("quality-result"), eq(result.resultId), anyString())
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `does not retry or throw when Kafka future fails`() {
        val kafkaTemplate = mock(KafkaTemplate::class.java) as KafkaTemplate<String, String>
        val sendFuture = CompletableFuture<SendResult<String, String>>()
        val result = result()
        `when`(kafkaTemplate.send(eq("quality-result"), eq(result.resultId), anyString())).thenReturn(sendFuture)
        val publisher = KafkaQualityResultPublisher(kafkaTemplate, ObjectMapper().findAndRegisterModules(), "quality-result")

        publisher.publish(result)
        sendFuture.completeExceptionally(IllegalStateException("Kafka unavailable"))

        verify(kafkaTemplate, times(1)).send(eq("quality-result"), eq(result.resultId), anyString())
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `does not throw when Kafka send cannot start`() {
        val kafkaTemplate = mock(KafkaTemplate::class.java) as KafkaTemplate<String, String>
        val result = result()
        `when`(kafkaTemplate.send(eq("quality-result"), eq(result.resultId), anyString()))
            .thenThrow(IllegalStateException("Kafka unavailable"))
        val publisher = KafkaQualityResultPublisher(kafkaTemplate, ObjectMapper().findAndRegisterModules(), "quality-result")

        assertThatCode { publisher.publish(result) }.doesNotThrowAnyException()
    }

    private fun result() =
        NoopQualityResultStore().save(
            QualityResultWrite(
                rawEventId = "01JZK8T9CJ4S9ZZ9G0E0D7YQ9M",
                provider = DataProvider.API_SPORTS,
                endpointKey = "fixtureSingle",
                parameters = listOf(QualityParameter("fixtureId", "123456")),
                canonicalHash = "hash",
                rawJsonObjectKey = "data-quality/raw/test.json.gz",
                collectedAt = Instant.parse("2026-07-13T00:00:00Z"),
                checkedAt = Instant.parse("2026-07-13T00:00:00Z"),
                inspectionVersion = 1,
                issues = emptyList(),
            ),
        )
}
