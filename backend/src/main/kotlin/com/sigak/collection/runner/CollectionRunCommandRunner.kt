package com.sigak.collection.runner

import com.sigak.collection.service.CollectionRunService
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

@Component
class CollectionRunCommandRunner(
    private val parser: CollectionRunCommandParser,
    private val service: CollectionRunService,
    private val formatter: CollectionRunCommandFormatter,
    private val output: CollectionRunCommandOutput,
    private val exit: ApplicationExit
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        val command = runCatching { parser.parse(args.sourceArgs) }
            .getOrElse { exception ->
                output.writeErrorLine(formatter.formatError(exception))
                exit.exit(1)
                return
            }

        if (command == null) {
            return
        }

        runCatching { service.run(command.request) }
            .onSuccess { response ->
                output.writeLine(formatter.format(response))
                exit.exit(0)
            }
            // CLI 실행에서는 잘못된 source/max 요청을 HTTP 400 대신 exit code로 표현한다.
            .onFailure { exception ->
                output.writeErrorLine(formatter.formatError(exception))
                exit.exit(1)
            }
    }
}
