package com.footballay.dataquality.support

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName

/** SeaweedFS `weed mini` S3 endpoint를 AWS SDK 통합 테스트에 제공한다. */
class SeaweedFsMiniContainer : GenericContainer<SeaweedFsMiniContainer>(IMAGE) {
    init {
        withEnv("AWS_ACCESS_KEY_ID", ACCESS_KEY)
        withEnv("AWS_SECRET_ACCESS_KEY", SECRET_KEY)
        withCommand("mini", "-dir=/data")
        withExposedPorts(S3_PORT)
        withStartupAttempts(1)
        waitingFor(Wait.forHttp("/").forPort(S3_PORT).forStatusCode(403))
    }

    fun endpoint(): String = "http://$host:${getMappedPort(S3_PORT)}"

    companion object {
        const val IMAGE_TAG = "chrislusf/seaweedfs:4.29"
        const val ACCESS_KEY = "seaweedfsadmin"
        const val SECRET_KEY = "seaweedfsadmin"
        const val S3_PORT = 8333
        private val IMAGE = DockerImageName.parse(IMAGE_TAG)
    }
}
