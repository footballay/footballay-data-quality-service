package com.footballay.footballaydataqualityservice.pipeline

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test

class DataProviderTest {
    private val objectMapper = ObjectMapper()

    @Test
    fun `maps API Sports provider spelling variants to the same enum value`() {
        val values =
            listOf(
                "API_SPORTS",
                "ApiSports",
                "apiSports",
                "API-SPORTS",
                "api_sports",
                "api sports",
            )

        val providers = values.map { value -> objectMapper.readValue("\"$value\"", DataProvider::class.java) }

        assertThat(providers).containsOnly(DataProvider.API_SPORTS)
    }

    @Test
    fun `rejects an unsupported provider value`() {
        assertThatIllegalArgumentException()
            .isThrownBy { DataProvider.fromWireValue("sportmonks") }
            .withMessage("Unsupported data provider: sportmonks")
    }
}
