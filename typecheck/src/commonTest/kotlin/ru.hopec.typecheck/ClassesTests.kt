package ru.hopec.typecheck

import ru.hopec.desugarer.DesugaredRepresentation
import ru.hopec.desugarer.DesugaredRepresentation.Declarations
import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Data.Name.Core
import ru.hopec.desugarer.DesugaredRepresentation.Expr
import ru.hopec.desugarer.DesugaredRepresentation.Pattern
import ru.hopec.desugarer.DesugaredRepresentation.PolymorphicType
import ru.hopec.desugarer.DesugaredRepresentation.Predicate
import ru.hopec.desugarer.DesugaredRepresentation.Type
import ru.hopec.desugarer.instanceDictionaryName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val eqClass = Declarations.Class.Name.Defined(null, "Eq")
private val eqMethod = Declarations.Function.Name.User(null, "eq")
private val member = Declarations.Function.Name.User(null, "member")
private val mainName = Declarations.Function.Name.User(null, "main")

private val coreEq = Declarations.Function.Name.Core("=")
private val cons = Declarations.Function.Name.Constructor(Core.List, "cons")
private val nil = Declarations.Function.Name.Constructor(Core.List, "nil")
private val makeTuple = Declarations.Function.Name.Constructor(Core.Tuple, "#")
private val trueName = Declarations.Function.Name.Constructor(Core.TruVal, "true")
private val falseName = Declarations.Function.Name.Constructor(Core.TruVal, "false")

private fun a(i: Int = 0) = Type.Variable(i)

private infix fun Type.arrow(other: Type) = Type.Arrow(this, other)

private fun ident(name: Declarations.Function.Name) = Expr.Identifier(setOf(name))

private fun v(
    name: String,
    binder: Int,
) = Expr.Variable(name, binder)

private fun pv(name: String) = Pattern.Variable(name)

private fun app(
    fn: Expr,
    vararg args: Expr,
) = Expr.Application(fn, args.toList())

private fun tuple(
    left: Expr,
    right: Expr,
) = app(ident(makeTuple), left, right)

private fun tuplePat(
    left: Pattern,
    right: Pattern,
) = Pattern.Data(setOf(makeTuple), listOf(left, right))

private fun consPat(
    head: Pattern,
    tail: Pattern,
) = Pattern.Data(setOf(cons), listOf(tuplePat(head, tail)))

private fun list(vararg elements: Expr): Expr = elements.foldRight(ident(nil) as Expr) { e, acc -> app(ident(cons), tuple(e, acc)) }

private fun lambda(vararg branches: Pair<Pattern, Expr>) = Expr.Lambda(branches.map { Expr.Lambda.Branch(it.first, it.second) })

private val eqType = Type.Data.tuple(a(), a()) arrow Type.Data.truval

private val eqClassDecl = Declarations.Class(mapOf(eqMethod to eqType))

private val eqNum =
    Declarations.Instance(
        eqClass,
        Type.Data.num,
        0,
        emptyList(),
        mapOf(eqMethod to lambda(pv("p") to app(ident(coreEq), v("p", 0)))),
    )

private val eqList =
    Declarations.Instance(
        eqClass,
        Type.Data.list(a()),
        1,
        listOf(Predicate(eqClass, 0)),
        mapOf(
            eqMethod to
                lambda(
                    tuplePat(Pattern.Data(setOf(nil), emptyList()), Pattern.Data(setOf(nil), emptyList())) to ident(trueName),
                    tuplePat(consPat(pv("x"), pv("xs")), consPat(pv("y"), pv("ys"))) to
                        Expr.If(
                            app(ident(eqMethod), tuple(v("x", 3), v("y", 1))),
                            app(ident(eqMethod), tuple(v("xs", 2), v("ys", 0))),
                            ident(falseName),
                        ),
                    Pattern.Wildcard to ident(falseName),
                ),
        ),
    )

private fun memberFunction(context: List<Predicate>) =
    Declarations.Function(
        lambda(
            tuplePat(Pattern.Wildcard, Pattern.Data(setOf(nil), emptyList())) to ident(falseName),
            tuplePat(pv("x"), consPat(pv("y"), pv("ys"))) to
                Expr.If(
                    app(ident(eqMethod), tuple(v("x", 2), v("y", 1))),
                    ident(trueName),
                    app(ident(member), tuple(v("x", 2), v("ys", 0))),
                ),
        ),
        PolymorphicType(Type.Data.tuple(a(), Type.Data.list(a())) arrow Type.Data.truval, 1, context),
    )

private fun mainFunction(body: Expr) =
    Declarations.Function(
        lambda(Pattern.Wildcard to body),
        PolymorphicType(Type.Data.truval, 0),
    )

private fun program(
    functions: Map<Declarations.Function.Name, Declarations.Function>,
    instances: List<Declarations.Instance> = listOf(eqNum, eqList),
) = DesugaredRepresentation(
    emptyMap(),
    Declarations(emptyMap(), functions, mapOf(eqClass to eqClassDecl), instances),
)

class ClassesTests {
    private fun check(repr: DesugaredRepresentation): Pair<TypedRepresentation?, List<String>> {
        val errors = mutableListOf<String>()
        val result = annotate(repr) { errors.add(it) }
        return result to errors
    }

    private fun assertFails(
        repr: DesugaredRepresentation,
        message: String,
    ) {
        val (result, errors) = check(repr)
        assertNull(result)
        assertTrue(errors.any { message in it }, "expected error containing '$message', got $errors")
    }

    @Test
    fun instancesTranslateToDictionaryFunctions() {
        val (result, errors) = check(program(emptyMap()))
        assertNotNull(result, errors.toString())
        val functions = result.topLevel.functions
        val eqNumDictionary = assertNotNull(functions[instanceDictionaryName(eqClass, Core.Num)])
        assertEquals(eqType.substitute(listOf(Type.Data.num)), eqNumDictionary.lambda.type)

        val eqListDictionary = assertNotNull(functions[instanceDictionaryName(eqClass, Core.List)])
        val outer = eqListDictionary.lambda
        assertEquals(eqType, outer.type.argument)
        assertEquals(eqType.substitute(listOf(Type.Data.list(a()))), outer.type.result)
        assertEquals("dict.Eq.0", (outer.branches.single().pattern as TypedRepresentation.Pattern.Variable).name)
    }

    @Test
    fun overloadedFunctionTakesDictionary() {
        val (result, errors) = check(program(mapOf(member to memberFunction(listOf(Predicate(eqClass, 0))))))
        assertNotNull(result, errors.toString())
        val lambda = assertNotNull(result.topLevel.functions[member]).lambda
        val parameter = lambda.branches.single().pattern as TypedRepresentation.Pattern.Variable
        assertEquals("dict.Eq.0", parameter.name)
        assertEquals(eqType, parameter.type)
    }

    @Test
    fun dictionaryIsBuiltFromInstances() {
        val body = app(ident(eqMethod), tuple(list(DesugaredRepresentation.Literal.Num(1)), list()))
        val (result, errors) = check(program(mapOf(mainName to mainFunction(body))))
        assertNotNull(result, errors.toString())
        val mainBody =
            assertNotNull(result.topLevel.functions[mainName])
                .lambda.branches
                .single()
                .body as TypedRepresentation.Expr.Application
        val dictionary = mainBody.left as TypedRepresentation.Expr.Application
        assertEquals(instanceDictionaryName(eqClass, Core.List), (dictionary.left as TypedRepresentation.Expr.Identifier).name)
        assertEquals(instanceDictionaryName(eqClass, Core.Num), (dictionary.right as TypedRepresentation.Expr.Identifier).name)
    }

    @Test
    fun missingConstraintIsReported() {
        assertFails(program(mapOf(member to memberFunction(emptyList()))), "add 'given Eq")
    }

    @Test
    fun missingInstanceIsReported() {
        val body = app(ident(eqMethod), tuple(DesugaredRepresentation.Literal.Char('a'), DesugaredRepresentation.Literal.Char('b')))
        assertFails(program(mapOf(mainName to mainFunction(body))), "no instance for 'Eq char'")
    }

    @Test
    fun functionsHaveNoEquality() {
        val id = lambda(pv("x") to v("x", 0))
        assertFails(program(mapOf(mainName to mainFunction(app(ident(eqMethod), tuple(id, id))))), "function type")
    }

    @Test
    fun ambiguousTypeIsReported() {
        val body = app(ident(eqMethod), tuple(ident(nil), ident(nil)))
        assertFails(program(mapOf(mainName to mainFunction(body))), "ambiguous")
    }

    @Test
    fun letDoesNotGeneralizeConstrainedVariables() {
        val body =
            Expr.Let(
                pv("e"),
                lambda(pv("p") to app(ident(eqMethod), v("p", 0))),
                app(v("e", 0), tuple(DesugaredRepresentation.Literal.Num(1), DesugaredRepresentation.Literal.Num(2))),
            )
        val (result, errors) = check(program(mapOf(mainName to mainFunction(body))))
        assertNotNull(result, errors.toString())
    }

    @Test
    fun overlappingInstancesAreRejected() {
        assertFails(program(emptyMap(), listOf(eqNum, eqNum.copy())), "Overlapping")
    }

    @Test
    fun severalMethodsAreTupledInDictionary() {
        val ordClass = Declarations.Class.Name.Defined(null, "Ord")
        val less = Declarations.Function.Name.User(null, "less")
        val greater = Declarations.Function.Name.User(null, "greater")
        val ordNum =
            Declarations.Instance(
                ordClass,
                Type.Data.num,
                0,
                emptyList(),
                mapOf(
                    less to lambda(pv("p") to app(ident(Declarations.Function.Name.Core("<")), v("p", 0))),
                    greater to lambda(pv("p") to app(ident(Declarations.Function.Name.Core(">")), v("p", 0))),
                ),
            )
        val body = app(ident(greater), tuple(DesugaredRepresentation.Literal.Num(2), DesugaredRepresentation.Literal.Num(1)))
        val repr =
            DesugaredRepresentation(
                emptyMap(),
                Declarations(
                    emptyMap(),
                    mapOf(mainName to mainFunction(body)),
                    mapOf(ordClass to Declarations.Class(mapOf(less to eqType, greater to eqType))),
                    listOf(ordNum),
                ),
            )
        val (result, errors) = check(repr)
        assertNotNull(result, errors.toString())
        val functions = result.topLevel.functions

        assertNotNull(functions[ordNum.method(less)])
        assertNotNull(functions[ordNum.method(greater)])
        val numEq = eqType.substitute(listOf(Type.Data.num))
        val dictionary = assertNotNull(functions[ordNum.dictionary]).lambda
        assertEquals(Type.Data.tuple(numEq, numEq), dictionary.type.result)

        val call =
            assertNotNull(functions[mainName])
                .lambda.branches
                .single()
                .body as TypedRepresentation.Expr.Application
        val selection = call.left as TypedRepresentation.Expr.Let
        assertEquals(ordNum.dictionary, (selection.matcher as TypedRepresentation.Expr.Identifier).name)
        val pattern = selection.pattern as TypedRepresentation.Pattern.Data
        assertTrue(pattern.args[0] is TypedRepresentation.Pattern.Wildcard)
        assertTrue(pattern.args[1] is TypedRepresentation.Pattern.Variable)
    }
}
