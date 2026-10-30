package com.sigak

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SigakBackendApplication

fun main(args: Array<String>) {
    // 유료 POST의 연결 재시도는 결과를 불확실하게 만들 수 있으므로 시작 전에 비활성화한다.
    System.setProperty("jdk.httpclient.disableRetryConnect", "true")
    runApplication<SigakBackendApplication>(*args)
}
