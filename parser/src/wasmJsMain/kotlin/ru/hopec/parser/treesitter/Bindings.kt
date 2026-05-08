@file:OptIn(ExperimentalWasmJsInterop::class)
@file:JsModule("web-tree-sitter")

package ru.hopec.parser.treesitter

import kotlin.js.Promise

open external class Parser {
    fun parse(
        input: JsAny,
        oldTree: Tree?,
        options: Options?,
    ): Tree

    fun getIncludedRanges(): JsArray<Range>

    fun getTimeoutMicros(): UInt

    fun setTimeoutMicros(timeout: UInt)

    fun reset()

    fun getLanguage(): Language

    fun setLanguage(language: Language?)

    fun printDotGraphs(
        enabled: Boolean?,
        fd: UInt?,
    )

    companion object {
        fun init(): Promise<JsAny>
    }
}

open external class Options {
    val bufferSize: UInt?
    val includedRanges: JsArray<Range>?
    val progressCallback: ((index: UInt, hasError: Boolean) -> Boolean)?
}

open external class Point {
    val row: UInt
    val column: UInt
}

open external class Range : JsAny {
    val startIndex: UInt
    val endIndex: UInt
    val startPosition: Point
    val endPosition: Point
}

external interface SyntaxNode : JsAny {
    val tree: Tree
    val id: UInt
    val typeId: UInt
    val grammarId: UInt
    val type: String
    val grammarType: String
    val isNamed: Boolean
    val isMissing: Boolean
    val isExtra: Boolean
    val hasChanges: Boolean
    val hasError: Boolean
    val isError: Boolean
    val text: String
    val parseState: UInt
    val nextParseState: UInt
    val startPosition: Point
    val endPosition: Point
    val startIndex: UInt
    val endIndex: UInt
    val parent: SyntaxNode?
    val children: JsArray<SyntaxNode>
    val namedChildren: JsArray<SyntaxNode>
    val childCount: UInt
    val namedChildCount: UInt
    val firstChild: SyntaxNode?
    val firstNamedChild: SyntaxNode?
    val lastChild: SyntaxNode?
    val lastNamedChild: SyntaxNode?
    val nextSibling: SyntaxNode?
    val nextNamedSibling: SyntaxNode?
    val previousSibling: SyntaxNode?
    val previousNamedSibling: SyntaxNode?
    val descendantCount: UInt
    val fields: JsArray<JsAny>

    override fun toString(): String

    fun toJSON(): JsAny

    fun child(index: UInt): SyntaxNode?

    fun namedChild(index: UInt): SyntaxNode?

    fun childForFieldName(fieldName: String): SyntaxNode?

    fun childForFieldId(fieldId: UInt): SyntaxNode?

    fun fieldNameForChild(childIndex: UInt): String?

    fun fieldNameForNamedChild(namedChildIndex: UInt): String?

    fun childrenForFieldName(fieldName: String): JsArray<SyntaxNode>

    fun childrenForFieldId(fieldId: UInt): JsArray<SyntaxNode>

    fun firstChildForIndex(index: UInt): SyntaxNode?

    fun firstNamedChildForIndex(index: UInt): SyntaxNode?

    fun childWithDescendant(descendant: SyntaxNode): SyntaxNode?

    fun descendantForIndex(index: UInt): SyntaxNode

    fun descendantForIndex(
        startIndex: UInt,
        endIndex: UInt,
    ): SyntaxNode

    fun namedDescendantForIndex(index: UInt): SyntaxNode

    fun namedDescendantForIndex(
        startIndex: UInt,
        endIndex: UInt,
    ): SyntaxNode

    fun descendantForPosition(position: Point): SyntaxNode

    fun descendantForPosition(
        startPosition: Point,
        endPosition: Point,
    ): SyntaxNode

    fun namedDescendantForPosition(position: Point): SyntaxNode

    fun namedDescendantForPosition(
        startPosition: Point,
        endPosition: Point,
    ): SyntaxNode

    fun descendantsOfType(
        types: JsArray<JsString>,
        startPosition: Point?,
        endPosition: Point?,
    ): JsArray<SyntaxNode>

    fun closest(types: JsArray<JsString>): SyntaxNode?

    fun walk(): TreeCursor
}

external interface TreeCursor {
    val nodeType: String
    val nodeTypeId: UInt
    val nodeStateId: UInt
    val nodeText: String
    val nodeIsNamed: Boolean
    val nodeIsMissing: Boolean
    val startPosition: Point
    val endPosition: Point
    val startIndex: UInt
    val endIndex: UInt
    val currentNode: SyntaxNode
    val currentFieldName: String
    val currentFieldId: UInt
    val currentDepth: UInt
    val currentDescendantIndex: UInt

    fun reset(node: SyntaxNode)

    fun resetTo(cursor: TreeCursor)

    fun gotoParent(): Boolean

    fun gotoFirstChild(): Boolean

    fun gotoLastChild(): Boolean

    fun gotoFirstChildForIndex(goalIndex: UInt): Boolean

    fun gotoFirstChildForPosition(goalPosition: Point): Boolean

    fun gotoNextSibling(): Boolean

    fun gotoPreviousSibling(): Boolean

    fun gotoDescendant(goalDescendantIndex: UInt)
}

external interface Tree {
    val rootNode: SyntaxNode

    fun rootNodeWithOffset(
        offsetBytes: UInt,
        offsetExtent: Point,
    ): SyntaxNode

    fun walk(): TreeCursor

    fun getChangedRanges(other: Tree): JsArray<Range>

    fun getIncludedRanges(): JsArray<Range>
}

external interface QueryCapture : JsAny {
    val name: String
    val node: SyntaxNode
}

external interface QueryMatch : JsAny {
    val pattern: UInt
    val captures: JsArray<QueryCapture>
}

open external class QueryOptions {
    val startPosition: Point?
    val endPosition: Point?
    val startIndex: UInt?
    val endIndex: UInt?
    val matchLimit: UInt?
    val maxStartDepth: UInt?
    val progressCallback: (index: UInt) -> Boolean
}

open external class Query {
    val matchLimit: UInt

    constructor(language: Language, source: String)

    fun captures(
        node: SyntaxNode,
        options: QueryOptions?,
    ): JsArray<QueryCapture>

    fun matches(
        node: SyntaxNode,
        options: QueryOptions?,
    ): JsArray<QueryMatch>

    fun disableCapture(captureName: String)

    fun disablePattern(patternIndex: UInt)

    fun isPatternGuaranteedAtStep(byteOffset: UInt): Boolean

    fun isPatternRooted(patternIndex: UInt): Boolean

    fun isPatternNonLocal(patternIndex: UInt): Boolean

    fun startIndexForPattern(patternIndex: UInt): UInt

    fun endIndexForPattern(patternIndex: UInt): UInt

    fun didExceedMatchLimit(): Boolean
}

open external class LookaheadIterator {
    val currentTypeId: UInt
    val currentType: String

    constructor(language: Language, state: UInt)

    fun reset(
        language: Language,
        stateId: UInt,
    ): Boolean

    fun resetState(stateId: UInt): Boolean
}

open external class Language : JsAny {
    val language: JsAny

    companion object {
        fun load(input: String): Promise<Language?>
    }
}
