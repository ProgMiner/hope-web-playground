package ru.hopec.driver

import okio.Buffer
import ru.hopec.core.CompilationContext
import ru.hopec.core.JsObject
import ru.hopec.core.genericTreeType
import ru.hopec.core.set
import ru.hopec.core.toJsObject
import ru.hopec.core.topography.Range
import ru.hopec.core.tree.GenericNode
import ru.hopec.core.tree.GenericTree
import ru.hopec.core.tree.intoNode
import ru.hopec.parser.treesitter.JsTree
import ru.hopec.parser.treesitter.Tree
import kotlin.wasm.unsafe.Pointer
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.withScopedMemoryAllocator

@OptIn(ExperimentalWasmJsInterop::class, ExperimentalJsExport::class, UnsafeWasmMemoryApi::class)
@JsExport
fun compile(input: Tree): JsObject {
    val buffer = Buffer()
    val context = CompilationContext()
    context.rememberTree(generateGenericTree())
    Hopec(context).run(JsTree(input), buffer)
    val result = buffer.readByteArray()
    withScopedMemoryAllocator {
        val buffer = Pointer(0U)
        result.forEachIndexed { index, b -> buffer.plus(index).storeByte(b) }
        return CompilationResult(result.size.toUInt(), context).toJsObject()
    }
}

data class CompilationResult(
    val size: UInt,
    val representations: List<GenericTree>,
) {
    constructor(size: UInt, context: CompilationContext) : this(size, trees(context))

    @OptIn(ExperimentalWasmJsInterop::class)
    fun toJsObject() =
        JsObject().apply {
            set("size", size)
            set("representations", representations.map { it.toJsObject() }.toJsArray())
        }
}

private fun trees(context: CompilationContext): List<GenericTree> {
    val trees = ArrayList(context.trees())
    trees.add(GenericTree(statusTreeType(), context.result().intoNode()))
    return trees
}

@OptIn(ExperimentalJsExport::class)
@JsExport
fun statusTreeType(): String = "STATUS"

@OptIn(ExperimentalWasmJsInterop::class)
private fun generateGenericTree(): GenericTree =
    GenericTree(
        genericTreeType(),
        GenericNode(Range(), "hello", listOf()),
    )
