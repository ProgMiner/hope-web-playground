package ru.hopec.parser.test

import com.goncalossilva.resources.Resource
import ru.hopec.parser.treesitter.TsTree
import ru.hopec.parser.treesitter.factory
import ru.hopec.parser.treesitter.findSharedLibrary
import ru.hopec.parser.treesitter.parseHope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TreeSitterTest {
    @Test
    fun `language loaded`() {
        assertNotNull(factory().loadLanguage(findSharedLibrary()))
    }

    @Test
    fun `simple file parsed`() {
        assertEquals(1U, parseHello().rootNode.childCount)
    }

    @Test
    fun `node contains text`() {
        assertEquals("""write "Hello world\n"""", parseHello().rootNode.text)
    }

    private fun parseHello(): TsTree =
        parseHope(findSharedLibrary(), Resource("hello.hope").readText())

}