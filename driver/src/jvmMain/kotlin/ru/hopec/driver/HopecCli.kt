package ru.hopec.driver

import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.wasm.Parser
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.path
import kotlinx.coroutines.runBlocking
import okio.Buffer
import ru.hopec.core.CompilationContext
import ru.hopec.parser.treesitter.parseHope
import java.nio.file.Path
import kotlin.io.path.readText

class HopecCompile : CliktCommand() {
    val input: Path by argument().path(mustExist = true)

    override fun run() {
        val tree = runBlocking { parseHope(input.readText()) }
        val buffer = Buffer()
        val status = Hopec(CompilationContext()).run(tree, buffer)
        if (status != 0) {
            echo("compilation failed", err = true)
            return
        }
        val entrance = Instance.builder(Parser.parse(buffer.readByteArray())).build().export("main")
        echo(entrance.apply(0)[0])
    }
}

fun main(args: Array<String>) = HopecCompile().main(args)
