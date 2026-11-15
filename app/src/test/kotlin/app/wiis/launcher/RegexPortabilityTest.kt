package app.wiis.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Android compiles regular expressions with ICU, which is stricter than the JVM these tests run on:
 * a "{" or "}" that isn't part of a quantifier ("{2}", "{1,3}") or an escape like "\p{M}" must be
 * escaped. 0.2.0 to 0.4.1 crashed on start on the Thor because of one unescaped "}", and no test
 * could see it. This reads every pattern in the app's source and checks it the way ICU would.
 */
class RegexPortabilityTest {
    private val sources = listOf("src/main", "../core/designsystem/src/main", "../core/system/src/main", "../emulator/src/main")
        .map(::File)
        .flatMap { root -> root.walkTopDown().filter { it.extension == "kt" }.toList() }

    @Test fun `every pattern in the source is valid on Android`() {
        val patterns = sources.flatMap { file -> patternsIn(file.readText()).map { "${file.name}: $it" to it } }
        assertTrue("found only ${patterns.size} patterns; is the scan broken?", patterns.size >= 10)
        val problems = patterns.mapNotNull { (where, p) -> braceProblem(p)?.let { "$where -> $it" } }
        assertEquals(emptyList<String>(), problems)
    }

    @Test fun `the check catches the pattern that crashed the Thor`() {
        assertEquals("unescaped } at 22", braceProblem("""@font-face\s*\{([^}]*)}"""))
        assertNull(braceProblem("""@font-face\s*\{([^}]*)\}"""))
        assertNull(braceProblem("""\p{M}+ x{2,3} [{}] \{a\}"""))
        assertEquals("unescaped { at 0", braceProblem("""{name}"""))
    }

    /** Patterns given to Regex(...) as raw ("""…""") or ordinary ("…") string literals. */
    private fun patternsIn(code: String): List<String> {
        val raw = Regex("""Regex\(\s*"{3}(.*?)"{3}""", RegexOption.DOT_MATCHES_ALL).findAll(code).map { it.groupValues[1] }
        val plain = Regex("""Regex\(\s*"((?:[^"\\]|\\.)*)"(?!")""").findAll(code).map { unescapeKotlin(it.groupValues[1]) }
        return (raw + plain).toList()
    }

    private fun unescapeKotlin(s: String): String = buildString {
        var i = 0
        while (i < s.length) {
            if (s[i] == '\\' && i + 1 < s.length) {
                append(when (val c = s[i + 1]) { 'n' -> '\n'; 't' -> '\t'; else -> c })
                i += 2
            } else {
                append(s[i]); i++
            }
        }
    }

    /** Null when every brace is escaped, a quantifier, inside a character class or part of \p{…}. */
    private fun braceProblem(p: String): String? {
        val quantifier = Regex("""\{\d+(,\d*)?}""")
        var i = 0
        var inClass = false
        while (i < p.length) {
            val c = p[i]
            when {
                c == '\\' -> {
                    if (i + 2 < p.length && p[i + 1] in "pPxN" && p[i + 2] == '{') {
                        val end = p.indexOf('}', i + 3)
                        if (end < 0) return "unclosed \\${p[i + 1]}{ at $i"
                        i = end + 1
                    } else {
                        i += 2
                    }
                }
                inClass -> { if (c == ']') inClass = false; i++ }
                c == '[' -> {
                    inClass = true; i++
                    if (i < p.length && p[i] == '^') i++
                    if (i < p.length && p[i] == ']') i++
                }
                c == '{' -> {
                    val m = quantifier.find(p, i)
                    if (m == null || m.range.first != i) return "unescaped { at $i"
                    i = m.range.last + 1
                }
                c == '}' -> return "unescaped } at $i"
                else -> i++
            }
        }
        return null
    }
}
