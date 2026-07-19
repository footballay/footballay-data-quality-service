package com.footballay.footballaydataqualityservice.inspection

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test

class InspectionNameTest {
    @Test
    fun `creates an uppercase snake case inspection name`() {
        val name = InspectionName.of("FIXTURE_STATUS")

        assertThat(name.value).isEqualTo("FIXTURE_STATUS")
    }

    @Test
    fun `rejects an invalid inspection name`() {
        assertThatIllegalArgumentException()
            .isThrownBy { InspectionName.of("fixtureStatus") }
            .withMessage("Inspection name must use uppercase snake case: fixtureStatus")
    }
}
