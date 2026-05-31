package com.sigak.collection.runner

import kotlin.system.exitProcess
import org.springframework.boot.ExitCodeGenerator
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.stereotype.Component

fun interface ApplicationExit {
    fun exit(code: Int)
}

@Component
class SpringApplicationExit(
    private val context: ConfigurableApplicationContext
) : ApplicationExit {

    override fun exit(code: Int) {
        exitProcess(SpringApplication.exit(context, ExitCodeGenerator { code }))
    }
}
