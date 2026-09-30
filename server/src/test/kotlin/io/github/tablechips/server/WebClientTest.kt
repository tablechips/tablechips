package io.github.tablechips.server

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The web client is one file of plain script, with no build step to catch what
 * a compiler would. Two top-level functions with the same name are the worst of
 * it: the second silently replaces the first, and the screen the first drew
 * just stops appearing. It has happened twice — the seats, then the hands a
 * bank game settles — so it is checked here.
 */
class WebClientTest {

    private val page: String =
        WebClientTest::class.java.classLoader.getResourceAsStream("web/index.html")!!
            .bufferedReader().use { it.readText() }

    @Test
    fun `no function in the web client is defined twice`() {
        val names = Regex("""^(?:async\s+)?function\s+(\w+)\s*\(""", RegexOption.MULTILINE)
            .findAll(page)
            .map { it.groupValues[1] }
            .toList()
        val twice = names.groupingBy { it }.eachCount().filterValues { it > 1 }.keys

        assertTrue(names.size > 50, "the functions were found at all")
        assertTrue(twice.isEmpty(), "defined more than once: $twice")
    }
}
