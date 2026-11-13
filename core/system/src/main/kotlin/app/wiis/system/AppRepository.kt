package app.wiis.system

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.os.Process
import android.os.UserHandle
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppEntry(
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
) {
    val key: String get() = component.flattenToShortString()
}

/**
 * Installed launchable apps, kept current through LauncherApps callbacks (no polling).
 * Icons load lazily off the main thread and are cached at a fixed pixel size.
 */
class AppRepository(
    context: Context,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val ownPackage = context.packageName
    private val iconSizePx = (context.resources.displayMetrics.density * 72).toInt()
    private val iconCache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = refresh(packageName)
        override fun onPackageAdded(packageName: String, user: UserHandle) = refresh(packageName)
        override fun onPackageChanged(packageName: String, user: UserHandle) = refresh(packageName)
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh(null)
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh(null)
    }

    fun start() {
        launcherApps.registerCallback(callback)
        refresh(null)
    }

    private fun refresh(changedPackage: String?) {
        if (changedPackage != null) synchronized(iconCache) {
            iconCache.snapshot().keys.filter { it.startsWith("$changedPackage/") }.forEach(iconCache::remove)
        }
        scope.launch(io) {
            _apps.value = launcherApps.getActivityList(null, Process.myUserHandle())
                .filter { it.componentName.packageName != ownPackage }
                .map { AppEntry(it.label.toString(), it.componentName, it.user) }
                .sortedBy { it.label.lowercase() }
        }
    }

    suspend fun icon(entry: AppEntry): Bitmap? {
        synchronized(iconCache) { iconCache.get(entry.key) }?.let { return it }
        return withContext(io) {
            val info = launcherApps.resolveActivity(
                android.content.Intent(android.content.Intent.ACTION_MAIN)
                    .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                    .setComponent(entry.component),
                entry.user,
            ) ?: return@withContext null
            info.getIcon(0).toBitmap(iconSizePx, iconSizePx).also { bmp ->
                synchronized(iconCache) { iconCache.put(entry.key, bmp) }
            }
        }
    }

    fun launch(entry: AppEntry) {
        launcherApps.startMainActivity(entry.component, entry.user, null, null)
    }
}
