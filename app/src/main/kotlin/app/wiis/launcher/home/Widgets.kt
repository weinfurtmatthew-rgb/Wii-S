package app.wiis.launcher.home

import android.annotation.SuppressLint
import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.SizeF
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import app.wiis.designsystem.glassBox
import app.wiis.launcher.ui.Hint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.wiis.designsystem.LocalWiis
import app.wiis.designsystem.TileIcon
import app.wiis.launcher.AppGraph
import app.wiis.launcher.graph
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.lang.ref.WeakReference
import java.security.MessageDigest
import kotlin.math.roundToInt

// ---- Your own HTML widgets ---------------------------------------------------------------------

/** An HTML widget in the widgets folder: [ref] is its path there ("clock.html", "weather/index.html"). */
data class HtmlWidgetEntry(val ref: String, val name: String)

/**
 * Paths and the sandbox's rules, apart from Android so they can be tested. Every widget gets its
 * own origin on the reserved `.invalid` domain (so its storage is its own and nothing can reach the
 * internet by it); its files come from the widgets folder through the WebView's request hook.
 */
object WidgetPaths {
    const val DOMAIN = "wiis-widget.invalid"

    /** A widget's name: the file without ".html", or its folder's name. */
    fun name(ref: String): String {
        val parts = ref.split('/').filter { it.isNotEmpty() }
        val last = parts.lastOrNull() ?: return ref
        return if (last.equals("index.html", true) || last.equals("index.htm", true)) parts.getOrNull(parts.size - 2) ?: last
        else last.substringBeforeLast('.')
    }

    /** The widget's origin: one per widget, so two widgets never share storage. */
    fun host(ref: String): String {
        val folder = ref.substringBeforeLast('/', "")
        val key = if (folder.isEmpty()) ref else folder
        val hash = MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).take(6).joinToString("") { "%02x".format(it) }
        return "w$hash.$DOMAIN"
    }

    /** The address a widget is opened at, with its size in CSS pixels and the light or dark look. */
    fun url(ref: String, widthPx: Int, heightPx: Int, dark: Boolean): String =
        "https://${host(ref)}/" + ref.split('/').joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") } + "?w=$widthPx&h=$heightPx&dark=${if (dark) 1 else 0}"

    /**
     * The folder path a request asks for, or null when it may not have one: another host, a
     * scheme other than https, or a path that climbs out of the folder.
     */
    fun requested(scheme: String?, host: String?, decodedPath: String?): String? {
        if (scheme != "https" || host == null || !host.endsWith(".$DOMAIN")) return null
        val parts = (decodedPath ?: return null).split('/').filter { it.isNotEmpty() }
        if (parts.isEmpty() || parts.any { it == ".." || it == "." || '\\' in it }) return null
        return parts.joinToString("/")
    }

    fun mime(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
        "html", "htm" -> "text/html"
        "css" -> "text/css"
        "js", "mjs" -> "text/javascript"
        "json" -> "application/json"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "woff" -> "font/woff"
        "woff2" -> "font/woff2"
        "ttf" -> "font/ttf"
        "otf" -> "font/otf"
        "mp3" -> "audio/mpeg"
        "ogg" -> "audio/ogg"
        "wav" -> "audio/wav"
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        else -> "application/octet-stream"
    }

    /**
     * Sent with every file: the widget may use its own files, data: and blob: URLs, inline styles
     * and scripts, and nothing from anywhere else (no fetch, XHR, WebSocket or form to the internet).
     */
    const val CSP = "default-src 'self' data: blob: 'unsafe-inline' 'unsafe-eval'; connect-src 'self'; form-action 'none'; base-uri 'self'; frame-ancestors 'none'"
}

/** The widgets folder you picked: lists its widgets and opens their files (read-only). */
class WidgetFolder(private val resolver: ContentResolver, private val tree: Uri) {
    private data class Doc(val id: String, val name: String, val dir: Boolean)
    private val children = HashMap<String, List<Doc>>()

    private fun list(docId: String): List<Doc> = synchronized(children) {
        children.getOrPut(docId) {
            val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
            val out = ArrayList<Doc>()
            runCatching {
                resolver.query(uri, PROJECTION, null, null, null)?.use { c ->
                    while (c.moveToNext()) out += Doc(c.getString(0), c.getString(1) ?: continue, c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR)
                }
            }
            out
        }
    }

    private val root get() = DocumentsContract.getTreeDocumentId(tree)

    /** .html and .htm files at the top, and folders with an index.html, A to Z. */
    fun widgets(): List<HtmlWidgetEntry> {
        val top = list(root)
        val files = top.filter { !it.dir && it.name.substringAfterLast('.', "").lowercase() in setOf("html", "htm") }.map { it.name }
        val folders = top.filter { it.dir }.mapNotNull { d -> list(d.id).firstOrNull { !it.dir && it.name.lowercase() in setOf("index.html", "index.htm") }?.let { "${d.name}/${it.name}" } }
        return (files + folders).map { HtmlWidgetEntry(it, WidgetPaths.name(it)) }.sortedBy { it.name.lowercase() }
    }

    /** The file at [path] (from [WidgetPaths.requested]), or null. */
    fun open(path: String): InputStream? {
        var docId = root
        val parts = path.split('/')
        parts.forEachIndexed { i, part ->
            val doc = list(docId).firstOrNull { it.name == part && it.dir == (i < parts.lastIndex) } ?: return null
            docId = doc.id
        }
        return runCatching { resolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree, docId)) }.getOrNull()
    }

    private companion object {
        val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
    }
}

/** Serves the widget's own files and refuses everything else; it can't navigate anywhere. */
private class SandboxClient(private val folder: WidgetFolder) : WebViewClient() {
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
        val url = request.url
        val path = WidgetPaths.requested(url.scheme, url.host, url.path) ?: return refuse(403, "Blocked")
        val stream = folder.open(path) ?: return refuse(404, "Not found")
        return WebResourceResponse(WidgetPaths.mime(path), "utf-8", 200, "OK", mapOf("Content-Security-Policy" to WidgetPaths.CSP, "Cache-Control" to "no-store"), stream)
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true

    private fun refuse(code: Int, reason: String) = WebResourceResponse("text/plain", "utf-8", code, reason, emptyMap(), ByteArrayInputStream(ByteArray(0)))
}

@SuppressLint("SetJavaScriptEnabled")
private fun sandboxedWebView(context: Context, folder: WidgetFolder, ref: String, widthPx: Int, heightPx: Int, dark: Boolean, interactive: Boolean, onClose: () -> Unit): WebView =
    WebView(context).apply {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        isFocusable = interactive
        isFocusableInTouchMode = interactive
        isVerticalScrollBarEnabled = interactive
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        with(settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            setGeolocationEnabled(false)
            mediaPlaybackRequiresUserGesture = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            cacheMode = WebSettings.LOAD_NO_CACHE
        }
        webViewClient = SandboxClient(folder)
        webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) = request.deny()
            override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) { callback?.invoke(origin, false, false) }
        }
        if (interactive) {
            // B (and Back) close it; everything else is the page's.
            setOnKeyListener { _, code, ev ->
                if (code != KeyEvent.KEYCODE_BUTTON_B && code != KeyEvent.KEYCODE_BACK) return@setOnKeyListener false
                if (ev.action == KeyEvent.ACTION_UP) onClose()
                true
            }
        }
        loadUrl(WidgetPaths.url(ref, widthPx, heightPx, dark))
    }

/** A live HTML widget, paused with the screen and torn down when it leaves. */
@Composable
private fun HtmlWidgetLive(ref: String, interactive: Boolean, onClose: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val graph = context.graph
    val tree = graph.widgetsFolder
    if (tree == null) {
        WidgetNote("Choose your widgets folder in Wii-S settings → Widgets.", modifier)
        return
    }
    val folder = remember(tree) { WidgetFolder(context.contentResolver, tree) }
    val dark = LocalWiis.current.glassIsDark
    val holder = remember { arrayOfNulls<WebView>(1) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> holder[0]?.onPause()
                Lifecycle.Event.ON_RESUME -> holder[0]?.onResume()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    BoxWithConstraints(modifier) {
        val w = maxWidth.value.roundToInt()
        val h = maxHeight.value.roundToInt()
        AndroidView(
            factory = { ctx -> sandboxedWebView(ctx, folder, ref, w, h, dark, interactive, onClose).also { holder[0] = it; if (interactive) it.requestFocus() } },
            onRelease = { it.stopLoading(); it.destroy(); holder[0] = null },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

// ---- Android widgets ---------------------------------------------------------------------------

/** An installed Android widget you can add: its provider, names and smallest size in dp. */
data class AndroidWidgetChoice(val provider: ComponentName, val label: String, val app: String, val minWidthDp: Int, val minHeightDp: Int)

/** The size a new Android widget tile starts at: big enough for the widget's smallest size. */
fun androidWidgetSize(minWidthDp: Int, minHeightDp: Int, cellWidthDp: Int = 115, cellHeightDp: Int = 98): String {
    val wide = minWidthDp > cellWidthDp
    val tall = minHeightDp > cellHeightDp
    return when {
        tall -> "2x2"
        wide -> "2x1"
        else -> "1x1"
    }
}

/** The host views on screen, so A on a tile can press the widget. */
object AndroidWidgetViews {
    private val views = HashMap<Int, WeakReference<AppWidgetHostView>>()

    fun put(id: Int, view: AppWidgetHostView) = synchronized(views) { views[id] = WeakReference(view) }
    fun remove(id: Int, view: AppWidgetHostView) = synchronized(views) { if (views[id]?.get() === view) views.remove(id) }

    /** Presses the widget's largest clickable part (usually the whole widget); false when it has none. */
    fun press(id: Int): Boolean {
        val root = synchronized(views) { views[id]?.get() } ?: return false
        var best: View? = null
        var bestArea = 0
        fun walk(v: View) {
            if (v.isShown && (v.isClickable || v.hasOnClickListeners()) && v !== root) {
                val area = v.width * v.height
                if (area > bestArea) { best = v; bestArea = area }
            }
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(root)
        return best?.performClick() ?: false
    }
}

@Composable
private fun AndroidWidgetLive(id: Int, modifier: Modifier) {
    val context = LocalContext.current
    val graph = context.graph
    val info = remember(id) { runCatching { graph.widgetManager.getAppWidgetInfo(id) }.getOrNull() }
    if (info == null) {
        WidgetNote("This widget is gone. Remove the tile, then add it again.", modifier)
        return
    }
    BoxWithConstraints(modifier) {
        val w = maxWidth.value
        val h = maxHeight.value
        AndroidView(
            factory = { ctx -> graph.widgetHost.createView(ctx, id, info).also { it.setPadding(0, 0, 0, 0); AndroidWidgetViews.put(id, it) } },
            update = { v -> runCatching { v.updateAppWidgetSize(Bundle(), listOf(SizeF(w, h))) } },
            onRelease = { AndroidWidgetViews.remove(id, it) },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Adding an Android widget: a new widget id, Android's "allow Wii-S to create widgets?" the first
 * time, the widget's own setup screen if it has one, then the tile. Cancelled anywhere, the id is
 * given back.
 */
class WidgetBinder(private val graph: AppGraph) {
    class Pending(val id: Int, val info: AppWidgetProviderInfo, val before: Int?, val at: Pair<Int, Int>?, val size: String)

    private var pending: Pending? = null

    /** Set by the screen: puts the bound widget on the page. */
    var onAdded: (Pending) -> Unit = {}

    fun start(provider: ComponentName, before: Int?, at: Pair<Int, Int>?, activity: Activity?, bind: ActivityResultLauncher<Intent>) {
        val info = graph.widgetManager.installedProviders.firstOrNull { it.provider == provider } ?: return
        cancel()
        val density = graph.context.resources.displayMetrics.density
        val size = androidWidgetSize((info.minWidth / density).roundToInt(), (info.minHeight / density).roundToInt())
        val id = graph.widgetHost.allocateAppWidgetId()
        pending = Pending(id, info, before, at, size)
        if (graph.widgetManager.bindAppWidgetIdIfAllowed(id, provider)) {
            configureOrFinish(activity)
        } else {
            bind.launch(
                Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider),
            )
        }
    }

    fun onBindResult(ok: Boolean, activity: Activity?) = if (ok) configureOrFinish(activity) else cancel()

    private fun configureOrFinish(activity: Activity?) {
        val p = pending ?: return
        val optional = p.info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0
        if (p.info.configure != null && !optional && activity != null) {
            runCatching { graph.widgetHost.startAppWidgetConfigureActivityForResult(activity, p.id, 0, REQUEST_CONFIGURE, null) }
                .onFailure { finish() }
        } else {
            finish()
        }
    }

    fun onConfigureResult(ok: Boolean) = if (ok) finish() else cancel()

    private fun finish() {
        val p = pending ?: return
        pending = null
        onAdded(p)
    }

    private fun cancel() {
        val p = pending ?: return
        pending = null
        runCatching { graph.widgetHost.deleteAppWidgetId(p.id) }
    }

    companion object {
        const val HOST_ID = 0x5753
        const val REQUEST_CONFIGURE = 0x5754
    }
}

/** Reads the installed Android widgets, A to Z. */
fun androidWidgetChoices(context: Context, manager: AppWidgetManager): List<AndroidWidgetChoice> {
    val pm = context.packageManager
    val density = context.resources.displayMetrics.density
    return runCatching { manager.installedProviders }.getOrDefault(emptyList()).map { info ->
        AndroidWidgetChoice(
            provider = info.provider,
            label = runCatching { info.loadLabel(pm) }.getOrNull() ?: info.provider.className.substringAfterLast('.'),
            app = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString() }.getOrDefault(info.provider.packageName),
            minWidthDp = (info.minWidth / density).roundToInt(),
            minHeightDp = (info.minHeight / density).roundToInt(),
        )
    }.sortedWith(compareBy({ it.app.lowercase() }, { it.label.lowercase() }))
}

/** The activity a context belongs to (the configure screen is started from it). */
fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

// ---- Drawing them: the real thing on the device, a stand-in in previews and tests ---------------

/** How tiles draw HTML and Android widgets. The launcher provides [Live]; tests and previews get stand-ins. */
class WidgetRenderers(
    val html: @Composable (ref: String, interactive: Boolean, onClose: () -> Unit, modifier: Modifier) -> Unit,
    val android: @Composable (id: Int, modifier: Modifier) -> Unit,
) {
    companion object {
        val Live = WidgetRenderers(
            html = { ref, interactive, onClose, modifier -> HtmlWidgetLive(ref, interactive, onClose, modifier) },
            android = { id, modifier -> AndroidWidgetLive(id, modifier) },
        )
        val StandIn = WidgetRenderers(
            html = { ref, _, _, modifier -> WidgetStandIn("html", WidgetPaths.name(ref), "HTML widget", modifier) },
            android = { _, modifier -> WidgetStandIn("widget", "Android widget", "Shown live on the Thor", modifier) },
        )
    }
}

val LocalWidgetRenderers = staticCompositionLocalOf { WidgetRenderers.StandIn }

@Composable
private fun WidgetStandIn(icon: String, title: String, note: String, modifier: Modifier) {
    val style = LocalWiis.current
    Column(
        modifier.fillMaxSize().background(style.glassText.copy(alpha = 0.05f)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TileIcon(if (icon == "html") "code" else "widget", 24.dp, style.glassTextMuted)
        Text(title, style = style.label.copy(color = style.glassText, fontSize = 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(note, style = style.label.copy(color = style.glassTextMuted, fontSize = 10.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun WidgetNote(text: String, modifier: Modifier) {
    val style = LocalWiis.current
    Box(modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
        Text(text, style = style.label.copy(color = style.glassTextMuted, fontSize = 11.sp), textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * An HTML widget full screen (A on its tile): over the dimmed screen in a glass frame with its name
 * and "B Close"; the page gets the D-pad (arrow keys) and A (Enter). B closes it.
 */
@Composable
internal fun HtmlWidgetScreen(ref: String, onClose: () -> Unit) {
    val style = LocalWiis.current
    val renderers = LocalWidgetRenderers.current
    val focus = remember { FocusRequester() }
    BackHandler(onBack = onClose)
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { focus.requestFocus() } }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (style.glassIsDark) 0.55f else 0.4f))
            .padding(horizontal = 24.dp, vertical = 18.dp),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                // B closes it when the page doesn't have the keys (a stand-in, or before it has loaded).
                .onPreviewKeyEvent { e ->
                    if (e.key != Key.ButtonB && e.key != Key.Back) return@onPreviewKeyEvent false
                    if (e.type == KeyEventType.KeyUp) onClose()
                    true
                }
                .focusRequester(focus)
                .focusProperties { onExit = { cancelFocusChange() } }
                .focusable()
                .glassBox(style, RoundedCornerShape(style.spec.shape.dialogRadius.coerceAtMost(28).dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(WidgetPaths.name(ref), style = style.label.copy(color = style.glassText, fontSize = 16.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Hint("B", "Close")
            }
            Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(style.spec.glass.radius.dp))) {
                renderers.html(ref, true, onClose, Modifier.fillMaxSize())
            }
        }
    }
}
