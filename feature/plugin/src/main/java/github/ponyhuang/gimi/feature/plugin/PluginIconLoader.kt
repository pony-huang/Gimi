package github.ponyhuang.gimi.feature.plugin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

/** 从插件包读取应用图标；失败（无图标/不可见）返回 null，交由调用方回退默认图标。 */
internal fun loadPluginIcon(context: Context, packageName: String): Drawable? = runCatching {
    val info = context.packageManager.getApplicationInfo(packageName, 0)
    context.packageManager.getApplicationIcon(info)
}.getOrNull()

/** 把 Android Drawable 转成 Bitmap，供 Compose [androidx.compose.foundation.Image] 渲染。 */
internal fun Drawable.toBitmap(): Bitmap {
    if (this is BitmapDrawable) return bitmap
    val width = intrinsicWidth.coerceAtLeast(1)
    val height = intrinsicHeight.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, width, height)
    draw(canvas)
    return bitmap
}
