package com.footballay.dataquality.inspection

import com.footballay.dataquality.pipeline.input.DataProvider
import com.footballay.dataquality.pipeline.input.QualityCheckRequest
import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.junit.jupiter.api.Test

class InspectionProcessorInspectorValidatorTest {
    @Test
    fun `rejects duplicated inspector names within a processor`() {
        val processor =
            TestProcessor(
                listOf(
                    TestInspector(InspectionName.of("FIXTURE")),
                    TestInspector(InspectionName.of("FIXTURE")),
                ),
            )

        assertThatIllegalStateException()
            .isThrownBy { InspectionProcessorInspectorValidator(listOf(processor)).afterSingletonsInstantiated() }
            .withMessage("Duplicated inspector names for processor=${TestProcessor::class.java.name}: FIXTURE")
    }

    private class TestProcessor(
        private val usedInspectors: List<QualityInspector<*>>,
    ) : QualityInspectionProcessor {
        override fun inspectors(): List<QualityInspector<*>> = usedInspectors

        override fun supports(
            provider: DataProvider,
            endpointKey: String,
        ): Boolean = false

        override fun inspect(
            request: QualityCheckRequest,
            rawJson: com.fasterxml.jackson.databind.JsonNode,
        ): List<FoundIssue> = emptyList()
    }

    private class TestInspector(
        override val inspectionName: InspectionName,
    ) : QualityInspector<Any> {
        override fun inspect(target: Any): List<FoundIssue> = emptyList()
    }
}
