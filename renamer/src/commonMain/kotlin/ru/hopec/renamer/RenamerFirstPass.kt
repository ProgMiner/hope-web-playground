package ru.hopec.renamer

import ru.hopec.core.CompilationContext
import ru.hopec.parser.TreeSitterRepresentation
import ru.hopec.parser.treesitter.TsSyntaxNode
import ru.hopec.parser.treesitter.range
import ru.hopec.renamer.RenamerSecondPass.TypeDeclarationException

class RenamerFirstPass(
    val from: TreeSitterRepresentation,
) {
    val imported: MutableSet<String> = mutableSetOf()

    data class ParserState(
        val infixes: MutableMap<String, Infix> = mutableMapOf(),
        val operators: MutableSet<String> = mutableSetOf(),
        val declarations: MutableSet<String> = mutableSetOf(),
        val exports: MutableSet<String> = mutableSetOf(),
        val typeVars: MutableSet<String> = mutableSetOf(),
    )

    fun parse(context: CompilationContext): FirstPassProgram {
        val rootNode = from.tree.rootNode
        val parserState = ParserState()
        val modules = mutableMapOf<String, Map<String, Infix>>()
        val topLevelNodes =
            parseMultiple(rootNode, { child ->
                try {
                    when (child.type) {
                        "module" -> {
                            val module = parseModule(child)
                            modules[module.name] = module.infixes
                            module
                        }

                        else -> {
                            parseStatement(child, parserState)
                        }
                    }
                } catch (e: RenamerException) {
                    context.add(e)
                    FirstPassNode.Statement.Error(child, e)
                }
            }).toMutableList()

        val infixes =
            (
                parserState.declarations
                    .filter { parserState.infixes.contains(it) } + parserState.operators
            ).filter { parserState.exports.contains(it) }
                .associateWith { parserState.infixes[it]!! }

        return FirstPassProgram(topLevelNodes, modules, infixes, imported)
    }

    private fun parseModule(node: TsSyntaxNode): FirstPassNode.Module {
        val moduleName = node.getChildOrThrow(0u).text
        val parserState = ParserState()
        val statements =
            parseMultipleOrError(
                node,
                { node -> parseStatement(node, parserState) },
                { FirstPassNode.Statement.Error(node, it) },
                1u,
            ).toMutableList()

        val infixes =
            (
                parserState.declarations
                    .filter { parserState.infixes.contains(it) } + parserState.operators
            ).filter { parserState.exports.contains(it) }
                .associateWith { parserState.infixes[it]!! }

        return FirstPassNode.Module(node, moduleName, statements, infixes)
    }

    fun parseStatement(
        node: TsSyntaxNode,
        parserState: ParserState,
    ): FirstPassNode.Statement? =
        when (node.type) {
            "data_declaration" -> {
                val name = node.getChildOrThrow(0u).text
                val params = parseMultiple(node, { it.text }, 1u, node.namedChildCount - 1u)
                val typeNode =
                    parseTypeDeclaration(
                        node.getChildOrThrow(node.namedChildCount - 1u),
                        parserState,
                        params.toMutableSet(),
                    )
                FirstPassNode.Statement.DataDeclaration(node, name, params, typeNode)
            }

            "function_declaration" -> {
                val name = node.getChildOrThrow(0u).text
                val typeNode = parseType(node.getChildOrThrow(1u), parserState.typeVars)
                parserState.declarations.add(name)
                FirstPassNode.Statement.FunctionDeclaration(
                    node,
                    name,
                    getBoundVars(typeNode).toList(),
                    typeNode,
                )
            }

            "infix_declaration" -> {
                val operators = parseInfix(node)!!
                parserState.infixes.putAll(operators)
                FirstPassNode.Statement.InfixDeclaration(node, operators)
            }

            "module_use_declaration" -> {
                val names = parseMultipleIdent(node)
                imported.addAll(names)
                FirstPassNode.Statement.ModuleUseDeclaration(node, names)
            }

            "constant_export_declaration" -> {
                val export = parseMultipleIdent(node)
                parserState.exports.addAll(export)
                FirstPassNode.Statement.ConstantExportDeclaration(node, export)
            }

            "type_variable_declaration" -> {
                parserState.typeVars.addAll(parseMultipleIdent(node))
                null
            }

            else -> {
                FirstPassNode.Statement.NotParsed(node)
            }
        }

    private fun parseTypeDeclaration(
        node: TsSyntaxNode,
        parserState: ParserState,
        typeVars: MutableSet<String>,
    ): List<Pair<String, FirstPassNode.TypeExpr?>> {
        if (node.type != "type_expression") {
            throw TypeDeclarationException(node)
        }

        val typeNode = node.getChildOrThrow(0u)
        return when (typeNode.type) {
            "binary_type_expression" -> {
                val op = typeNode.child(1u)!!
                if (op.type == "++") {
                    parseTypeDeclaration(typeNode.getChildOrThrow(0u), parserState, typeVars) +
                        parseTypeDeclaration(typeNode.getChildOrThrow(1u), parserState, typeVars)
                } else {
                    throw TypeDeclarationException(node)
                }
            }

            else -> {
                if (node.namedChildCount == 1u) {
                    val constructor = node.getChildOrThrow(0u).text
                    listOf(Pair(constructor, null))
                } else if (node.namedChildCount == 2u) {
                    val constructor = node.getChildOrThrow(0u).text
                    listOf(Pair(constructor, parseType(node.getChildOrThrow(1u), typeVars)))
                } else {
                    val operatorIndex =
                        node.namedChildren.indexOfFirst { parserState.infixes.contains(it.text) }.toUInt()
                    val operator =
                        node.namedChild(operatorIndex) ?: throw RenamerException(
                            "Unknown operator",
                            node.range(),
                        )

                    parserState.operators.add(operator.text)

                    val left = parseFunctionalType(node, typeVars, 0u, operatorIndex)
                    val right =
                        parseFunctionalType(node, typeVars, operatorIndex + 1u, node.namedChildCount)

                    listOf(Pair(operator.text, FirstPassNode.TypeExpr.ProductType(left, right)))
                }
            }
        }
    }

    private fun parseType(
        node: TsSyntaxNode,
        typeVars: MutableSet<String>,
    ): FirstPassNode.TypeExpr =
        when (node.type) {
            "type_expression" -> {
                parseFunctionalType(node, typeVars, 0u, node.namedChildCount)
            }

            "binary_type_expression" -> {
                val type1 = parseType(node.getChildOrThrow(0u), typeVars)
                val type2 = parseType(node.getChildOrThrow(1u), typeVars)
                when (node.children[1].text) {
                    "->" -> FirstPassNode.TypeExpr.FunctionalType(type1, type2)

                    "#" -> FirstPassNode.TypeExpr.ProductType(type1, type2)

                    else -> throw RenamerException(
                        "Unknown ADT: ${node.children[1].text}",
                        node.children[1].range(),
                        fatal = true,
                    )
                }
            }

            "ident" -> {
                if (typeVars.contains(node.text)) {
                    FirstPassNode.TypeExpr.VarType(node.text)
                } else {
                    FirstPassNode.TypeExpr.NamedType(node.text, emptyList())
                }
            }

            else -> {
                throw RenamerException(
                    "Unknown type: ${node.type} in node $node",
                    node.range(),
                    fatal = true,
                )
            }
        }

    private fun parseFunctionalType(
        node: TsSyntaxNode,
        typeVars: MutableSet<String>,
        from: UInt,
        to: UInt,
    ) = if (node.namedChildCount == 1u) {
        parseType(node.getChildOrThrow(from), typeVars)
    } else {
        val func = node.getChildOrThrow(from).text
        val args = parseMultiple(node, { parseType(it, typeVars) }, from + 1u, to)
        FirstPassNode.TypeExpr.NamedType(func, args)
    }

    private fun getBoundVars(type: FirstPassNode.TypeExpr): Set<String> =
        when (type) {
            is FirstPassNode.TypeExpr.VarType -> setOf(type.name)
            is FirstPassNode.TypeExpr.NamedType -> type.arguments.flatMap { getBoundVars(it) }.toSet()
            is FirstPassNode.TypeExpr.FunctionalType -> getBoundVars(type.premise) + getBoundVars(type.result)
            is FirstPassNode.TypeExpr.ProductType -> getBoundVars(type.left) + getBoundVars(type.right)
        }
}

fun parseInfix(node: TsSyntaxNode): List<Pair<String, Infix>>? {
    if (node.type == "infix_declaration") {
        val assoc = node.getChildOrThrow(0u).text != "infix"
        val names = parseMultipleIdent(node = node, to = node.namedChildCount - 1u)
        val priority = node.getChildOrThrow(node.namedChildCount - 1u).text
        return names.map { Pair(it, Infix(priority.toInt(), assoc)) }
    }
    return null
}
