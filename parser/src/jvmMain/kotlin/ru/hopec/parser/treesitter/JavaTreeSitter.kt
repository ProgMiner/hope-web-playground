package ru.hopec.parser.treesitter

import org.treesitter.TSLanguage
import kotlin.system.getProperty
import org.treesitter.TSNode
import org.treesitter.TSParser
import org.treesitter.TSPoint
import org.treesitter.TSQuery
import org.treesitter.TSQueryCapture
import org.treesitter.TSQueryCursor
import org.treesitter.TSQueryMatch
import org.treesitter.TSRange
import org.treesitter.TSTree
import org.treesitter.TSTreeCursor

private class JavaParser(
    val delegate: TSParser,
) : TsParser {
    override fun parse(input: String): TsTree = JavaTree(input, delegate.parseString(null, input))

    override fun getIncludedRanges(): List<TsRange> = delegate.getIncludedRanges().toList().map { JavaRange(it) }

    override fun reset() = delegate.reset()

    override fun getLanguage(): TsLanguage = JavaLanguage(delegate.language)

    override fun setLanguage(language: TsLanguage?) {
        delegate.setLanguage(language?.inner())
    }
}

private class JavaTree(
    val text: String,
    val delegate: TSTree,
) : TsTree {
    override val rootNode: TsSyntaxNode = delegate.rootNode.wrap(this)

    override fun rootNodeWithOffset(
        offsetBytes: UInt,
        offsetExtent: TsPoint,
    ): TsSyntaxNode = delegate.getRootNodeWithOffset(offsetBytes.toInt(), offsetExtent.inner()).wrap(this)

    override fun walk(): TsTreeCursor = JavaTreeCursor(this, TSTreeCursor(delegate.rootNode))

    override fun getIncludedRanges(): List<TsRange> = delegate.getIncludedRanges().toList().map { JavaRange(it) }
}

private class JavaSyntaxNode(
    override val tree: JavaTree,
    val delegate: TSNode,
) : TsSyntaxNode {
    override val text: String by lazy {
        tree.text
            .toByteArray()
            .copyOfRange(startIndex.toInt(), endIndex.toInt())
            .toString(Charsets.UTF_8)
    }

    override val typeId: UInt = delegate.symbol.toUInt()

    override val grammarId: UInt = delegate.grammarSymbol.toUInt()

    override val type: String = delegate.type

    override val grammarType: String = delegate.grammarType

    override val isNamed: Boolean = delegate.isNamed

    override val isMissing: Boolean = delegate.isMissing

    override val isExtra: Boolean = delegate.isExtra

    override val hasError: Boolean = delegate.hasError()

    override val isError: Boolean = delegate.isError

    override val parseState: UInt = delegate.parserState.toUInt()

    override val nextParseState: UInt = delegate.nextParserState.toUInt()

    override val startPosition: TsPoint = JavaPoint(delegate.startPoint)

    override val endPosition: TsPoint = JavaPoint(delegate.endPoint)

    override val startIndex: UInt = delegate.startByte.toUInt()

    override val endIndex: UInt = delegate.endByte.toUInt()

    override val parent: TsSyntaxNode? by lazy { delegate.parent?.nonNull()?.wrap(tree) }

    override val childCount: UInt by lazy { delegate.childCount.toUInt() }

    override val children: List<TsSyntaxNode> by lazy { 0U.rangeTo(childCount).mapNotNull { child(it) } }

    override val namedChildCount: UInt by lazy { delegate.namedChildCount.toUInt() }

    override val namedChildren: List<TsSyntaxNode> by lazy { 0U.rangeTo(namedChildCount).mapNotNull { namedChild(it) } }

    override val firstChild: TsSyntaxNode? by lazy { child(0U) }

    override val firstNamedChild: TsSyntaxNode? by lazy { namedChild(0U) }

    override val lastChild: TsSyntaxNode? by lazy { child(childCount - 1U) }

    override val lastNamedChild: TsSyntaxNode? by lazy { namedChild(namedChildCount - 1U) }

    override val nextSibling: TsSyntaxNode? by lazy { delegate.nextSibling?.nonNull()?.wrap(tree) }

    override val nextNamedSibling: TsSyntaxNode? by lazy { delegate.nextNamedSibling?.nonNull()?.wrap(tree) }

    override val previousSibling: TsSyntaxNode? by lazy { delegate.prevSibling?.nonNull()?.wrap(tree) }

    override val previousNamedSibling: TsSyntaxNode? by lazy { delegate.prevNamedSibling?.nonNull()?.wrap(tree) }

    override fun toString(): String = delegate.toString()

    override fun child(index: UInt): TsSyntaxNode? = delegate.getChild(index.toInt())?.nonNull()?.wrap(tree)

    override fun namedChild(index: UInt): TsSyntaxNode? = delegate.getNamedChild(index.toInt())?.nonNull()?.wrap(tree)

    override fun childForFieldName(fieldName: String): TsSyntaxNode? = delegate.getChildByFieldName(fieldName)?.nonNull()?.wrap(tree)

    override fun childForFieldId(fieldId: UInt): TsSyntaxNode? = delegate.getChildByFieldId(fieldId.toInt())?.nonNull()?.wrap(tree)

    override fun fieldNameForChild(childIndex: UInt): String? = delegate.getFieldNameForChild(childIndex.toInt())

    override fun fieldNameForNamedChild(namedChildIndex: UInt): String? = delegate.getFieldNameForNamedChild(namedChildIndex.toInt())

    override fun firstChildForIndex(index: UInt): TsSyntaxNode? = delegate.getFirstChildForByte(index.toInt())?.nonNull()?.wrap(tree)

    override fun firstNamedChildForIndex(index: UInt): TsSyntaxNode? =
        delegate.getFirstNamedChildForByte(index.toInt())?.nonNull()?.wrap(tree)

    override fun childWithDescendant(descendant: TsSyntaxNode): TsSyntaxNode? =
        delegate.getChildWithDescendant(descendant.inner())?.nonNull()?.wrap(tree)

    override fun descendantForIndex(index: UInt): TsSyntaxNode = descendantForIndex(index, index)

    override fun descendantForIndex(
        startIndex: UInt,
        endIndex: UInt,
    ): TsSyntaxNode = delegate.getDescendantForByteRange(startIndex.toInt(), endIndex.toInt()).wrap(tree)

    override fun namedDescendantForIndex(index: UInt): TsSyntaxNode = namedDescendantForIndex(index, index)

    override fun namedDescendantForIndex(
        startIndex: UInt,
        endIndex: UInt,
    ): TsSyntaxNode = delegate.getNamedDescendantForByteRange(startIndex.toInt(), endIndex.toInt()).wrap(tree)

    override fun descendantForPosition(position: TsPoint): TsSyntaxNode = descendantForPosition(position, position)

    override fun descendantForPosition(
        startPosition: TsPoint,
        endPosition: TsPoint,
    ): TsSyntaxNode = delegate.getDescendantForPointRange(startPosition.inner(), endPosition.inner()).wrap(tree)

    override fun namedDescendantForPosition(position: TsPoint): TsSyntaxNode = namedDescendantForPosition(position, position)

    override fun namedDescendantForPosition(
        startPosition: TsPoint,
        endPosition: TsPoint,
    ): TsSyntaxNode = delegate.getNamedDescendantForPointRange(startPosition.inner(), endPosition.inner()).wrap(tree)

    override fun descendantsOfType(
        types: List<String>,
        startPosition: TsPoint?,
        endPosition: TsPoint?,
    ): List<TsSyntaxNode> {
        val descendant = startPosition?.let { descendantForPosition(it, endPosition ?: it) } ?: this
        return listOf(
            listOf(descendant).filter { types.contains(it.type) },
            descendant.children.flatMap { it.descendantsOfType(types, startPosition, endPosition) },
        ).flatten()
    }

    override fun closest(types: List<String>): TsSyntaxNode? {
        if (types.contains(type)) return this
        return parent?.closest(types)
    }

    override fun walk(): TsTreeCursor = JavaTreeCursor(tree, TSTreeCursor(delegate))
}

fun TSNode.depth(): UInt = (parent?.depth() ?: 0U) + 1U

fun TSNode.nonNull(): TSNode? = if (isNull) null else this

private class JavaTreeCursor(
    tree: JavaTree,
    val delegate: TSTreeCursor,
) : TsTreeCursor {
    override val nodeType: String = node().type

    override val nodeTypeId: UInt = node().symbol.toUInt()

    override val nodeStateId: UInt = node().parserState.toUInt()

    override val nodeIsNamed: Boolean = node().isNamed

    override val nodeIsMissing: Boolean = node().isMissing

    override val startPosition: TsPoint = JavaPoint(node().startPoint)

    override val endPosition: TsPoint = JavaPoint(node().endPoint)

    override val startIndex: UInt = node().startByte.toUInt()

    override val endIndex: UInt = node().endByte.toUInt()

    override val currentNode: TsSyntaxNode = node().wrap(tree)

    override val currentFieldName: String = delegate.currentFieldName()

    override val currentFieldId: UInt = delegate.currentFieldId().toUInt()

    override val currentDepth: UInt = node().depth()

    override fun reset(node: TsSyntaxNode) = delegate.reset(node.inner())

    override fun gotoParent(): Boolean = delegate.gotoParent()

    override fun gotoFirstChild(): Boolean = delegate.gotoFirstChild()

    override fun gotoLastChild(): Boolean = delegate.gotoFirstChildForByte(node().endByte - 1) != -1

    override fun gotoFirstChildForIndex(goalIndex: UInt): Boolean = delegate.gotoFirstChildForByte(goalIndex.toInt()) != -1

    override fun gotoFirstChildForPosition(goalPosition: TsPoint): Boolean = delegate.gotoFirstChildForPoint(goalPosition.inner()) != -1

    override fun gotoNextSibling(): Boolean = delegate.gotoNextSibling()

    override fun gotoPreviousSibling(): Boolean = delegate.gotoPreviousSibling()

    private fun node(): TSNode = delegate.currentNode()
}

private class JavaPoint(
    val delegate: TSPoint,
) : TsPoint {
    override val row: UInt = delegate.row.toUInt()
    override val column: UInt = delegate.column.toUInt()
}

private class JavaRange(
    delegate: TSRange,
) : TsRange {
    override val startIndex: UInt = delegate.startByte.toUInt()
    override val endIndex: UInt = delegate.endByte.toUInt()
    override val startPosition: TsPoint = JavaPoint(delegate.startPoint)
    override val endPosition: TsPoint = JavaPoint(delegate.endPoint)
}

private class JavaQuery(
    val delegate: TSQuery,
) : TsQuery {
    override fun captures(node: TsSyntaxNode): List<TsQueryCapture> =
        TSQueryCursor()
            .captures
            .asSequence()
            .flatMap { it.captures.asSequence() }
            .map { JavaQueryCapture(node.innerTree(), it) }
            .toList()

    override fun matches(node: TsSyntaxNode): List<TsQueryMatch> =
        TSQueryCursor()
            .matches
            .asSequence()
            .map { JavaQueryMatch(node.innerTree(), it) }
            .toList()

    override fun disableCapture(captureName: String) = delegate.disableCapture(captureName)

    override fun disablePattern(patternIndex: UInt) = delegate.disablePattern(patternIndex.toInt())

    override fun isPatternGuaranteedAtStep(byteOffset: UInt): Boolean = delegate.isPatternGuaranteedAtStep(byteOffset.toInt())

    override fun isPatternRooted(patternIndex: UInt): Boolean = delegate.isPatternRooted(patternIndex.toInt())

    override fun isPatternNonLocal(patternIndex: UInt): Boolean = delegate.isPatterNonLocal(patternIndex.toInt())

    override fun startIndexForPattern(patternIndex: UInt): UInt = delegate.getStartByteForPattern(patternIndex.toInt()).toUInt()

    override fun endIndexForPattern(patternIndex: UInt): UInt = delegate.getEndByteForPattern(patternIndex.toInt()).toUInt()

    private class JavaQueryCapture(
        tree: JavaTree,
        delegate: TSQueryCapture,
    ) : TsQueryCapture {
        override val node: TsSyntaxNode = delegate.node.wrap(tree)
    }

    private class JavaQueryMatch(
        tree: JavaTree,
        delegate: TSQueryMatch,
    ) : TsQueryMatch {
        override val pattern: UInt = delegate.patternIndex.toUInt()

        override val captures: List<TsQueryCapture> = delegate.captures.toList().map { JavaQueryCapture(tree, it) }
    }
}

private class JavaLookaheadIterator : TsLookaheadIterator {
    // JVM stub: some Java tree-sitter bindings don't expose a lookahead
    // iterator implementation. Provide conservative defaults so the JVM
    // compilation succeeds and callers get safe values.
    override val currentTypeId: UInt = 0U

    override val currentType: String = "ERROR"

    override fun reset(
        language: TsLanguage,
        stateId: UInt,
    ): Boolean = false

    override fun resetState(stateId: UInt): Boolean = false
}

private class JavaLanguage(
    val delegate: TSLanguage,
) : TsLanguage

private fun TsLanguage.inner(): TSLanguage = (this as JavaLanguage).delegate

private fun TsSyntaxNode.inner(): TSNode = (this as JavaSyntaxNode).delegate

private fun TsSyntaxNode.innerTree(): JavaTree = (this as JavaSyntaxNode).tree

private fun TSNode.wrap(tree: JavaTree): JavaSyntaxNode = JavaSyntaxNode(tree, this)

fun TsPoint.inner(): TSPoint = (this as JavaPoint).delegate

private class JavaFactory : TsFactory {
    override suspend fun createParser(): TsParser = JavaParser(TSParser())

    override fun createQuery(
        language: TsLanguage,
        source: String,
    ): TsQuery = JavaQuery(TSQuery(language.inner(), source))

    override fun createLookaheadIterator(
        language: TsLanguage,
        state: UInt,
    ): TsLookaheadIterator = JavaLookaheadIterator()

    override suspend fun loadLanguage(location: String): TsLanguage? =
        TSLanguage.load(location, "tree_sitter_hope")?.let { JavaLanguage(it) }
}

actual fun factory(): TsFactory = JavaFactory()

actual fun sharedLibraryLocation(): String {
    val os = getProperty("os.name").lowercase()
    val extension = when {
        os.contains("win") -> ".dll"
        os.contains("mac") || os.contains("darwin") -> ".dylib"
        else -> ".so"
    }
    return "../tree-sitter-hope/hope$extension"
}
