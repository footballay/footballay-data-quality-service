package com.footballay.dataquality.inspection

import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.stereotype.Component

/** Processor별 검사 이름의 중복을 애플리케이션 초기화 시 차단한다. */
@Component
class InspectionProcessorInspectorValidator(
    private val processors: List<QualityInspectionProcessor>,
) : SmartInitializingSingleton {
    override fun afterSingletonsInstantiated() {
        processors.forEach(::validateInspectionNames)
    }

    private fun validateInspectionNames(processor: QualityInspectionProcessor) {
        val duplicatedNames =
            processor.inspectionNames()
                .groupBy { it.value }
                .filterValues { it.size > 1 }
                .keys

        check(duplicatedNames.isEmpty()) {
            "Duplicated inspection names for processor=${processor.javaClass.name}: ${duplicatedNames.sorted().joinToString(", ")}"
        }
    }
}
