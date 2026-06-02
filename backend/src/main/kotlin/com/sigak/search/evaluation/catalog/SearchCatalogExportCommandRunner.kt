package com.sigak.search.evaluation.catalog

import com.sigak.collection.runner.ApplicationExit
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

@Component
class SearchCatalogExportCommandRunner(
    private val parser: SearchCatalogExportCommandParser,
    private val service: SearchCatalogExportService,
    private val writer: SearchCatalogExportJsonWriter,
    private val formatter: SearchCatalogExportCommandFormatter,
    private val output: SearchCatalogExportCommandOutput,
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

        runCatching {
            val catalog = service.export(command)
            writer.write(command.output, catalog)
            SearchCatalogExportCommandResult(command = command, catalog = catalog)
        }.onSuccess { result ->
            output.writeLine(formatter.format(result))
            exit.exit(0)
        }.onFailure { exception ->
            output.writeErrorLine(formatter.formatError(exception))
            exit.exit(1)
        }
    }
}
