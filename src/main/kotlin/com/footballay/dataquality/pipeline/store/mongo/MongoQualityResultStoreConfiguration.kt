package com.footballay.dataquality.pipeline.store.mongo

import com.footballay.dataquality.pipeline.port.NoopQualityResultStore
import com.footballay.dataquality.pipeline.port.QualityResultStore
import org.springframework.beans.factory.InitializingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.core.MongoOperations
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "footballay.data-quality.pipeline", name = ["enabled"], havingValue = "true")
class MongoQualityResultStoreConfiguration {
    @Bean
    @ConditionalOnProperty(
        prefix = "footballay.data-quality.pipeline.store",
        name = ["type"],
        havingValue = "MONGO",
        matchIfMissing = true,
    )
    fun mongoQualityResultStore(mongoOperations: MongoOperations): MongoQualityResultStore =
        MongoQualityResultStore(mongoOperations)

    @Bean
    @ConditionalOnProperty(
        prefix = "footballay.data-quality.pipeline.store",
        name = ["type"],
        havingValue = "NOOP",
    )
    fun noopQualityResultStore(): QualityResultStore = NoopQualityResultStore()

    @Bean
    @ConditionalOnProperty(
        prefix = "footballay.data-quality.pipeline.store",
        name = ["type"],
        havingValue = "MONGO",
        matchIfMissing = true,
    )
    fun mongoQualityResultIndexInitializer(mongoOperations: MongoOperations): InitializingBean =
        InitializingBean {
            val indexResolver = MongoPersistentEntityIndexResolver(mongoOperations.converter.mappingContext)
            val indexOperations = mongoOperations.indexOps(QualityResultDocument::class.java)

            indexResolver.resolveIndexFor(QualityResultDocument::class.java).forEach(indexOperations::createIndex)
        }
}
