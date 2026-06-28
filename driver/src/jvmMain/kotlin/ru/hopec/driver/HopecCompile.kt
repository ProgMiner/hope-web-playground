package ru.hopec.driver

import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.wasm.Parser
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import kotlinx.coroutines.runBlocking
import okio.Buffer
import ru.hopec.core.GlobalCompilationContext
import ru.hopec.core.isError
import ru.hopec.parser.treesitter.parseHope
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeBytes

class HopecCompile : CliktCommand(name = "compile") {
    val input: Path by argument().path(mustExist = true)

    private val output: Path? by option(
        "-o",
        "--output",
        help = "Write the compiled wasm binary to this file instead of executing it",
    ).path()

    override fun run() {
        val tree = runBlocking { parseHope(input.readText()) }
        val buffer = Buffer()
        val status = Hopec(GlobalCompilationContext()).run(tree, buffer)
        if (status.isError()) {
            echo("compilation failed", err = true)
            return
        }
        val wasmBinary = buffer.readByteArray()
        if (output != null) {
            output!!.writeBytes(wasmBinary)
            echo("wasm written to $output")
            return
        }
        val entrance = Instance.builder(Parser.parse(wasmBinary)).build().export("main")
        echo(entrance.apply(0)[0])
    }
}
