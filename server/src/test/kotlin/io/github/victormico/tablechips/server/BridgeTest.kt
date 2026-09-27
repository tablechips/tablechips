package io.github.victormico.tablechips.server

import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.Decoder
import io.github.victormico.tablechips.core.Table
import io.github.victormico.tablechips.core.TableConfig
import io.github.victormico.tablechips.protocol.TableLink
import io.github.victormico.tablechips.protocol.parseTableLink
import io.github.victormico.tablechips.protocol.qrCode
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The way in for somebody who has just pointed a camera at a code. None of it
 * can lean on a verified link, so it is worth proving that what the code
 * carries is exactly what a scanner will read back.
 */
class BridgeTest {

    private lateinit var server: TableServer
    private var port = 0
    private val client = HttpClient(CIO)

    @BeforeTest
    fun start() = runBlocking {
        val host = TableHost(Table("48KB", TableConfig(), clock = { 0 }))
        server = TableServer(host, preferredPort = 0)
        port = server.start()
        Unit
    }

    @AfterTest
    fun stop() {
        client.close()
        server.stop()
    }

    /** Encode, then read it back the way a camera would. */
    private fun readBack(text: String): String {
        val matrix = qrCode(text)
        val bits = BitMatrix(matrix.size, matrix.size)
        for (y in 0 until matrix.size) {
            for (x in 0 until matrix.size) if (matrix[x, y]) bits.set(x, y)
        }
        return Decoder().decode(bits).text
    }

    @Test
    fun `a code carries the address a scanner can open`() {
        val link = "http://192.168.0.17:8080/join?room=48KB"

        assertEquals(link, readBack(link))
    }

    @Test
    fun `the code survives the sizes a table actually uses`() {
        val links = listOf(
            "http://192.168.43.1:8080/join?room=AB12",
            "http://10.250.245.49:9000/join?room=ZGWH",
            "http://192.168.0.17:8080/",
        )

        links.forEach { assertEquals(it, readBack(it)) }
    }

    @Test
    fun `the table serves its own code as vectors`() = runBlocking {
        val response = client.get("http://127.0.0.1:$port/qr.svg")
        val svg = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(svg.startsWith("<svg"), svg.take(80))
        // Squares on the one light surface of the product, as the design asks.
        assertTrue(svg.contains("shape-rendering=\"crispEdges\""), svg.take(200))
        assertTrue(svg.contains("#F2EAE0"), svg.take(200))
    }

    @Test
    fun `the bridge page offers the app and the browser, and needs neither`() = runBlocking {
        val page = client.get("http://127.0.0.1:$port/join?room=48KB").bodyAsText()

        assertTrue(page.contains("scheme=tablechips"), "the custom scheme is the way into the app")
        assertTrue(page.contains("io.github.victormico.tablechips"), "the intent names the package")
        assertTrue(page.contains("browser_fallback_url"), "and falls back to the web client")
        // Nothing on this page may come from outside the phone serving it.
        assertTrue(!page.contains("https://"), "the bridge must not reach for the internet")
    }

    @Test
    fun `the bridge speaks the three languages`() = runBlocking {
        val page = client.get("http://127.0.0.1:$port/join").bodyAsText()

        assertTrue(page.contains("Jugar al navegador"))
        assertTrue(page.contains("Jugar en el navegador"))
        assertTrue(page.contains("Play in the browser"))
    }

    @Test
    fun `a link is read the same however it arrives`() {
        val default = 8080
        val expected = TableLink("192.168.0.17", 8080, "48KB")

        // what the intent carries, what the camera reads, what a person types
        assertEquals(
            expected,
            parseTableLink("tablechips://join?host=192.168.0.17&port=8080&room=48KB", default),
        )
        assertEquals(expected, parseTableLink("http://192.168.0.17:8080/join?room=48KB", default))
        assertEquals(
            TableLink("192.168.0.17", 8080),
            parseTableLink("  192.168.0.17  ", default),
        )
        assertEquals(TableLink("192.168.0.17", 9000), parseTableLink("192.168.0.17:9000", default))
    }

    @Test
    fun `nonsense is refused rather than half understood`() {
        val default = 8080

        assertEquals(null, parseTableLink("", default))
        assertEquals(null, parseTableLink("   ", default))
        assertEquals(null, parseTableLink("http://:8080/join", default))
        assertEquals(null, parseTableLink("192.168.0.17:0", default))
        assertEquals(null, parseTableLink("tablechips://join?room=48KB", default))
    }
}
