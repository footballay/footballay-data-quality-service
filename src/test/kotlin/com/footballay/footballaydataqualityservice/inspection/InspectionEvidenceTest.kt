package com.footballay.footballaydataqualityservice.inspection

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class InspectionEvidenceTest {
    @Test
    fun `keeps bounded string values and null values`() {
        val entries = (1..7).map { "key$it" to "v".repeat(if (it == 1) 300 else 1) }.toTypedArray()

        val evidence = InspectionEvidence.of(*entries, "nullable" to null)

        assertThat(evidence.values).hasSize(8)
        assertThat(evidence.values["key1"]).hasSize(256)
        assertThat(evidence.values).containsEntry("nullable", null)
        assertThat(evidence.values).doesNotContainKey("key9")
    }
}
