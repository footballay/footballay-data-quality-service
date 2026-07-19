package com.footballay.dataquality.inspection

import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test

class InspectionRuleCodeTest {
    @Test
    fun `accepts uppercase snake case rule codes`() {
        assertThatCode {
            validateInspectionRuleCode("LONG_NULL")
            validateInspectionRuleCode("MAPPING_FAILED")
        }.doesNotThrowAnyException()
    }

    @Test
    fun `rejects rule codes outside uppercase snake case`() {
        listOf("longNull", "long-null", "LONG:NULL").forEach { invalidRuleCode ->
            assertThatIllegalArgumentException()
                .isThrownBy { validateInspectionRuleCode(invalidRuleCode) }
                .withMessage("Inspection rule code must use uppercase snake case: $invalidRuleCode")
        }
    }
}
