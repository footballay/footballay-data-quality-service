package com.footballay.dataquality.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.footballay.dataquality.logger
import com.footballay.dataquality.pipeline.port.QualityResultPublisher
import com.footballay.dataquality.pipeline.result.QualityResult
import com.footballay.dataquality.pipeline.result.toEvent
import org.springframework.kafka.core.KafkaTemplate

class KafkaQualityResultPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper,
    private val topic: String,
) : QualityResultPublisher {
    private val log = logger()

    override fun publish(result: QualityResult) {
        val payload = objectMapper.writeValueAsString(result.toEvent())
        kafkaTemplate.send(topic, result.resultId, payload).get()
        log.info("Published quality-result event. resultId={}, rawEventId={}", result.resultId, result.rawEventId)
    }
}
