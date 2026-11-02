package com.sigak.source.runner

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.sigak.collection.runner.ApplicationExit
import com.sigak.collection.runner.CollectionRunCommandOutput
import com.sigak.common.http.ExternalFetchException
import com.sigak.source.service.SourceCheckOutput
import com.sigak.source.service.SourceManagementService
import com.sigak.source.service.SourceOperationException
import com.sigak.source.service.SourcePatch
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

@Component
class SourceCommandRunner(private val service: SourceManagementService, private val mapper: ObjectMapper,
    private val exit: ApplicationExit, private val output: CollectionRunCommandOutput, private val environment: Environment) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        if (!args.containsOption("sigak.sources.command")) return
        try {
            val protected = listOf("sigak.collection.automation.enabled","sigak.summary.enabled","sigak.summary.worker-enabled","sigak.fulltext.worker-enabled")
            if (protected.any { environment.getProperty(it) != "false" } || environment.getProperty("spring.main.web-application-type") != "none") throw SourceOperationException("UNSAFE_SOURCE_COMMAND_MODE")
            val allowed = setOf("command","url","key","version","patch","reviewed","feed-reviewed","full").map { "sigak.sources.$it" }
            if (args.optionNames.any { it.startsWith("sigak.sources.") && it !in allowed }) throw SourceOperationException("UNKNOWN_SOURCE_OPTION")
            fun value(name: String): String = args.getOptionValues("sigak.sources.$name")?.singleOrNull()?.takeIf { it.isNotBlank() }
                ?: throw SourceOperationException("SOURCE_OPTION_REQUIRED_${name.uppercase()}")
            fun flag(name: String): Boolean {
                val values = args.getOptionValues("sigak.sources.$name") ?: return false
                return when { values.isEmpty() -> true; values.size==1 && values.single() == "true" -> true
                    values.size==1 && values.single() == "false" -> false; else -> throw SourceOperationException("INVALID_SOURCE_FLAG") }
            }
            fun version(): Long = value("version").toLongOrNull()?.takeIf { it > 0 } ?: throw SourceOperationException("INVALID_SOURCE_VERSION")
            val result: Any = when (value("command")) {
                "list" -> service.list()
                "register" -> service.register(value("url")).let { source ->
                    if (source.version==1L && source.validationStatus=="UNVERIFIED" && source.report.isEmpty()) service.check(source.key,source.version) else source
                }
                "check" -> service.check(value("key"),version())
                "enable" -> service.enable(value("key"),version(),flag("reviewed"),flag("feed-reviewed"))
                "disable" -> service.disable(value("key"),version())
                "configure" -> {
                    val patch = try { mapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue<SourcePatch>(value("patch")) }
                        catch (_: Exception) { throw SourceOperationException("INVALID_SOURCE_PATCH") }
                    service.configure(value("key"),version(),patch)
                }
                else -> throw SourceOperationException("UNKNOWN_SOURCE_COMMAND")
            }
            val shown = if (result is SourceCheckOutput && !flag("full")) mapOf("source" to result.source,"samples" to result.samples.map { sample ->
                sample.metadata() + mapOf("bodyPreview" to preview(sample.text),"feedPreview" to preview(sample.feedText))
            }) else result
            output.writeLine("SIGAK_SOURCE_RESULT="+mapper.writeValueAsString(shown))
            exit.exit(if (result is SourceCheckOutput && result.source.validationStatus != "PASSED") 2 else 0)
        } catch (error: Exception) {
            val code = when (error) { is SourceOperationException -> error.code; is ExternalFetchException -> error.code; else -> "SOURCE_COMMAND_FAILED" }
            output.writeErrorLine("SIGAK_SOURCE_ERROR="+mapper.writeValueAsString(mapOf("error" to code)))
            exit.exit(1)
        }
    }

    private fun preview(text: String?): Map<String,String>? = text?.let {
        val start = (it.length/2-120).coerceAtLeast(0)
        mapOf("opening" to it.take(240),"middle" to it.substring(start,(start+240).coerceAtMost(it.length)),"closing" to it.takeLast(240))
    }
}
