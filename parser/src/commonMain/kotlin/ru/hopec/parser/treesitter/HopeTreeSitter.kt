package ru.hopec.parser.treesitter

interface TsParser {
    /**
     * Parse UTF8 text into a syntax tree.
     *
     * @param input - The text to parse as a String
     *
     * @returns A syntax tree representing the parsed text
     */
    fun parse(input: String): TsTree

    /**
     * Get the ranges of text that the parser will include when parsing.
     *
     * @returns An array of ranges that will be included in parsing
     */
    fun getIncludedRanges(): List<TsRange>

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
    fun getLanguage(): TsLanguage

    /**
     * Set the language that the parser should use for parsing.
     *
     * The language must be compatible with the version of tree-sitter
     * being used. A version mismatch will prevent the language from
     * being assigned successfully.
     *
     * @param language - The language to use for parsing
     */
    fun setLanguage(language: TsLanguage?)
}

/**
 * A position in a multi-line text document, in terms of rows and columns.
 * Both values are zero-based.
 */
interface TsPoint {
    val row: UInt
    val column: UInt
}

/**
 * A range of positions in a multi-line text document, specified both in
 * terms of byte offsets and row/column positions.
 */
interface TsRange {
    val startIndex: UInt
    val endIndex: UInt
    val startPosition: TsPoint
    val endPosition: TsPoint
}

interface TsSyntaxNode {
    /** The syntax tree that contains this node */
    val tree: TsTree

    /**
     * Textual contents of the node
     */
    val text: String

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

    /** The parse state of this node */
    val parseState: UInt

    /** The parse state that follows this node */
    val nextParseState: UInt

    /** The position where this node starts in terms of rows and columns */
    val startPosition: TsPoint

    /** The position where this node ends in terms of rows and columns */
    val endPosition: TsPoint

    /** The byte offset where this node starts */
    val startIndex: UInt

    /** The byte offset where this node ends */
    val endIndex: UInt

    /**
     * This node's immediate parent.
     * For iterating over ancestors, prefer using {@link childWithDescendant}
     */
    val parent: TsSyntaxNode?

    /** List of all child nodes */
    val children: List<TsSyntaxNode>

    /** List of all named child nodes */
    val namedChildren: List<TsSyntaxNode>

    /** The UInt of children this node has */
    val childCount: UInt

    /**
     * The UInt of named children this node has.
     * @see {@link isNamed}
     */
    val namedChildCount: UInt

    /** The first child of this node */
    val firstChild: TsSyntaxNode?

    /** The first named child of this node */
    val firstNamedChild: TsSyntaxNode?

    /** The last child of this node */
    val lastChild: TsSyntaxNode?

    /** The last child of this node */
    val lastNamedChild: TsSyntaxNode?

    /** This node's next sibling */
    val nextSibling: TsSyntaxNode?

    /** This node's next named sibling */
    val nextNamedSibling: TsSyntaxNode?

    /** This node's previous sibling */
    val previousSibling: TsSyntaxNode?

    /** This node's previous named sibling */
    val previousNamedSibling: TsSyntaxNode?

    /**
     * Convert this node to its String representation
     */
    override fun toString(): String

    /**
     * Get the node's child at the given index, where zero represents the first child.
     *
     * Note: While fairly fast, this method's cost is technically log(i).
     * For iterating over many children, prefer using the children List.
     *
     * @param index - Zero-based index of the child to retrieve
     * @returns The child node, or null if none exists at the given index
     */
    fun child(index: UInt): TsSyntaxNode?

    /**
     * Get this node's named child at the given index.
     *
     * Note: While fairly fast, this method's cost is technically log(i).
     * For iterating over many children, prefer using the namedChildren List.
     *
     * @param index - Zero-based index of the named child to retrieve
     * @returns The named child node, or null if none exists at the given index
     */
    fun namedChild(index: UInt): TsSyntaxNode?

    /**
     * Get the first child with the given field name.
     *
     * For fields that may have multiple children, use childrenForFieldName instead.
     *
     * @param fieldName - The field name to search for
     * @returns The child node, or null if no child has the given field name
     */
    fun childForFieldName(fieldName: String): TsSyntaxNode?

    /**
     * Get this node's child with the given numerical field id.
     *
     * Field IDs can be obtained from field names using the parser's language object.
     *
     * @param fieldId - The field ID to search for
     * @returns The child node, or null if no child has the given field ID
     */
    fun childForFieldId(fieldId: UInt): TsSyntaxNode?

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
     * Get the node's first child that extends beyond the given byte offset
     *
     * @param index - The byte offset to search from
     * @returns The first child extending beyond the offset, or null if none found
     */
    fun firstChildForIndex(index: UInt): TsSyntaxNode?

    /**
     * Get the node's first named child that extends beyond the given byte offset
     *
     * @param index - The byte offset to search from
     * @returns The first named child extending beyond the offset, or null if none found
     */
    fun firstNamedChildForIndex(index: UInt): TsSyntaxNode?

    /**
     * Get the immediate child that contains the given descendant node.
     * Note that this can return the descendant itself if it is an immediate child.
     *
     * @param descendant - The descendant node to find the parent of
     * @returns The child containing the descendant, or null if not found
     */
    fun childWithDescendant(descendant: TsSyntaxNode): TsSyntaxNode?

    /**
     * Get the smallest node within this node that spans the given byte offset.
     *
     * @param index - The byte offset to search for
     * @returns The smallest node spanning the offset
     */
    fun descendantForIndex(index: UInt): TsSyntaxNode

    /**
     * Get the smallest node within this node that spans the given byte range.
     *
     * @param startIndex - The starting byte offset
     * @param endIndex - The ending byte offset
     * @returns The smallest node spanning the range
     */
    fun descendantForIndex(startIndex: UInt, endIndex: UInt): TsSyntaxNode

    /**
     * Get the smallest named node within this node that spans the given byte offset.
     *
     * @param index - The byte offset to search for
     * @returns The smallest named node spanning the offset
     */
    fun namedDescendantForIndex(index: UInt): TsSyntaxNode

    /**
     * Get the smallest named node within this node that spans the given byte range.
     *
     * @param startIndex - The starting byte offset
     * @param endIndex - The ending byte offset
     * @returns The smallest named node spanning the range
     */
    fun namedDescendantForIndex(startIndex: UInt, endIndex: UInt): TsSyntaxNode

    /**
     * Get the smallest node within this node that spans the given position.
     * When only one position is provided, it's used as both start and end.
     *
     * @param position - The point to search for
     * @returns The smallest node spanning the position
     */
    fun descendantForPosition(position: TsPoint): TsSyntaxNode

    /**
     * Get the smallest node within this node that spans the given position range.
     *
     * @param startPosition - The starting position
     * @param endPosition - The ending position
     * @returns The smallest node spanning the range
     */
    fun descendantForPosition(startPosition: TsPoint, endPosition: TsPoint): TsSyntaxNode

    /**
     * Get the smallest named node within this node that spans the given position.
     * When only one position is provided, it's used as both start and end.
     *
     * @param position - The point to search for
     * @returns The smallest named node spanning the position
     */
    fun namedDescendantForPosition(position: TsPoint): TsSyntaxNode

    /**
     * Get the smallest named node within this node that spans the given position range.
     *
     * @param startPosition - The starting position
     * @param endPosition - The ending position
     * @returns The smallest named node spanning the range
     */
    fun namedDescendantForPosition(startPosition: TsPoint, endPosition: TsPoint): TsSyntaxNode

    /**
     * Get all descendants of this node that have the given type(s)
     *
     * @param types - A String or List of Strings of node types to find
     * @param startPosition - Optional starting position to search from
     * @param endPosition - Optional ending position to search to
     * @returns List of descendant nodes matching the given types
     */
    fun descendantsOfType(types: List<String>, startPosition: TsPoint?, endPosition: TsPoint?): List<TsSyntaxNode>

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
     * @param types - A String or List of Strings representing the node types to search for
     * @returns The closest matching ancestor node, or null if none found
     * @throws Exception If the argument is not a String or List of Strings
     */
    fun closest(types: List<String>): TsSyntaxNode?

    /**
     * Create a new TreeCursor starting from this node.
     *
     * @returns A new cursor positioned at this node
     */
    fun walk(): TsTreeCursor

}

/** A stateful object for walking a syntax {@link Tree} efficiently */
interface TsTreeCursor {
    /** The type of the current node as a String */
    val nodeType: String

    /** The type of the current node as a numeric ID */
    val nodeTypeId: UInt

    /** The parse state of the current node */
    val nodeStateId: UInt

    /** Whether the current node is named */
    val nodeIsNamed: Boolean

    /** Whether the current node is missing from the source code */
    val nodeIsMissing: Boolean

    /** The start position of the current node */
    val startPosition: TsPoint

    /** The end position of the current node */
    val endPosition: TsPoint

    /** The start byte index of the current node */
    val startIndex: UInt

    /** The end byte index of the current node */
    val endIndex: UInt

    /** The current node that the cursor is pointing to */
    val currentNode: TsSyntaxNode

    /** The field name of the current node */
    val currentFieldName: String

    /** The numerical field ID of the current node */
    val currentFieldId: UInt

    /** The depth of the current node relative to the node where the cursor was created */
    val currentDepth: UInt

    /**
     * Re-initialize this cursor to start at a new node
     *
     * @param node - The node to start from
     */
    fun reset(node: TsSyntaxNode)

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
    fun gotoFirstChildForPosition(goalPosition: TsPoint): Boolean

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

}

/**
 * A tree that represents the syntactic structure of a source code file.
 */
interface TsTree {
    /**
     * The root node of the syntax tree
     */
    val rootNode: TsSyntaxNode

    /**
     * Get the root node of the syntax tree, but with its position shifted
     * forward by the given offset.
     *
     * @param offsetBytes - The UInt of bytes to shift by
     * @param offsetExtent - The UInt of rows/columns to shift by
     * @returns The root node with its position offset
     */
    fun rootNodeWithOffset(offsetBytes: UInt, offsetExtent: TsPoint): TsSyntaxNode

    /**
     * Create a new TreeCursor starting from the root of the tree.
     *
     * @returns A new cursor positioned at the root node
     */
    fun walk(): TsTreeCursor

    /**
     * Get the ranges that were included when parsing this syntax tree
     *
     * @returns List of included ranges
     */
    fun getIncludedRanges(): List<TsRange>
}

/**
 * A particular syntax node that was captured by a named pattern in a query.
 */
interface TsQueryCapture {
    /** The captured syntax node */
    val node: TsSyntaxNode
}

/**
 * A match of a {@link Query} to a particular set of {@link SyntaxNode}s.
 */
interface TsQueryMatch {
    /**
     * The index of the pattern that was matched.
     * Each pattern in a query is assigned a numeric index in sequence.
     */
    val pattern: UInt

    /** List of nodes that were captured in the pattern match */
    val captures: List<TsQueryCapture>
}

interface TsQuery {
    /**
     * Iterate over all of the individual captures in the order that they
     * appear.
     *
     * This is useful if you don't care about which pattern matched, and just
     * want a single, ordered sequence of captures.
     *
     * @param node - The syntax node to query
     *
     * @returns An List of captures
     */
    fun captures(node: TsSyntaxNode): List<TsQueryCapture>

    /**
     * Iterate over all of the matches in the order that they were found.
     *
     * Each match contains the index of the pattern that matched, and a list of
     * captures. Because multiple patterns can match the same set of nodes
     * one match may contain captures that appear *before* some of the
     * captures from a previous match.
     *
     * @param node - The syntax node to query
     *
     * @returns An List of matches
     */
    fun matches(node: TsSyntaxNode): List<TsQueryMatch>

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
}

interface TsLookaheadIterator {
    /** The current symbol of the lookahead iterator. */
    val currentTypeId: UInt

    /** The current symbol name of the lookahead iterator. */
    val currentType: String

    /**
     * Reset the lookahead iterator.
     *
     * This returns `true` if the language was set successfully and `false`
     * otherwise.
     *
     * @param language - The language to use for the lookahead iterator
     * @param stateId - The parse state to use for the lookahead iterator
     */
    fun reset(language: TsLanguage, stateId: UInt): Boolean

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

/** Information about a language */
interface TsLanguage

interface TsFactory {

    fun createParser(): TsParser

    /**
     * Create a new query from a String containing one or more S-expression
     * patterns.
     *
     * The query is associated with a particular language, and can only be run
     * on syntax nodes parsed with that language. References to Queries can be
     * shared between multiple threads.
     */
    fun createQuery(language: TsLanguage, source: String): TsQuery

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
    fun createLookaheadIterator(language: TsLanguage, state: UInt): TsLookaheadIterator

    fun loadLanguage(location: String): TsLanguage?
}

expect fun factory(): TsFactory

fun parseHope(location: String, input: String): TsTree {
    val parser = factory().createParser()
    parser.setLanguage(factory().loadLanguage(location))
    return parser.parse(input)
}