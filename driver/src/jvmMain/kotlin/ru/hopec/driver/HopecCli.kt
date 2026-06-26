package ru.hopec.driver

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import ru.hopec.driver.bench.FannkuchBenchmark

class HopecCli : CliktCommand(name = "hopec") {
    override fun run() {
        echo("Use a subcommand: compile or bench")
    }
}

class HopecBench : CliktCommand(name = "bench") {
    private val n by option("--n", help = "Pfannkuchen size").int().default(5)

    override fun run() {
        val result = FannkuchBenchmark.run(n)
        echo(result.report())
    }
}

fun main(args: Array<String>) =
    HopecCli()
        .subcommands(HopecCompile(), HopecBench())
        .main(args)
