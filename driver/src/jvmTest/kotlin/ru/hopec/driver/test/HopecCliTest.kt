package ru.hopec.driver.test

import com.github.ajalt.clikt.testing.test
import ru.hopec.driver.HopecCompile
import kotlin.test.Test
import kotlin.test.assertEquals

class HopecCliTest {

    @Test
    fun `simple file compiled`() {
        assertEquals("4\n", compile().stdout)
    }

    private fun compile(file: String = helloFile()) = HopecCompile().test(file)

    private fun helloFile() = "../parser/src/commonTest/resources/hello.hope"

}