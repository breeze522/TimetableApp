package com.example.timetable.data

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import java.io.File

/**
 * 自定义课表背景图的本地存储。
 *
 * 用户从相册选中的图（`content://` Uri）**不允许长期直接引用** ——
 * 那个 Uri 的读取授权在进程重启后可能失效，而且用户删掉原图后就变空。
 * 所以选中时立刻把图片**拷贝一份**到应用私有目录（`filesDir/background.jpg`），
 * 之后只读自己的副本，稳定且不占相册权限。
 */
object BackgroundImageStore {

    private const val FILE_NAME = "background_custom.jpg"

    /** 私有目录里的背景图文件 */
    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    /** 是否已有自定义背景图 */
    fun exists(context: Context): Boolean = file(context).exists()

    /** 取自定义背景图的 Uri；文件不存在时返回 null */
    fun uri(context: Context): Uri? {
        val f = file(context)
        return if (f.exists()) f.toUri() else null
    }

    /**
     * 把用户选中的图片拷贝进私有目录。
     *
     * 会先压到合理尺寸（最长边 1440px）再存，避免一张 4000px 的原图
     * 被当成背景后每帧都要缩放，平白拖慢渲染。
     *
     * @return 成功返回 true
     */
    fun save(context: Context, source: Uri): Boolean {
        return runCatching {
            val input = context.contentResolver.openInputStream(source) ?: return false
            input.use { ins ->
                val bitmap = android.graphics.BitmapFactory.decodeStream(ins) ?: return false
                val scaled = scaleDown(bitmap, MAX_EDGE)
                file(context).outputStream().use { out ->
                    scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, out)
                }
                if (scaled !== bitmap) bitmap.recycle()
            }
            true
        }.getOrDefault(false)
    }

    /** 删除自定义背景图（切回内置方案时调用） */
    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    /** 等比缩放到最长边不超过 [maxEdge]；本来就够小则原样返回 */
    private fun scaleDown(
        src: android.graphics.Bitmap,
        maxEdge: Int,
    ): android.graphics.Bitmap {
        val longest = maxOf(src.width, src.height)
        if (longest <= maxEdge) return src
        val ratio = maxEdge.toFloat() / longest
        return android.graphics.Bitmap.createScaledBitmap(
            src,
            (src.width * ratio).toInt().coerceAtLeast(1),
            (src.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    private const val MAX_EDGE = 1440
}
