@file:OptIn(ExperimentalWasmJsInterop::class)

package ru.hopec.parser.treesitter

import kotlinx.coroutines.await

private class JsParser(
    val delegate: Parser,
) : TsParser {
    override fun parse(input: String): TsTree = JsTree(delegate.parse(input.toJsString(), null, null))

    override fun getIncludedRanges(): List<TsRange> = delegate.getIncludedRanges().toList().map { JsRange(it) }

    override fun reset() = delegate.reset()

    override fun getLanguage(): TsLanguage = JsLanguage(delegate.getLanguage())

    override fun setLanguage(language: TsLanguage?) = delegate.setLanguage(language?.inner())
}

private class JsPoint(
    val delegate: Point,
) : TsPoint {
    override val row: UInt = delegate.row
    override val column: UInt = delegate.column
}

private class JsRange(
    delegate: Range,
) : TsRange {
    override val startIndex: UInt = delegate.startIndex
    override val endIndex: UInt = delegate.endIndex
    override val startPosition: TsPoint = JsPoint(delegate.startPosition)
    override val endPosition: TsPoint = JsPoint(delegate.endPosition)
}

private class JsSyntaxNode(
    val delegate: SyntaxNode,
) : TsSyntaxNode {
    override val tree: TsTree by lazy { JsTree(delegate.tree) }

    override val text: String = delegate.text

    override val typeId: UInt = delegate.typeId

    override val grammarId: UInt = delegate.grammarId

    override val type: String = delegate.type

    override val grammarType: String = delegate.grammarType

    override val isNamed: Boolean = delegate.isNamed

    override val isMissing: Boolean = delegate.isMissing

    override val isExtra: Boolean = delegate.isExtra

    override val hasError: Boolean = delegate.hasError

    override val isError: Boolean = delegate.isError

    override val parseState: UInt = delegate.parseState

    override val nextParseState: UInt = delegate.nextParseState

    override val startPosition: TsPoint = JsPoint(delegate.startPosition)

    override val endPosition: TsPoint = JsPoint(delegate.endPosition)

    override val startIndex: UInt = delegate.startIndex

    override val endIndex: UInt = delegate.endIndex

    override val parent: TsSyntaxNode? by lazy { delegate.parent?.wrap() }

    override val children: List<TsSyntaxNode> by lazy { delegate.children.toList().map(SyntaxNode::wrap) }

    override val namedChildren: List<TsSyntaxNode> by lazy { delegate.namedChildren.toList().map(SyntaxNode::wrap) }

    override val childCount: UInt = delegate.childCount

    override val namedChildCount: UInt = delegate.namedChildCount

    override val firstChild: TsSyntaxNode? by lazy { delegate.firstChild?.wrap() }

    override val firstNamedChild: TsSyntaxNode? by lazy { delegate.firstNamedChild?.wrap() }

    override val lastChild: TsSyntaxNode? by lazy { delegate.lastChild?.wrap() }

    override val lastNamedChild: TsSyntaxNode? by lazy { delegate.lastNamedChild?.wrap() }

    override val nextSibling: TsSyntaxNode? by lazy { delegate.nextSibling?.wrap() }

    override val nextNamedSibling: TsSyntaxNode? by lazy { delegate.nextNamedSibling?.wrap() }

    override val previousSibling: TsSyntaxNode? by lazy { delegate.previousSibling?.wrap() }

    override val previousNamedSibling: TsSyntaxNode? by lazy { delegate.previousNamedSibling?.wrap() }

    override fun toString(): String = delegate.toString()

    override fun child(index: UInt): TsSyntaxNode? = delegate.child(index)?.wrap()

    override fun namedChild(index: UInt): TsSyntaxNode? = delegate.namedChild(index)?.wrap()

    override fun childForFieldName(fieldName: String): TsSyntaxNode? = delegate.childForFieldName(fieldName)?.wrap()

    override fun childForFieldId(fieldId: UInt): TsSyntaxNode? = delegate.childForFieldId(fieldId)?.wrap()

    override fun fieldNameForChild(childIndex: UInt): String? = delegate.fieldNameForChild(childIndex)

    override fun fieldNameForNamedChild(namedChildIndex: UInt): String? = delegate.fieldNameForNamedChild(namedChildIndex)

    override fun firstChildForIndex(index: UInt): TsSyntaxNode? = delegate.firstChildForIndex(index)?.wrap()

    override fun firstNamedChildForIndex(index: UInt): TsSyntaxNode? = delegate.firstNamedChildForIndex(index)?.wrap()

    override fun childWithDescendant(descendant: TsSyntaxNode): TsSyntaxNode? = delegate.childWithDescendant(descendant.inner())?.wrap()

    override fun descendantForIndex(index: UInt): TsSyntaxNode = delegate.descendantForIndex(index).wrap()

    override fun descendantForIndex(
        startIndex: UInt,
        endIndex: UInt,
    ): TsSyntaxNode = delegate.descendantForIndex(startIndex, endIndex).wrap()

    override fun namedDescendantForIndex(index: UInt): TsSyntaxNode = delegate.namedDescendantForIndex(index).wrap()

    override fun namedDescendantForIndex(
        startIndex: UInt,
        endIndex: UInt,
    ): TsSyntaxNode = delegate.namedDescendantForIndex(startIndex, endIndex).wrap()

    override fun descendantForPosition(position: TsPoint): TsSyntaxNode = delegate.descendantForPosition(position.inner()).wrap()

    override fun descendantForPosition(
        startPosition: TsPoint,
        endPosition: TsPoint,
    ): TsSyntaxNode = delegate.descendantForPosition(startPosition.inner(), endPosition.inner()).wrap()

    override fun namedDescendantForPosition(position: TsPoint): TsSyntaxNode = delegate.namedDescendantForPosition(position.inner()).wrap()

    override fun namedDescendantForPosition(
        startPosition: TsPoint,
        endPosition: TsPoint,
    ): TsSyntaxNode = delegate.namedDescendantForPosition(startPosition.inner(), endPosition.inner()).wrap()

    override fun descendantsOfType(
        types: List<String>,
        startPosition: TsPoint?,
        endPosition: TsPoint?,
    ): List<TsSyntaxNode> =
        delegate
            .descendantsOfType(
                types.map { it.toJsString() }.toJsArray(),
                startPosition?.inner(),
                endPosition?.inner(),
            ).toList()
            .map(
                SyntaxNode::wrap,
            )

    override fun closest(types: List<String>): TsSyntaxNode? = delegate.closest(types.map { it.toJsString() }.toJsArray())?.wrap()

    override fun walk(): TsTreeCursor = JsTreeCursor(delegate.walk())
}

private class JsTreeCursor(
    val delegate: TreeCursor,
) : TsTreeCursor {
    override val nodeType: String = delegate.nodeType

    override val nodeTypeId: UInt = delegate.nodeTypeId

    override val nodeStateId: UInt = delegate.nodeStateId

    override val nodeIsNamed: Boolean = delegate.nodeIsNamed

    override val nodeIsMissing: Boolean = delegate.nodeIsMissing

    override val startPosition: TsPoint = JsPoint(delegate.startPosition)

    override val endPosition: TsPoint = JsPoint(delegate.endPosition)

    override val startIndex: UInt = delegate.startIndex

    override val endIndex: UInt = delegate.endIndex

    override val currentNode: TsSyntaxNode = delegate.currentNode.wrap()

    override val currentFieldName: String = delegate.currentFieldName

    override val currentFieldId: UInt = delegate.currentFieldId

    override val currentDepth: UInt = delegate.currentDepth

    override fun reset(node: TsSyntaxNode) = delegate.reset(node.inner())

    override fun gotoParent(): Boolean = delegate.gotoParent()

    override fun gotoFirstChild(): Boolean = delegate.gotoFirstChild()

    override fun gotoLastChild(): Boolean = delegate.gotoLastChild()

    override fun gotoFirstChildForIndex(goalIndex: UInt): Boolean = delegate.gotoFirstChildForIndex(goalIndex)

    override fun gotoFirstChildForPosition(goalPosition: TsPoint): Boolean = delegate.gotoFirstChildForPosition(goalPosition.inner())

    override fun gotoNextSibling(): Boolean = delegate.gotoNextSibling()

    override fun gotoPreviousSibling(): Boolean = delegate.gotoPreviousSibling()
}

class JsTree(
    val delegate: Tree,
) : TsTree {
    override val rootNode: TsSyntaxNode = delegate.rootNode.wrap()

    override fun rootNodeWithOffset(
        offsetBytes: UInt,
        offsetExtent: TsPoint,
    ): TsSyntaxNode = delegate.rootNodeWithOffset(offsetBytes, offsetExtent.inner()).wrap()

    override fun walk(): TsTreeCursor = JsTreeCursor(delegate.walk())

    override fun getIncludedRanges(): List<TsRange> = delegate.getIncludedRanges().toList().map { JsRange(it) }
}

private class JsQueryCapture(
    delegate: QueryCapture,
) : TsQueryCapture {
    override val node: TsSyntaxNode = delegate.node.wrap()
}

private class JsQueryMatch(
    delegate: QueryMatch,
) : TsQueryMatch {
    override val pattern: UInt = delegate.pattern

    override val captures: List<TsQueryCapture> = delegate.captures.toList().map { JsQueryCapture(it) }
}

private class JsQuery(
    val delegate: Query,
) : TsQuery {
    override fun captures(node: TsSyntaxNode): List<TsQueryCapture> =
        delegate.captures(node.inner(), null).toList().map { JsQueryCapture(it) }

    override fun matches(node: TsSyntaxNode): List<TsQueryMatch> = delegate.matches(node.inner(), null).toList().map { JsQueryMatch(it) }

    override fun disableCapture(captureName: String) = delegate.disableCapture(captureName)

    override fun disablePattern(patternIndex: UInt) = delegate.disablePattern(patternIndex)

    override fun isPatternGuaranteedAtStep(byteOffset: UInt): Boolean = delegate.isPatternGuaranteedAtStep(byteOffset)

    override fun isPatternRooted(patternIndex: UInt): Boolean = delegate.isPatternRooted(patternIndex)

    override fun isPatternNonLocal(patternIndex: UInt): Boolean = delegate.isPatternNonLocal(patternIndex)

    override fun startIndexForPattern(patternIndex: UInt): UInt = delegate.startIndexForPattern(patternIndex)

    override fun endIndexForPattern(patternIndex: UInt): UInt = delegate.endIndexForPattern(patternIndex)
}

private class JsLookaheadIterator(
    val delegate: LookaheadIterator,
) : TsLookaheadIterator {
    override val currentTypeId: UInt = delegate.currentTypeId

    override val currentType: String = delegate.currentType

    override fun reset(
        language: TsLanguage,
        stateId: UInt,
    ): Boolean = delegate.reset(language.inner(), stateId)

    override fun resetState(stateId: UInt): Boolean = delegate.resetState(stateId)
}

private class JsLanguage(
    val delegate: Language,
) : TsLanguage

private fun TsLanguage.inner(): Language = (this as JsLanguage).delegate

private fun TsPoint.inner(): Point = (this as JsPoint).delegate

private fun TsSyntaxNode.inner(): SyntaxNode = (this as JsSyntaxNode).delegate

private fun SyntaxNode.wrap(): JsSyntaxNode = JsSyntaxNode(this)

private class JsFactory : TsFactory {
    override suspend fun createParser(): TsParser {
        Parser.init().await<Any>()
        return JsParser(Parser())
    }

    override fun createQuery(
        language: TsLanguage,
        source: String,
    ): TsQuery = JsQuery(Query(language.inner(), source))

    override fun createLookaheadIterator(
        language: TsLanguage,
        state: UInt,
    ): TsLookaheadIterator = JsLookaheadIterator(LookaheadIterator(language.inner(), state))

    override suspend fun loadLanguage(location: String): TsLanguage? {
        Parser.init().await<Any>()
        return Language.load(location).await<Language?>()?.let { JsLanguage(it) }
    }
}

actual fun factory(): TsFactory = JsFactory()

actual fun sharedLibraryLocation(): String = "./kotlin/lib/tree-sitter-hope.wasm"
