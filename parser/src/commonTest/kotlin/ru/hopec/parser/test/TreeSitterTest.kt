package ru.hopec.parser.test

import com.goncalossilva.resources.Resource
import kotlinx.coroutines.test.runTest
import ru.hopec.parser.treesitter.TsTree
import ru.hopec.parser.treesitter.factory
import ru.hopec.parser.treesitter.parseHope
import ru.hopec.parser.treesitter.sharedLibraryLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TreeSitterTest {
    @Test
    fun `language loaded`() =
        runTest {
            assertNotNull(factory().loadLanguage(sharedLibraryLocation()))
        }

    @Test
    fun `simple file parsed`() =
        runTest {
            assertEquals(1U, parseHello().rootNode.childCount)
        }

    @Test
    fun `node contains text`() =
        runTest {
            assertEquals("""write "Hello world\n"""", parseHello().rootNode.text)
        }

    private suspend fun parseHello(): TsTree = parseHope(Resource("hello.hope").readText())
}
