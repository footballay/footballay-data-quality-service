package com.footballay.footballaydataqualityservice.pipeline

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource

class MockDataRawResponseReaderTest {
    private val reader =
        MockDataRawResponseReader(
            objectMapper = ObjectMapper().findAndRegisterModules(),
            resource = ClassPathResource("mock/20260712T033403Z_Iq_XxagJ8LaiRo1Fy1M4oYRNxUhVIp8aOn9jJCmAGWY.json.gz"),
        )

    @Test
    fun `reads and decompresses the configured gzip fixture`() {
        val result = reader.read("ignored-by-mock-reader")

        assertThat(result.path("get").asText()).isEqualTo("fixtures")
        assertThat(result.path("response").isArray).isTrue()
    }
}
