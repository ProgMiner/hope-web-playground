@file:OptIn(ExperimentalWasmJsInterop::class)

package ru.hopec.parser.treesitter

open external class Parser {
    /**
     * Parse UTF8 text into a syntax tree.
     *
     * @param input - The text to parse, either as a String or a custom input function
     * that provides text chunks. If providing a function, it should return text chunks
     * based on byte index and position.
     *
     * @param oldTree - An optional previous syntax tree from the same document.
     * If provided and the document has changed, you must first edit this tree using
     * {@link Parser.Tree.edit} to match the new text.
     *
     * @param options - Optional parsing settings:
     * - bufferSize: Size of internal parsing buffer
     * - includedRanges: Array of ranges to parse within the input
     * - progressCallback: A callback that receives the current parse state
     *
     * @returns A syntax tree representing the parsed text
     *
     * @throws JsException May fail if no language has been set or parsing was halted.
     */
    fun parse(input: JsAny, oldTree: Tree?, options: Options?): Tree

    /**
     * Get the ranges of text that the parser will include when parsing.
     *
     * @returns An array of ranges that will be included in parsing
     */
    fun getIncludedRanges(): JsArray<Range>

    /**
     * Get the duration in microseconds that parsing is allowed to take.
     *
     * This timeout can be set via {@link Parser.setTimeoutMicros}.
     *
     * @returns The parsing timeout in microseconds
     *
     * @deprecated Use the {@link progressCallback}
     */
    fun getTimeoutMicros(): UInt

    /**
     * Set the maximum duration that parsing is allowed to take before halting.
     *
     * If parsing takes longer than this, it will halt early, returning null.
     *
     * @param timeout - The maximum parsing duration in microseconds
     *
     * @deprecated Use the {@link progressCallback}
     */
    fun setTimeoutMicros(timeout: UInt)

    /**
     * Instruct the parser to start the next parse from the beginning.
     *
     * If the parser previously failed, it will resume where it left off
     * on the next parse by default. Call this method if you want to parse
     * a different document instead of resuming.
     */
    fun reset()

    /**
     * Get the parser's current language
     */
    fun getLanguage(): Language

    /**
     * Set the language that the parser should use for parsing.
     *
     * The language must be compatible with the version of tree-sitter
     * being used. A version mismatch will prevent the language from
     * being assigned successfully.
     *
     * @param language - The language to use for parsing
     */
    fun setLanguage(language: Language?)

    /**
     * Set the destination to which the parser should write debugging graphs during parsing.
     *
     * The graphs are formatted in the DOT language. You may want to pipe these graphs
     * directly to a 'dot' process to generate SVG output.
     *
     * @param enabled - Whether to enable or disable graph output
     * @param fd - Optional file descriptor for the output
     */
    fun printDotGraphs(enabled: Boolean?, fd: UInt?)
}

open external class Options {
    /** Size of the internal parsing buffer */
    val bufferSize: UInt?

    /** Array of ranges to include when parsing the input */
    val includedRanges: JsArray<Range>?

    /**
     * A callback that receives the parse state during parsing.
     *
     * @param index - The byte offset in the document that the parser is currently at
     * @param hasError - Indicates whether the parser has encountered an error during parsing
     *
     * @returns `true` to stop parsing or `false` to continue
     */
    val progressCallback: ((index: UInt, hasError: Boolean) -> Boolean)?
}

/**
 * A position in a multi-line text document, in terms of rows and columns.
 * Both values are zero-based.
 */
open external class Point {
    val row: UInt
    val column: UInt
}

/**
 * A range of positions in a multi-line text document, specified both in
 * terms of byte offsets and row/column positions.
 */
open external class Range : JsAny {
    val startIndex: UInt
    val endIndex: UInt
    val startPosition: Point
    val endPosition: Point
}

/** The syntax tree that contains this node */
external interface SyntaxNode : JsAny {
    /** The syntax tree that contains this node */
    val tree: Tree

    /**
     * A unique numeric identifier for this node.
     * Within a given syntax tree, no two nodes have the same id.
     * If a new tree is created based on an older tree and reuses
     * a node, that node will have the same id in both trees.
     */
    val id: UInt

    /**
     * This node's type as a numeric id
     */
    val typeId: UInt

    /**
     * This node's type as a numeric id as it appears in the grammar,
     * ignoring aliases
     */
    val grammarId: UInt

    /**
     * This node's type as a String
     */
    val type: String

    /**
     * This node's symbol name as it appears in the grammar,
     * ignoring aliases
     */
    val grammarType: String

    /**
     * Whether this node is named.
     * Named nodes correspond to named rules in the grammar,
     * whereas anonymous nodes correspond to String literals in the grammar.
     */
    val isNamed: Boolean

    /**
     * Whether this node is missing.
     * Missing nodes are inserted by the parser in order to
     * recover from certain kinds of syntax errors.
     */
    val isMissing: Boolean

    /**
     * Whether this node is extra.
     * Extra nodes represent things like comments, which are not
     * required by the grammar but can appear anywhere.
     */
    val isExtra: Boolean

    /**
     * Whether this node has been edited
     */
    val hasChanges: Boolean

    /**
     * Whether this node represents a syntax error or contains
     * any syntax errors within it
     */
    val hasError: Boolean

    /**
     * Whether this node represents a syntax error.
     * Syntax errors represent parts of the code that could not
     * be incorporated into a valid syntax tree.
     */
    val isError: Boolean

    /** The text content for this node from the source code */
    val text: String

    /** The parse state of this node */
    val parseState: UInt

    /** The parse state that follows this node */
    val nextParseState: UInt

    /** The position where this node starts in terms of rows and columns */
    val startPosition: Point

    /** The position where this node ends in terms of rows and columns */
    val endPosition: Point

    /** The byte offset where this node starts */
    val startIndex: UInt

    /** The byte offset where this node ends */
    val endIndex: UInt

    /**
     * This node's immediate parent.
     * For iterating over ancestors, prefer using {@link childWithDescendant}
     */
    val parent: SyntaxNode?

    /** JsArray of all child nodes */
    val children: JsArray<SyntaxNode>

    /** JsArray of all named child nodes */
    val namedChildren: JsArray<SyntaxNode>

    /** The UInt of children this node has */
    val childCount: UInt

    /**
     * The UInt of named children this node has.
     * @see {@link isNamed}
     */
    val namedChildCount: UInt

    /** The first child of this node */
    val firstChild: SyntaxNode?

    /** The first named child of this node */
    val firstNamedChild: SyntaxNode?

    /** The last child of this node */
    val lastChild: SyntaxNode?

    /** The last child of this node */
    val lastNamedChild: SyntaxNode?

    /** This node's next sibling */
    val nextSibling: SyntaxNode?

    /** This node's next named sibling */
    val nextNamedSibling: SyntaxNode?

    /** This node's previous sibling */
    val previousSibling: SyntaxNode?

    /** This node's previous named sibling */
    val previousNamedSibling: SyntaxNode?

    /**
     * The UInt of descendants this node has, including itself
     */
    val descendantCount: UInt

    /**
     * The names of extra fields available in the subclass, if any.
     */
    val fields: JsArray<JsAny>

    /**
     * Convert this node to its String representation
     */
    override fun toString(): String

    /**
     * Convert this node to its JSON representation
     */
    fun toJSON(): JsAny

    /**
     * Get the node's child at the given index, where zero represents the first child.
     *
     * Note: While fairly fast, this method's cost is technically log(i).
     * For iterating over many children, prefer using the children JsArray.
     *
     * @param index - Zero-based index of the child to retrieve
     * @returns The child node, or null if none exists at the given index
     */
    fun child(index: UInt): SyntaxNode?

    /**
     * Get this node's named child at the given index.
     *
     * Note: While fairly fast, this method's cost is technically log(i).
     * For iterating over many children, prefer using the namedChildren JsArray.
     *
     * @param index - Zero-based index of the named child to retrieve
     * @returns The named child node, or null if none exists at the given index
     */
    fun namedChild(index: UInt): SyntaxNode?

    /**
     * Get the first child with the given field name.
     *
     * For fields that may have multiple children, use childrenForFieldName instead.
     *
     * @param fieldName - The field name to search for
     * @returns The child node, or null if no child has the given field name
     */
    fun childForFieldName(fieldName: String): SyntaxNode?

    /**
     * Get this node's child with the given numerical field id.
     *
     * Field IDs can be obtained from field names using the parser's language object.
     *
     * @param fieldId - The field ID to search for
     * @returns The child node, or null if no child has the given field ID
     */
    fun childForFieldId(fieldId: UInt): SyntaxNode?

    /**
     * Get the field name of the child at the given index
     *
     * @param childIndex - Zero-based index of the child
     * @returns The field name, or null if the child has no field name
     */
    fun fieldNameForChild(childIndex: UInt): String?

    /**
     * Get the field name of the named child at the given index
     *
     * @param namedChildIndex - Zero-based index of the named child
     * @returns The field name, or null if the named child has no field name
     */
    fun fieldNameForNamedChild(namedChildIndex: UInt): String?

    /**
     * Get all children that have the given field name
     *
     * @param fieldName - The field name to search for
     * @returns JsArray of child nodes with the given field name
     */
    fun childrenForFieldName(fieldName: String): JsArray<SyntaxNode>

    /**
     * Get all children that have the given field ID
     *
     * @param fieldId - The field ID to search for
     * @returns JsArray of child nodes with the given field ID
     */
    fun childrenForFieldId(fieldId: UInt): JsArray<SyntaxNode>

    /**
     * Get the node's first child that extends beyond the given byte offset
     *
     * @param index - The byte offset to search from
     * @returns The first child extending beyond the offset, or null if none found
     */
    fun firstChildForIndex(index: UInt): SyntaxNode?

    /**
     * Get the node's first named child that extends beyond the given byte offset
     *
     * @param index - The byte offset to search from
     * @returns The first named child extending beyond the offset, or null if none found
     */
    fun firstNamedChildForIndex(index: UInt): SyntaxNode?

    /**
     * Get the immediate child that contains the given descendant node.
     * Note that this can return the descendant itself if it is an immediate child.
     *
     * @param descendant - The descendant node to find the parent of
     * @returns The child containing the descendant, or null if not found
     */
    fun childWithDescendant(descendant: SyntaxNode): SyntaxNode?

    /**
     * Get the smallest node within this node that spans the given byte offset.
     *
     * @param index - The byte offset to search for
     * @returns The smallest node spanning the offset
     */
    fun descendantForIndex(index: UInt): SyntaxNode

    /**
     * Get the smallest node within this node that spans the given byte range.
     *
     * @param startIndex - The starting byte offset
     * @param endIndex - The ending byte offset
     * @returns The smallest node spanning the range
     */
    fun descendantForIndex(startIndex: UInt, endIndex: UInt): SyntaxNode

    /**
     * Get the smallest named node within this node that spans the given byte offset.
     *
     * @param index - The byte offset to search for
     * @returns The smallest named node spanning the offset
     */
    fun namedDescendantForIndex(index: UInt): SyntaxNode

    /**
     * Get the smallest named node within this node that spans the given byte range.
     *
     * @param startIndex - The starting byte offset
     * @param endIndex - The ending byte offset
     * @returns The smallest named node spanning the range
     */
    fun namedDescendantForIndex(startIndex: UInt, endIndex: UInt): SyntaxNode

    /**
     * Get the smallest node within this node that spans the given position.
     * When only one position is provided, it's used as both start and end.
     *
     * @param position - The point to search for
     * @returns The smallest node spanning the position
     */
    fun descendantForPosition(position: Point): SyntaxNode

    /**
     * Get the smallest node within this node that spans the given position range.
     *
     * @param startPosition - The starting position
     * @param endPosition - The ending position
     * @returns The smallest node spanning the range
     */
    fun descendantForPosition(startPosition: Point, endPosition: Point): SyntaxNode

    /**
     * Get the smallest named node within this node that spans the given position.
     * When only one position is provided, it's used as both start and end.
     *
     * @param position - The point to search for
     * @returns The smallest named node spanning the position
     */
    fun namedDescendantForPosition(position: Point): SyntaxNode

    /**
     * Get the smallest named node within this node that spans the given position range.
     *
     * @param startPosition - The starting position
     * @param endPosition - The ending position
     * @returns The smallest named node spanning the range
     */
    fun namedDescendantForPosition(startPosition: Point, endPosition: Point): SyntaxNode

    /**
     * Get all descendants of this node that have the given type(s)
     *
     * @param types - A String or JsArray of Strings of node types to find
     * @param startPosition - Optional starting position to search from
     * @param endPosition - Optional ending position to search to
     * @returns JsArray of descendant nodes matching the given types
     */
    fun descendantsOfType(types: JsArray<JsString>, startPosition: Point?, endPosition: Point?): JsArray<SyntaxNode>

    /**
     * Find the closest ancestor of the current node that matches the given type(s).
     *
     * Starting from the node's parent, walks up the tree until it finds a node
     * whose type matches any of the given types.
     *
     * @example
     * const property = tree.rootNode.descendantForIndex(5),
     * // Find closest unary expression ancestor
     * const unary = property.closest('unary_expression'),
     * // Find closest binary or call expression ancestor
     * const expr = property.closest(['binary_expression', 'call_expression']),
     *
     * @param types - A String or JsArray of Strings representing the node types to search for
     * @returns The closest matching ancestor node, or null if none found
     * @throws Exception If the argument is not a String or JsArray of Strings
     */
    fun closest(types: JsArray<JsString>): SyntaxNode?

    /**
     * Create a new TreeCursor starting from this node.
     *
     * @returns A new cursor positioned at this node
     */
    fun walk(): TreeCursor

}

/** A stateful object for walking a syntax {@link Tree} efficiently */
external interface TreeCursor {
    /** The type of the current node as a String */
    val nodeType: String

    /** The type of the current node as a numeric ID */
    val nodeTypeId: UInt

    /** The parse state of the current node */
    val nodeStateId: UInt

    /** The text of the current node */
    val nodeText: String

    /** Whether the current node is named */
    val nodeIsNamed: Boolean

    /** Whether the current node is missing from the source code */
    val nodeIsMissing: Boolean

    /** The start position of the current node */
    val startPosition: Point

    /** The end position of the current node */
    val endPosition: Point

    /** The start byte index of the current node */
    val startIndex: UInt

    /** The end byte index of the current node */
    val endIndex: UInt

    /** The current node that the cursor is pointing to */
    val currentNode: SyntaxNode

    /** The field name of the current node */
    val currentFieldName: String

    /** The numerical field ID of the current node */
    val currentFieldId: UInt

    /** The depth of the current node relative to the node where the cursor was created */
    val currentDepth: UInt

    /** The index of the current node among all descendants of the original node */
    val currentDescendantIndex: UInt

    /**
     * Re-initialize this cursor to start at a new node
     *
     * @param node - The node to start from
     */
    fun reset(node: SyntaxNode)

    /**
     * Re-initialize this cursor to the same position as another cursor.
     * Unlike reset(), this will not lose parent information and allows
     * reusing already created cursors.
     *
     * @param cursor - The cursor to copy the position from
     */
    fun resetTo(cursor: TreeCursor)

    /**
     * Move this cursor to the parent of its current node.
     *
     * @returns true if cursor successfully moved, false if there was no parent
     * (cursor was already at the root node)
     */
    fun gotoParent(): Boolean

    /**
     * Move this cursor to the first child of its current node.
     *
     * @returns true if cursor successfully moved, false if there were no children
     */
    fun gotoFirstChild(): Boolean

    /**
     * Move this cursor to the last child of its current node.
     * Note: This may be slower than gotoFirstChild() as it needs to iterate
     * through all children to compute the position.
     *
     * @returns true if cursor successfully moved, false if there were no children
     */
    fun gotoLastChild(): Boolean

    /**
     * Move this cursor to the first child that extends beyond the given byte offset
     *
     * @param goalIndex - The byte offset to search for
     * @returns true if a child was found and cursor moved, false otherwise
     */
    fun gotoFirstChildForIndex(goalIndex: UInt): Boolean

    /**
     * Move this cursor to the first child that extends beyond the given position
     *
     * @param goalPosition - The position to search for
     * @returns true if a child was found and cursor moved, false otherwise
     */
    fun gotoFirstChildForPosition(goalPosition: Point): Boolean

    /**
     * Move this cursor to the next sibling of its current node
     *
     * @returns true if cursor successfully moved, false if there was no next sibling
     */
    fun gotoNextSibling(): Boolean

    /**
     * Move this cursor to the previous sibling of its current node.
     * Note: This may be slower than gotoNextSibling() due to how node positions
     * are stored. In the worst case, it will need to iterate through all previous
     * siblings to recalculate positions.
     *
     * @returns true if cursor successfully moved, false if there was no previous sibling
     */
    fun gotoPreviousSibling(): Boolean

    /**
     * Move the cursor to the descendant node at the given index, where zero
     * represents the original node the cursor was created with.
     *
     * @param goalDescendantIndex - The index of the descendant to move to
     */
    fun gotoDescendant(goalDescendantIndex: UInt)
}

/**
 * A tree that represents the syntactic structure of a source code file.
 */
external interface Tree {
    /**
     * The root node of the syntax tree
     */
    val rootNode: SyntaxNode

    /**
     * Get the root node of the syntax tree, but with its position shifted
     * forward by the given offset.
     *
     * @param offsetBytes - The UInt of bytes to shift by
     * @param offsetExtent - The UInt of rows/columns to shift by
     * @returns The root node with its position offset
     */
    fun rootNodeWithOffset(offsetBytes: UInt, offsetExtent: Point): SyntaxNode

    /**
     * Create a new TreeCursor starting from the root of the tree.
     *
     * @returns A new cursor positioned at the root node
     */
    fun walk(): TreeCursor

    /**
     * Compare this edited syntax tree to a new syntax tree representing the
     * same document, returning ranges whose syntactic structure has changed.
     *
     * For this to work correctly, this tree must have been edited to match
     * the new tree's ranges. Generally, you'll want to call this right after
     * parsing, using the old tree that was passed to parse and the new tree
     * that was returned.
     *
     * @param other - The new tree to compare against
     * @returns JsArray of ranges that have changed
     */
    fun getChangedRanges(other: Tree): JsArray<Range>

    /**
     * Get the ranges that were included when parsing this syntax tree
     *
     * @returns JsArray of included ranges
     */
    fun getIncludedRanges(): JsArray<Range>
}

/**
 * A particular syntax node that was captured by a named pattern in a query.
 */
external interface QueryCapture : JsAny {
    /** The name that was used to capture the node in the query */
    val name: String

    /** The captured syntax node */
    val node: SyntaxNode
}

/**
 * A match of a {@link Query} to a particular set of {@link SyntaxNode}s.
 */
external interface QueryMatch : JsAny {
    /**
     * The index of the pattern that was matched.
     * Each pattern in a query is assigned a numeric index in sequence.
     */
    val pattern: UInt

    /** JsArray of nodes that were captured in the pattern match */
    val captures: JsArray<QueryCapture>
}

open external class QueryOptions {
    /** The starting row/column position in which the query will be executed. */
    val startPosition: Point?

    /** The ending row/column position in which the query will be executed. */
    val endPosition: Point?

    /** The starting byte offset in which the query will be executed. */
    val startIndex: UInt?

    /** The ending byte offset in which the query will be executed. */
    val endIndex: UInt?

    /** The maximum UInt of in-progress matches for this cursor. The limit must be > 0 and <= 65536. */
    val matchLimit: UInt?

    /**
     * The maximum start depth for a query cursor.
     *
     * This prevents cursors from exploring children nodes at a certain depth.
     * Note if a pattern includes many children, then they will still be
     * checked.
     *
     * The zero max start depth value can be used as a special behavior and
     * it helps to destructure a subtree by staying on a node and using
     * captures for interested parts. Note that the zero max start depth
     * only limit a search depth for a pattern's root node but other nodes
     * that are parts of the pattern may be searched at any depth what
     * defined by the pattern structure.
     */
    val maxStartDepth: UInt?

    /**
     * A callback that receives the query state during execution.
     *
     * @param index - The current byte offset
     *
     * @returns `true` to stop the query or `false` to continue
     */
    val progressCallback: (index: UInt) -> Boolean
}

open external class Query {
    /** The maximum UInt of in-progress matches for this cursor. */
    val matchLimit: UInt

    /**
     * Create a new query from a String containing one or more S-expression
     * patterns.
     *
     * The query is associated with a particular language, and can only be run
     * on syntax nodes parsed with that language. References to Queries can be
     * shared between multiple threads.
     */
    constructor(language: Language, source: String)

    /**
     * Iterate over all of the individual captures in the order that they
     * appear.
     *
     * This is useful if you don't care about which pattern matched, and just
     * want a single, ordered sequence of captures.
     *
     * @param node - The syntax node to query
     * @param options - Optional query options
     *
     * @returns An JsArray of captures
     */
    fun captures(node: SyntaxNode, options: QueryOptions?): JsArray<QueryCapture>

    /**
     * Iterate over all of the matches in the order that they were found.
     *
     * Each match contains the index of the pattern that matched, and a list of
     * captures. Because multiple patterns can match the same set of nodes
     * one match may contain captures that appear *before* some of the
     * captures from a previous match.
     *
     * @param node - The syntax node to query
     * @param options - Optional query options
     *
     * @returns An JsArray of matches
     */
    fun matches(node: SyntaxNode, options: QueryOptions?): JsArray<QueryMatch>

    /**
     * Disable a certain capture within a query.
     *
     * This prevents the capture from being returned in matches, and also
     * avoids any resource usage associated with recording the capture.
     *
     * @param captureName - The name of the capture to disable
     */
    fun disableCapture(captureName: String)

    /**
     * Disable a certain pattern within a query.
     *
     * This prevents the pattern from matching, and also avoids any resource
     * usage associated with the pattern.
     *
     * @param patternIndex - The index of the pattern to disable
     */
    fun disablePattern(patternIndex: UInt)

    /**
     * Check if a given step in a query is 'definite'.
     *
     * A query step is 'definite' if its parent pattern will be guaranteed to
     * match successfully once it reaches the step.
     *
     * @param byteOffset - The byte offset of the step to check
     */
    fun isPatternGuaranteedAtStep(byteOffset: UInt): Boolean

    /**
     * Check if a given pattern within a query has a single root node.
     *
     * @param patternIndex - The index of the pattern to check
     */
    fun isPatternRooted(patternIndex: UInt): Boolean

    /**
     * Check if a given pattern within a query has a single root node.
     *
     * @param patternIndex - The index of the pattern to check
     */
    fun isPatternNonLocal(patternIndex: UInt): Boolean

    /**
     * Get the byte offset where the given pattern starts in the query's
     * source.
     *
     * @param patternIndex - The index of the pattern to check
     *
     * @returns The byte offset where the pattern starts
     */
    fun startIndexForPattern(patternIndex: UInt): UInt

    /**
     * Get the byte offset where the given pattern ends in the query's
     * source.
     *
     * @param patternIndex - The index of the pattern to check
     *
     * @returns The byte offset where the pattern ends
     */
    fun endIndexForPattern(patternIndex: UInt): UInt

    /**
     * Check if, on its last execution, this cursor exceeded its maximum UInt
     * of in-progress matches.
     *
     * @returns true if the cursor exceeded its match limit
     */
    fun didExceedMatchLimit(): Boolean
}

open external class LookaheadIterator {
    /** The current symbol of the lookahead iterator. */
    val currentTypeId: UInt

    /** The current symbol name of the lookahead iterator. */
    val currentType: String

    /**
     * Create a new lookahead iterator for this language and parse state.
     *
     * This returns `null` if the state is invalid for this language.
     *
     * Iterating {@link LookaheadIterator} will yield valid symbols in the given
     * parse state. Newly created lookahead iterators will have {@link currentType}
     * populated with the `ERROR` symbol.
     *
     * Lookahead iterators can be useful to generate suggestions and improve
     * syntax error diagnostics. To get symbols valid in an ERROR node, use the
     * lookahead iterator on its first leaf node state. For `MISSING` nodes, a
     * lookahead iterator created on the previous non-extra leaf node may be
     * appropriate.
     *
     * @param language - The language to use for the lookahead iterator
     * @param state - The parse state to use for the lookahead iterator
     */
    constructor(language: Language, state: UInt)

    /**
     * Reset the lookahead iterator.
     *
     * This returns `true` if the language was set successfully and `false`
     * otherwise.
     *
     * @param language - The language to use for the lookahead iterator
     * @param stateId - The parse state to use for the lookahead iterator
     */
    fun reset(language: Language, stateId: UInt): Boolean

    /**
     * Reset the lookahead iterator to another state.
     *
     * This returns `true` if the iterator was reset to the given state and
     * `false` otherwise.
     *
     * @param stateId - The parse state to reset the lookahead iterator to
     */
    fun resetState(stateId: UInt): Boolean
}

/** The base node type */
open external class BaseNode : JsAny {
    /** The node's type */
    val type: String

    /** Whether the node is named */
    val named: Boolean
}

/** A child within a node */
open external class ChildNode {
    /** Whether the child is repeated */
    val multiple: Boolean

    /** Whether the child is required */
    val required: Boolean

    /** The child's type */
    val types: JsArray<BaseNode>
}

/** Information about a language */
open external class Language : JsAny {

    /** The inner language object */
    val language: JsAny

    companion object {
        fun load(input: String): Language?
    }
}