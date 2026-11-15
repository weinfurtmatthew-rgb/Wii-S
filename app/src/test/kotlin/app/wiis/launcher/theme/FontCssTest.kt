package app.wiis.launcher.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class FontCssTest {
    /** Shape of a real css2 response for an unrecognised user agent (TrueType, one face per weight). */
    private val css = """
        @font-face {
          font-family: 'Fredoka';
          font-style: normal;
          font-weight: 400;
          src: url(https://fonts.gstatic.com/s/fredoka/v17/regular.ttf) format('truetype');
        }
        @font-face {
          font-family: 'Fredoka';
          font-style: normal;
          font-weight: 700;
          font-stretch: normal;
          src: url(https://fonts.gstatic.com/s/fredoka/v17/bold.ttf) format('truetype');
        }
    """.trimIndent()

    @Test fun `parses one truetype url per weight`() {
        assertEquals(
            mapOf(
                400 to "https://fonts.gstatic.com/s/fredoka/v17/regular.ttf",
                700 to "https://fonts.gstatic.com/s/fredoka/v17/bold.ttf",
            ),
            FontRepository.parseCss(css),
        )
    }

    @Test fun `the last face per weight wins and other properties don't confuse it`() {
        val subsets = """
            /* latin-ext */
            @font-face { font-family: 'Poppins'; font-weight: 600; src: url('https://fonts.gstatic.com/s/poppins/ext.ttf') format('truetype'); unicode-range: U+0100-02BA; }
            /* latin */
            @font-face { font-family: 'Poppins'; font-style: normal; font-weight: 600; font-display: swap; src: url(https://fonts.gstatic.com/s/poppins/latin.ttf) format('truetype'); }
            @font-face { font-family: 'Poppins'; font-weight: 300 700; src: url(https://fonts.gstatic.com/s/poppins/var.ttf) format("truetype"); }
        """.trimIndent()
        assertEquals(
            mapOf(600 to "https://fonts.gstatic.com/s/poppins/latin.ttf", 300 to "https://fonts.gstatic.com/s/poppins/var.ttf"),
            FontRepository.parseCss(subsets),
        )
    }

    @Test fun `ignores woff2 faces`() {
        assertEquals(emptyMap<Int, String>(), FontRepository.parseCss(css.replace("format('truetype')", "format('woff2')")))
    }
}
