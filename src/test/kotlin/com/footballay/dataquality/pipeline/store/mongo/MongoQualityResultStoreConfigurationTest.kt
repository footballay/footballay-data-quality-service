package com.footballay.dataquality.pipeline.store.mongo

import com.footballay.dataquality.pipeline.port.NoopQualityResultStore
import com.footballay.dataquality.pipeline.port.QualityResultStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.data.mongodb.core.MongoOperations
import org.springframework.data.mongodb.core.convert.MongoConverter
import org.springframework.data.mongodb.core.convert.MongoCustomConversions
import org.springframework.data.mongodb.core.index.IndexOperations
import org.springframework.data.mongodb.core.mapping.MongoMappingContext
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class MongoQualityResultStoreConfigurationTest {
    private val contextRunner =
        ApplicationContextRunner()
            .withUserConfiguration(MongoQualityResultStoreConfiguration::class.java)
            .withPropertyValues("footballay.data-quality.pipeline.enabled=true")

    @Test
    fun `uses Mongo store by default`() {
        contextRunner
            .withUserConfiguration(MongoOperationsTestConfiguration::class.java)
            .run { context ->
                assertThat(context).hasSingleBean(MongoQualityResultStore::class.java)
                assertThat(context.getBean(QualityResultStore::class.java)).isInstanceOf(MongoQualityResultStore::class.java)
                verify(context.getBean(IndexOperations::class.java), times(10))
                    .createIndex(org.mockito.ArgumentMatchers.any(org.springframework.data.mongodb.core.index.IndexDefinition::class.java))
            }
    }

    @Test
    fun `uses Noop store when explicitly configured`() {
        contextRunner
            .withPropertyValues("footballay.data-quality.pipeline.store.type=noop")
            .run { context ->
                assertThat(context).hasSingleBean(NoopQualityResultStore::class.java)
                assertThat(context.getBean(QualityResultStore::class.java)).isInstanceOf(NoopQualityResultStore::class.java)
            }
    }
}

@TestConfiguration(proxyBeanMethods = false)
private class MongoOperationsTestConfiguration {
    @Bean
    fun mongoOperations(indexOperations: IndexOperations): MongoOperations {
        val mongoOperations = mock(MongoOperations::class.java)
        val converter = mock(MongoConverter::class.java)
        val customConversions = MongoCustomConversions.create { }
        val mappingContext =
            MongoMappingContext().apply {
                setSimpleTypeHolder(customConversions.simpleTypeHolder)
                afterPropertiesSet()
            }

        `when`(mongoOperations.converter).thenReturn(converter)
        `when`(converter.mappingContext).thenReturn(mappingContext)
        `when`(mongoOperations.indexOps(QualityResultDocument::class.java)).thenReturn(indexOperations)
        return mongoOperations
    }

    @Bean
    fun indexOperations(): IndexOperations = mock(IndexOperations::class.java)
}
