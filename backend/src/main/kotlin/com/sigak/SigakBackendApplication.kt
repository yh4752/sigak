package com.sigak

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SigakBackendApplication

fun main(args: Array<String>) {
    // 유료 POST의 연결 재시도는 결과를 불확실하게 만들 수 있으므로 시작 전에 비활성화한다.
    System.setProperty("jdk.httpclient.disableRetryConnect", "true")
    runApplication<SigakBackendApplication>(*sourceOperatorArguments(args))
}

fun sourceOperatorArguments(args: Array<String>): Array<String> {
    if (args.none { it == "--sigak.sources.command" || it.startsWith("--sigak.sources.command=") }) return args
    // 운영 명령은 환경의 active 설정보다 먼저 worker를 끈다. Runner 시작까지의 과금 경합도 막는다.
    val forced = mapOf("spring.main.web-application-type" to "none", "sigak.collection.automation.enabled" to "false",
        "sigak.summary.enabled" to "false", "sigak.summary.worker-enabled" to "false", "sigak.fulltext.worker-enabled" to "false")
    return (args.filterNot { argument -> argument.substringBefore('=').removePrefix("--") in forced.keys } +
        forced.map { (key,value) -> "--$key=$value" }).toTypedArray()
}
