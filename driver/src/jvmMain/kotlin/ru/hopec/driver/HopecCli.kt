package ru.hopec.driver

import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.wasm.Parser
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.path
import okio.Buffer
import ru.hopec.core.CompilationContext
import ru.hopec.parser.treesitter.findSharedLibrary
import ru.hopec.parser.treesitter.parseHope
import java.nio.file.Path
import kotlin.io.path.readText

class HopecCompile : CliktCommand() {
    val input: Path by argument().path(mustExist = true)

    override fun run() {
        val tree = parseHope(findSharedLibrary(), input.readText())
        val buffer = Buffer()
        Hopec(CompilationContext()).run(tree, buffer)
        val entrance = Instance.builder(Parser.parse(buffer.readByteArray())).build().export("add")
        echo(entrance.apply(2, 2)[0])
    }

}

fun main(args: Array<String>) = HopecCompile().main(args)
