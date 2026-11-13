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

    @Test fun `ignores woff2 faces`() {
        assertEquals(emptyMap<Int, String>(), FontRepository.parseCss(css.replace("format('truetype')", "format('woff2')")))
    }
}
