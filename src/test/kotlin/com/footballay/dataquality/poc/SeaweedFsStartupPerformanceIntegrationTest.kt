package com.footballay.dataquality.poc

import com.footballay.dataquality.support.SeaweedFsMiniContainer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.ListBucketsRequest
import java.net.URI
import kotlin.math.round

class SeaweedFsStartupPerformanceIntegrationTest {
    @Test
    @DisplayName("로컬에 pull된 SeaweedFS image를 10회 순차 기동해 S3 ready 시간을 측정한다.")
    fun measuresTenSequentialStarts() {
        val samples = mutableListOf<StartupSample>()
        repeat(10) { iteration ->
            val requestedAt = System.nanoTime()
            val container = SeaweedFsMiniContainer()
            try {
                container.start()
                val endpointReadyAt = System.nanoTime()
                createS3Client(container.endpoint()).use { client ->
                    client.listBuckets(ListBucketsRequest.builder().build())
                }
                val apiReadyAt = System.nanoTime()
                samples += StartupSample(
                    iteration = iteration + 1,
                    endpointReadyMs = elapsedMillis(requestedAt, endpointReadyAt),
                    firstApiSuccessMs = elapsedMillis(requestedAt, apiReadyAt),
                )
            } finally {
                container.stop()
            }
        }

        assertThat(samples).hasSize(10)
        println("SeaweedFS startup samples (ms): ${samples.joinToString { "#${it.iteration} endpoint=${it.endpointReadyMs} api=${it.firstApiSuccessMs}" }}")
        println("SeaweedFS endpoint ready summary (ms): ${summary(samples.map { it.endpointReadyMs })}")
        println("SeaweedFS first API success summary (ms): ${summary(samples.map { it.firstApiSuccessMs })}")
    }

    private fun createS3Client(endpoint: String): S3Client =
        S3Client.builder().endpointOverride(URI.create(endpoint)).region(Region.of("ap-northeast-2"))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(SeaweedFsMiniContainer.ACCESS_KEY, SeaweedFsMiniContainer.SECRET_KEY)))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build()).build()

    private fun elapsedMillis(startedAt: Long, completedAt: Long): Long = (completedAt - startedAt) / 1_000_000

    private fun summary(values: List<Long>): String {
        val ordered = values.sorted()
        val average = round(values.average() * 10) / 10
        val median = (ordered[4] + ordered[5]) / 2.0
        return "average=$average median=$median min=${ordered.first()} max=${ordered.last()} failures=0"
    }

    private data class StartupSample(
        val iteration: Int,
        val endpointReadyMs: Long,
        val firstApiSuccessMs: Long,
    )
}
