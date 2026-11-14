package app.wiis.launcher.home

/** An installed app as the dock sees it. [key] names one launchable activity. */
data class AppRef(val key: String, val packageName: String, val label: String)

/**
 * Which apps sit in the dock. Each slot keeps the app you chose for it while it is installed;
 * empty slots take the first installed app of each kind below, then any other app A to Z.
 */
object DockApps {
    /** Default kinds, in dock order: browser, video, store, music, files, chat. */
    val PREFERRED: List<List<String>> = listOf(
        listOf("com.android.chrome", "org.mozilla.firefox", "com.brave.browser", "com.microsoft.emmx", "com.opera.browser", "com.android.browser"),
        listOf("com.google.android.youtube", "com.netflix.mediaclient", "org.videolan.vlc"),
        listOf("com.android.vending"),
        listOf("com.google.android.apps.youtube.music", "com.spotify.music"),
        listOf("com.google.android.apps.nbu.files", "com.google.android.documentsui", "com.android.documentsui"),
        listOf("com.discord", "org.telegram.messenger", "com.whatsapp"),
    )

    /**
     * @param saved the keys you picked, by slot (null = no choice for that slot).
     * @param defaultBrowser the system's default browser, tried first for the browser kind.
     * @param exclude packages never used to fill a slot (emulators: they are on the games side).
     */
    fun select(
        installed: List<AppRef>,
        saved: List<String?>,
        count: Int,
        preferred: List<List<String>> = PREFERRED,
        defaultBrowser: String? = null,
        exclude: Set<String> = emptySet(),
    ): List<AppRef> {
        if (count <= 0) return emptyList()
        val byKey = installed.associateBy { it.key }
        val byPackage = installed.groupBy { it.packageName }
        val slots = arrayOfNulls<AppRef>(count)
        val used = mutableSetOf<String>()
        for (i in 0 until count) {
            val app = saved.getOrNull(i)?.let(byKey::get) ?: continue
            if (used.add(app.key)) slots[i] = app
        }
        val kinds = preferred.mapIndexed { i, group -> if (i == 0 && defaultBrowser != null) listOf(defaultBrowser) + group else group }
        val defaults = kinds.mapNotNull { group -> group.firstNotNullOfOrNull { pkg -> byPackage[pkg]?.firstOrNull() } }
        val others = installed.filter { it.packageName !in exclude }.sortedBy { it.label.lowercase() }
        val fill = (defaults + others).distinctBy { it.key }.filter { it.key !in used }.iterator()
        for (i in 0 until count) {
            if (slots[i] == null && fill.hasNext()) slots[i] = fill.next().also { used += it.key }
        }
        return slots.filterNotNull()
    }

    /** Saved keys after putting [pick] in [slot]; if [pick] was already in the dock, the two swap. */
    fun choose(current: List<AppRef>, slot: Int, pick: AppRef, count: Int): List<String?> {
        val keys = MutableList(count) { current.getOrNull(it)?.key }
        if (slot !in 0 until count) return keys
        val from = keys.indexOf(pick.key)
        if (from >= 0 && from != slot) keys[from] = keys[slot]
        keys[slot] = pick.key
        return keys
    }

    fun encode(keys: List<String?>): String = keys.joinToString("\n") { it.orEmpty() }

    fun decode(text: String?): List<String?> =
        text?.takeIf { it.isNotEmpty() }?.split("\n")?.map { it.ifEmpty { null } }.orEmpty()
}
