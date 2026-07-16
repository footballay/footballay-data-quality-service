package com.footballay.footballaydataqualityservice.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.footballay.footballaydataqualityservice.logger
import com.footballay.footballaydataqualityservice.pipeline.QualityResult
import com.footballay.footballaydataqualityservice.pipeline.QualityResultPublisher
import com.footballay.footballaydataqualityservice.pipeline.toEvent
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
