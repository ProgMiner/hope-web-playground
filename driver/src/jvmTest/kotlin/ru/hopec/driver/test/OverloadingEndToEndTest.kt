package ru.hopec.driver.test

import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.wasm.Parser
import kotlinx.coroutines.runBlocking
import okio.Buffer
import ru.hopec.core.CompilationStatus
import ru.hopec.core.GlobalCompilationContext
import ru.hopec.core.MultiStatus
import ru.hopec.core.isError
import ru.hopec.driver.Hopec
import ru.hopec.parser.treesitter.parseHope
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OverloadingEndToEndTest {
    private fun errors(source: String): String {
        val tree = runBlocking { parseHope(source) }
        val status = Hopec(GlobalCompilationContext()).run(tree, Buffer())
        assertTrue(status.isError(), "expected compilation to fail")
        return messages(status).joinToString("\n")
    }

    private fun messages(status: CompilationStatus): List<String> =
        if (status is MultiStatus) status.children().flatMap { messages(it) } else listOf(status.message)

    private fun compile(source: String): ByteArray? {
        val tree = runBlocking { parseHope(source) }
        val buffer = Buffer()
        val status = Hopec(GlobalCompilationContext()).run(tree, buffer)
        if (status.isError()) return null
        return buffer.readByteArray()
    }

    private fun runMain(source: String): Long {
        val binary = compile(source) ?: error("compilation failed:\n$source")
        val main = Instance.builder(Parser.parse(binary)).build().export("main")
        return main.apply(0)[0]
    }

    @Test
    fun `equality on numbers`() {
        val source =
            """
            dec main : truval
            --- main <= 2 + 2 = 4
            """.trimIndent()
        assertEquals(1, runMain(source))
    }

    private fun mainTruval(body: String) = runMain("dec main : truval\n--- main <= $body")

    @Test
    fun `equality on strings is structural`() {
        assertEquals(1, mainTruval("\"ab\" = \"ab\""))
        assertEquals(0, mainTruval("\"ab\" = \"abc\""))
    }

    @Test
    fun `equality on nested lists and pairs`() {
        assertEquals(1, mainTruval("[[1, 2], [3]] = [[1, 2], [3]]"))
        assertEquals(0, mainTruval("[[1, 2], [3]] = [[1, 2], [4]]"))
        assertEquals(1, mainTruval("(1, 'a', \"b\") = (1, 'a', \"b\")"))
        assertEquals(0, mainTruval("(1, true) = (1, false)"))
    }

    @Test
    fun `polymorphic function using equality`() {
        val source =
            """
            typevar alpha
            dec elem : alpha # list alpha -> truval given Eq alpha
            --- elem(_, nil) <= false
            --- elem(x, y :: ys) <= (x = y) then true else elem(x, ys)

            dec main : truval
            --- main <= (elem("b", ["a", "b"])) then elem(3, [1, 2]) else false
            """.trimIndent()
        assertEquals(0, runMain(source))
        assertEquals(1, runMain(source.replace("elem(3, [1, 2])", "elem([2], [[1], [2]])")))
    }

    @Test
    fun `equality on type variable requires given Eq`() {
        val source =
            """
            typevar alpha
            dec same : alpha # alpha -> truval
            --- same(x, y) <= x = y

            dec main : truval
            --- main <= same(1, 1)
            """.trimIndent()
        assertContains(errors(source), "add 'given Eq 't0'")
    }

    @Test
    fun `functions have no equality`() {
        val source =
            """
            dec inc : num -> num
            --- inc(x) <= x + 1

            dec main : truval
            --- main <= inc = inc
            """.trimIndent()
        assertContains(errors(source), "no instance for 'Eq' at function type")
    }

    @Test
    fun `user instance of built-in Eq`() {
        fun source(right: String) =
            """
            data color == red ++ green ++ blue

            instance Eq color with
              --- red = red <= true
              --- green = green <= true
              --- blue = blue <= true
              --- _ = _ <= false
            end

            dec main : truval
            --- main <= [red, blue] = $right
            """.trimIndent()
        assertEquals(1, runMain(source("[red, blue]")))
        assertEquals(0, runMain(source("[red, green]")))
    }

    private val equalityClass =
        """
        typevar alpha, beta

        class Same alpha with
          dec same : alpha # alpha -> truval
        end

        instance Same num with
          --- same(x, y) <= x = y
        end

        instance Same (list alpha) given Same alpha with
          --- same(nil, nil) <= true
          --- same(x :: xs, y :: ys) <= (same(x, y)) then same(xs, ys) else false
          --- same(_, _) <= false
        end

        instance Same (alpha # beta) given Same alpha, Same beta with
          --- same((a, b), (c, d)) <= (same(a, c)) then same(b, d) else false
        end

        dec member : alpha # list alpha -> truval given Same alpha
        --- member(_, nil) <= false
        --- member(x, y :: ys) <= (same(x, y)) then true else member(x, ys)

        """.trimIndent()

    private fun runWithClass(main: String) = runMain(equalityClass + "\n" + main)

    @Test
    fun `method at base instance`() {
        assertEquals(1, runWithClass("dec main : truval\n--- main <= same(3, 3)"))
        assertEquals(0, runWithClass("dec main : truval\n--- main <= same(3, 4)"))
    }

    @Test
    fun `instance with context builds dictionary from dictionaries`() {
        assertEquals(1, runWithClass("dec main : truval\n--- main <= same([1, 2, 3], [1, 2, 3])"))
        assertEquals(0, runWithClass("dec main : truval\n--- main <= same([1, 2, 3], [1, 2])"))
        assertEquals(1, runWithClass("dec main : truval\n--- main <= same([[1], [2, 3]], [[1], [2, 3]])"))
        assertEquals(0, runWithClass("dec main : truval\n--- main <= same([[1], [2, 3]], [[1], [2, 4]])"))
    }

    @Test
    fun `tuple instance`() {
        assertEquals(1, runWithClass("dec main : truval\n--- main <= same((1, [2]), (1, [2]))"))
        assertEquals(0, runWithClass("dec main : truval\n--- main <= same((1, [2]), (1, [3]))"))
    }

    @Test
    fun `overloaded function receives dictionary`() {
        assertEquals(1, runWithClass("dec main : truval\n--- main <= member([2], [[1], [2]])"))
        assertEquals(0, runWithClass("dec main : truval\n--- main <= member(5, [1, 2, 3])"))
    }

    @Test
    fun `overloaded function passes its dictionary further`() {
        val source =
            """
            dec memberBoth : alpha # alpha # list alpha -> truval given Same alpha
            --- memberBoth(x, y, l) <= (member(x, l)) then member(y, l) else false

            dec main : truval
            --- main <= memberBoth([1], [2], [[2], [1]])
            """.trimIndent()
        assertEquals(1, runWithClass(source))
    }

    @Test
    fun `missing given is a compile error`() {
        val source =
            """
            dec bad : alpha # alpha -> truval
            --- bad(x, y) <= same(x, y)

            dec main : truval
            --- main <= bad(1, 1)
            """.trimIndent()
        val message = errors(equalityClass + "\n" + source)
        assertContains(message, "add 'given Same 't0'")
    }

    @Test
    fun `missing instance is a compile error`() {
        val message = errors(equalityClass + "\ndec main : truval\n--- main <= same('a', 'b')")
        assertContains(message, "no instance for 'Same char'")
    }

    @Test
    fun `README example`() {
        val readme =
            listOf("README.md", "../README.md")
                .map { java.io.File(it) }
                .first { it.exists() }
                .readText()
        val example = readme.substringAfter("## Overloading").substringAfter("```hope\n").substringBefore("```")
        val main =
            """
            dec main : num
            --- main <= (member(2, [1, 2])) then size([[true], [false, true]]) else 0
            """.trimIndent()
        assertEquals(3, runMain(example + main))
    }

    private val orderClass =
        """
        typevar alpha

        class Order alpha with
          dec less : alpha # alpha -> truval
          dec same : alpha # alpha -> truval
          dec notLess : alpha # alpha -> truval
        end

        instance Order num with
          --- less(x, y) <= x < y
          --- same(x, y) <= x = y
          --- notLess(x, y) <= (less(x, y)) then false else true
        end

        instance Order (list alpha) given Order alpha with
          --- less(_, nil) <= false
          --- less(nil, _) <= true
          --- less(x :: xs, y :: ys) <= (less(x, y)) then true else ((same(x, y)) then less(xs, ys) else false)
          --- same(nil, nil) <= true
          --- same(x :: xs, y :: ys) <= (same(x, y)) then same(xs, ys) else false
          --- same(_, _) <= false
          --- notLess(x, y) <= (less(x, y)) then false else true
        end

        dec insert : alpha # list alpha -> list alpha given Order alpha
        --- insert(x, nil) <= [x]
        --- insert(x, y :: ys) <= (notLess(y, x)) then x :: y :: ys else y :: insert(x, ys)

        dec sort : list alpha -> list alpha given Order alpha
        --- sort(nil) <= nil
        --- sort(x :: xs) <= insert(x, sort(xs))

        """.trimIndent()

    private fun runWithOrder(main: String) = runMain(orderClass + "\n" + main)

    @Test
    fun `class with several methods`() {
        assertEquals(1, runWithOrder("dec main : truval\n--- main <= less(1, 2)"))
        assertEquals(0, runWithOrder("dec main : truval\n--- main <= same(1, 2)"))
        assertEquals(1, runWithOrder("dec main : truval\n--- main <= notLess(2, 1)"))
    }

    @Test
    fun `instance with context of class with several methods`() {
        assertEquals(1, runWithOrder("dec main : truval\n--- main <= less([1, 2], [1, 3])"))
        assertEquals(0, runWithOrder("dec main : truval\n--- main <= less([1, 3], [1, 2])"))
        assertEquals(1, runWithOrder("dec main : truval\n--- main <= same([[1], [2]], [[1], [2]])"))
        assertEquals(1, runWithOrder("dec main : truval\n--- main <= notLess([[2]], [[1, 5]])"))
    }

    @Test
    fun `overloaded functions use several methods`() {
        assertEquals(1, runWithOrder("dec main : truval\n--- main <= sort([3, 1, 2]) = [1, 2, 3]"))
        assertEquals(1, runWithOrder("dec main : truval\n--- main <= sort([[2], [1, 2], [1]]) = [[1], [1, 2], [2]]"))
    }

    @Test
    fun `instance must define all methods`() {
        val source =
            """
            typevar alpha
            class Pair alpha with
              dec first : alpha -> num
              dec second : alpha -> num
            end

            instance Pair num with
              --- first(x) <= x
            end

            dec main : num
            --- main <= first(1)
            """.trimIndent()
        assertContains(errors(source), "must define exactly the methods")
    }
}
