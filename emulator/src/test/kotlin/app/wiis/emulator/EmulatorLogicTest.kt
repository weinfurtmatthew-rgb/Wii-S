package app.wiis.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EmulatorLogicTest {

    private val registry = EmulatorRegistry.parse(File("src/main/assets/emulators.json").readText())
    private val detector = SystemDetector(registry)

    @Test fun `registry references only known emulators`() {
        for (s in registry.systems) for (c in s.emulators) {
            assertTrue("${s.id} -> ${c.emulator}", registry.emulator(c.emulator) != null)
        }
    }

    @Test fun `every requested emulator is defined`() {
        val expected = setOf(
            "retroarch", "dolphin", "ppsspp", "duckstation", "cemu", "azahar",
            "eden", "gamenative", "melonds", "nethersx2", "xendroid",
        )
        assertEquals(expected, registry.emulators.map { it.id }.toSet())
    }

    @Test fun `unique extension detects system anywhere`() {
        assertEquals("snes", detector.detect(listOf("Roms", "misc"), "Game (USA).sfc")?.id)
        assertEquals("switch", detector.detect(emptyList(), "x.NSP")?.id)
    }

    @Test fun `ambiguous extension needs a folder`() {
        assertNull(detector.detect(listOf("Roms"), "game.iso"))
        assertEquals("psp", detector.detect(listOf("Roms", "PSP"), "game.iso")?.id)
        assertEquals("ps2", detector.detect(listOf("ps2", "rpg"), "game.iso")?.id)
        assertEquals("gc", detector.detect(listOf("gamecube"), "game.rvz")?.id)
    }

    @Test fun `folder that does not accept the extension yields nothing`() {
        assertNull(detector.detect(listOf("psp"), "game.sfc"))
    }

    @Test fun `retroarch intent uses path and core`() {
        val def = registry.emulator("retroarch")!!
        val target = def.targets.first()
        val choice = registry.system("snes")!!.emulators.first()
        val r = IntentSpecBuilder.build(def, target, choice, RomVars("content://x/1", "/storage/emulated/0/snes/a.sfc", "a"))
        val spec = (r as BuildResult.Ok).spec
        assertEquals("/data/data/com.retroarch.aarch64/cores/snes9x_libretro_android.so", spec.extras["LIBRETRO"])
        assertEquals("/storage/emulated/0/snes/a.sfc", spec.extras["ROM"])
        assertTrue(spec.grantUris.isEmpty())
    }

    @Test fun `retroarch without path reports missing path`() {
        val def = registry.emulator("retroarch")!!
        val r = IntentSpecBuilder.build(def, def.targets.first(), EmulatorChoice("retroarch", mapOf("core" to "mgba")), RomVars("content://x/1", null, "a"))
        assertEquals(BuildResult.MissingValue("rom.path"), r)
    }

    @Test fun `saf emulators get uri grant`() {
        val def = registry.emulator("ppsspp")!!
        val spec = (IntentSpecBuilder.build(def, def.targets.first(), EmulatorChoice("ppsspp"), RomVars("content://x/1", null, "a")) as BuildResult.Ok).spec
        assertEquals("content://x/1", spec.data)
        assertEquals(listOf("content://x/1"), spec.grantUris)
    }

    @Test fun `gamenative app id must be an integer`() {
        val def = registry.emulator("gamenative")!!
        val ok = IntentSpecBuilder.build(def, def.targets.first(), EmulatorChoice("gamenative"), RomVars("u", null, "a", contents = { " 620\n" }))
        assertEquals(620, (ok as BuildResult.Ok).spec.intExtras["app_id"])
        val bad = IntentSpecBuilder.build(def, def.targets.first(), EmulatorChoice("gamenative"), RomVars("u", null, "a", contents = { "portal" }))
        assertTrue(bad is BuildResult.BadTemplate)
    }

    @Test fun `saf document ids map to paths`() {
        val a = SafPaths.EXTERNAL_STORAGE_AUTHORITY
        assertEquals("/storage/emulated/0/ROMs/snes/a.sfc", SafPaths.documentIdToPath(a, "primary:ROMs/snes/a.sfc"))
        assertEquals("/storage/1A2B-3C4D/ROMs", SafPaths.documentIdToPath(a, "1A2B-3C4D:ROMs"))
        assertNull(SafPaths.documentIdToPath("com.other.provider", "primary:x"))
    }

    @Test fun `titles are cleaned`() {
        assertEquals("Super Game", RomScanner.cleanTitle("Super Game (USA) [!].sfc"))
        assertEquals("my game", RomScanner.cleanTitle("my_game.gba"))
    }
}

class FolderNamingTest {
    private val registry = EmulatorRegistry.parse(File("src/main/assets/emulators.json").readText())
    private val detector = SystemDetector(registry)

    @Test fun `descriptive folder names are recognised`() {
        assertEquals("nds", detector.detect(listOf("ROMs", "Nintendo DS"), "game.zip")?.id)
        assertEquals("nds", detector.detect(listOf("ROMs", "Nintendo - DS"), "game.7z")?.id)
        assertEquals("switch", detector.detect(listOf("ROMs", "Nintendo Switch"), "game.nsp")?.id)
        assertEquals("ps2", detector.detect(listOf("PlayStation 2"), "game.iso")?.id)
        assertEquals("gba", detector.detect(listOf("Game Boy Advance"), "game.zip")?.id)
    }

    @Test fun `archives outside a system folder are not guessed`() {
        assertNull(detector.detect(listOf("ROMs"), "game.zip"))
    }

    @Test fun `switch homebrew and nca are recognised anywhere`() {
        assertEquals("switch", detector.detect(emptyList(), "app.nro")?.id)
    }
}
