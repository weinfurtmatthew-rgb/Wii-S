package app.wiis.launcher.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetsTest {
    @Test fun `a widget is named after its file or its folder`() {
        assertEquals("Clock", WidgetPaths.name("Clock.html"))
        assertEquals("weather", WidgetPaths.name("weather/index.html"))
    }

    @Test fun `each widget has its own origin on a domain that never resolves`() {
        val a = WidgetPaths.host("clock.html")
        val b = WidgetPaths.host("weather/index.html")
        assertNotEquals(a, b)
        assertTrue(a.endsWith(".wiis-widget.invalid"))
        // A widget's files share its folder's origin.
        assertEquals(WidgetPaths.host("weather/index.html"), WidgetPaths.host("weather/other.html"))
        assertEquals("https://$a/clock.html?w=247&h=213&dark=1", WidgetPaths.url("clock.html", 247, 213, dark = true))
        assertTrue(WidgetPaths.url("My Clock.html", 1, 1, false).contains("/My%20Clock.html?"))
    }

    @Test fun `only the widget's own files are served`() {
        val host = WidgetPaths.host("weather/index.html")
        assertEquals("weather/icon.png", WidgetPaths.requested("https", host, "/weather/icon.png"))
        // Other hosts, plain http, and paths that climb out of the folder get nothing.
        assertNull(WidgetPaths.requested("https", "example.com", "/weather/icon.png"))
        assertNull(WidgetPaths.requested("http", host, "/weather/icon.png"))
        assertNull(WidgetPaths.requested("https", host, "/weather/../../secret.txt"))
        assertNull(WidgetPaths.requested("https", host, "/"))
        assertTrue("connect-src 'self'" in WidgetPaths.CSP)
    }

    @Test fun `files go out with their types`() {
        assertEquals("text/html", WidgetPaths.mime("a/index.HTML"))
        assertEquals("font/woff2", WidgetPaths.mime("fonts/x.woff2"))
        assertEquals("application/octet-stream", WidgetPaths.mime("README"))
    }

    @Test fun `an Android widget's tile starts big enough for it`() {
        assertEquals("1x1", androidWidgetSize(40, 40))
        assertEquals("2x1", androidWidgetSize(180, 40))
        assertEquals("2x2", androidWidgetSize(180, 180))
    }

    @Test fun `widget tiles come in their sizes and can start at a given one`() {
        assertEquals("2x2", Board.sizesFor(Board.HTML).first())
        val file = Board.add(BoardFile(), Board.ANDROID, "7", 6, 3, size = "2x2")!!
        assertEquals("2x2", file.tiles.first { it.kind == Board.ANDROID }.size)
        // A size the kind doesn't come in falls back to its own.
        assertEquals("2x1", Board.add(BoardFile(), Board.ANDROID, "8", 6, 3, size = "3x3")!!.tiles.first { it.kind == Board.ANDROID }.size)
    }
}
