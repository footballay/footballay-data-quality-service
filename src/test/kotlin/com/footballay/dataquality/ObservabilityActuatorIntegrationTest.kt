package com.footballay.dataquality

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["management.endpoints.web.exposure.include=health,info,metrics,prometheus"],
)
class ObservabilityActuatorIntegrationTest {
    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @LocalServerPort
    private var port: Int = 0

    @Test
    fun `exposes JVM and Spring metrics through the Prometheus actuator endpoint`() {
        val response = restTemplate.getForEntity("http://localhost:$port/actuator/prometheus", String::class.java)

        assertThat(response.statusCode.is2xxSuccessful)
            .withFailMessage("Unexpected actuator response: %s", response)
            .isTrue()
        assertThat(response.body)
            .contains("jvm_memory_used_bytes", "process_uptime_seconds")
            .doesNotContain("mongodb_")
    }
}
