package ru.hopec.renamer

import ru.hopec.core.StatusSeverity
import ru.hopec.parser.TreeSitterRepresentation
import ru.hopec.parser.treesitter.TsSyntaxNode

class CstParser(
    private val from: TreeSitterRepresentation,
    private val moduleOperators: Map<String, Map<String, Infix>>
) {

    data class ParserState(
        val operators: MutableMap<String, Infix> = mutableMapOf(),
        val typeVars: MutableSet<String> = mutableSetOf(),
    )
    fun parse(): Program {
        val rootNode = from.tree.rootNode
        val globalParserState = ParserState()
        val topLevelNodes = parseMultiple(rootNode, {
                child ->
            when (child.type) {
                "module" -> parseModule(child, globalParserState)
                else -> parseStatement(child, globalParserState)
            }
        })
        return Program(topLevelNodes)
    }

    private fun parseModule(node: TsSyntaxNode, globalParserState: ParserState): AstNode.Module {
        val parserState = ParserState()
        val moduleName = node.getChildOrThrow(0u, "binding").text
        val statements = parseMultiple(node, {node -> parseStatement(node, parserState)}, 1u)
        return AstNode.Module(moduleName, statements)
    }

    private fun parseStatement(node: TsSyntaxNode, parserState: ParserState): AstNode.Statement? {
        return when (node.type) {
            "data_declaration" -> {
                val name =  node.getChildOrThrow(0u).text
                val params = parseMultiple(node, {child -> child.text}, 1u, node.namedChildCount - 1u)
                val typeNode = parseTypeDeclaration(node.getChildOrThrow(node.namedChildCount - 1u))
                AstNode.DataDeclaration(name, params, typeNode)
            }

            "function_equation" -> {
                val patternNode = node.getChildOrThrow(0u)
                val exprNode = node.getChildOrThrow(1u)
                AstNode.FunctionEquation(
                    pattern = AstNode.PatternVar(" "),
                    body = AstNode.Ident(" ")
                )
                null
            }

            "function_declaration" -> {
                throw NotImplementedError()
//                val names = parseMultiple(node, {child -> child.text}, 0u, node.namedChildCount - 1u)
//                val typeNode = parseType(node.getChildOrThrow(node.namedChildCount - 1u))
//                AstNode.FunctionDeclaration(names, typeNode)
            }

            "infix_declaration" -> {
                parserState.operators.putAll(parseInfix(node)!!)
                null
            }

            "type_variable_declaration" -> {
                parserState.typeVars.addAll(parseMultipleIdent(node))
                null
            }

            "type_export_declaration" -> AstNode.TypeExportDeclaration(parseMultipleIdent(node))
            "constant_export_declaration" -> AstNode.ConstantExportDeclaration(parseMultipleIdent(node))
            "module_use_declaration" -> AstNode.ModuleUseDeclaration(parseMultipleIdent(node))

            else -> throw RenamerException(StatusSeverity.ERROR,
                "Unknown statement: ${node.type} in node $node",
                node.endPosition.toPosition()
            )
        }

    }

    private fun parseTypeDeclaration(node: TsSyntaxNode): List<Pair<String, AstNode.TypeExpr>> {
        throw NotImplementedError()
    }

    private fun parseType(node: TsSyntaxNode, typeVars: MutableSet<String>): AstNode.TypeExpr {
        return when (node.type) {
            "type_expression" -> {
                if (node.namedChildCount == 1u)
                    parseType(node.getChildOrThrow(0u), typeVars)
                else {
                    val func = parseType(node.getChildOrThrow(0u), typeVars)
                    val args = parseMultiple(node, { node -> parseType(node, typeVars) }, 1u)
                    AstNode.TypeApplication(func, args)
                }
            }

            "binary_type_expression" -> {
                val type1 = parseType(node.getChildOrThrow(0u), typeVars)
                val type2 = parseType(node.getChildOrThrow(1u), typeVars)
                if (node.children[1].text == "->")
                    AstNode.FunctionalType(type1, type2)
//                else if (node.children[1].text == "++")
//                    AstNode.SumType(type1, type2)
                else if (node.children[1].text == "#")
                    AstNode.ProductType(type1, type2)
                else
                    throw RenamerException(StatusSeverity.ERROR,
                        "Unknown ADT: ${node.children[1].text}",
                        node.children[1].startPosition.toPosition()
                    )
            }

            "ident" ->  AstNode.OuterType(node.text)

            else -> throw RenamerException(StatusSeverity.ERROR,
                "Unknown type: ${node.type} in node $node",
                node.startPosition.toPosition()
            )
        }
    }
}