package com.sigak

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SigakBackendApplication

fun main(args: Array<String>) {
    runApplication<SigakBackendApplication>(*args)
}
