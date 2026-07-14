package com.footballay.footballaydataqualityservice.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.footballay.footballaydataqualityservice.pipeline.DataQualityPipelineService
import com.footballay.footballaydataqualityservice.pipeline.RawCollectedEvent
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "footballay.data-quality.pipeline", name = ["enabled"], havingValue = "true")
class RawCollectedKafkaListener(
    private val objectMapper: ObjectMapper,
    private val pipelineService: DataQualityPipelineService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = ["\${footballay.data-quality.pipeline.raw-collected-topic}"],
        groupId = "\${footballay.data-quality.pipeline.consumer-group}",
    )
    fun consume(payload: String) {
        val event = objectMapper.readValue(payload, RawCollectedEvent::class.java)
        log.info("Consuming raw-collected event. rawEventId={}", event.rawEventId)
        pipelineService.process(event)
    }
}
