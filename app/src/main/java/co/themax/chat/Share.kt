package co.themax.chat

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val inkColor = 0xff102c34.toInt()
private val selfColor = 0xff007d78.toInt()
private val otherColor = 0xffa52262.toInt()
private data class Block(val layout: StaticLayout, val x: Int, val y: Int)

private class CardLayout {
    val blocks = mutableListOf<Block>()
    var y = 104
    fun cell(text: String, x: Int, width: Int, size: Float, color: Int = inkColor,
             bold: Boolean = false, right: Boolean = false): Int {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(if (right) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false).build()
        blocks += Block(layout, x, y)
        return layout.height
    }
    fun full(text: String, size: Float, bold: Boolean = false) {
        val height = cell(text, 72, 756, size, bold = bold)
        y += height + 22
    }
}

private fun render(tile: Tile): Bitmap {
    val page = CardLayout()
    page.full(tile.title, 44f, true)
    if (tile.left != null && tile.right != null) {
        val names = maxOf(page.cell(tile.left.first,72,360,32f,selfColor,true),
            page.cell(tile.right.first,468,360,32f,otherColor,true,true))
        page.y += names + 10
        val values = maxOf(page.cell(tile.left.second,72,360,58f,bold = true),
            page.cell(tile.right.second,468,360,58f,bold = true,right = true))
        page.y += values + 20
    }
    if (tile.detail.isNotBlank()) page.full(tile.detail,36f,true)
    val tableTop = page.y
    tile.comparison?.let { table ->
        page.y += 16
        val header = maxOf(page.cell("Tú",390,148,28f,selfColor,true,true),
            page.cell(table.contact,550,250,28f,otherColor,true,true))
        page.y += header + 14
        for (metric in table.rows) {
            val height = maxOf(page.cell(metric.label,86,292,27f),
                page.cell(metric.self.toString(),390,148,27f,right = true),
                page.cell(metric.other.toString(),550,250,27f,right = true))
            page.y += height + 16
        }
    }
    val tableBottom = page.y
    if (tile.note.isNotBlank()) { page.y += 16; page.full(tile.note,26f) }
    val bottom = page.y + 16
    page.y = bottom + 20
    val footer = page.cell("Hecho con The Max",72,756,23f,0xff16894d.toInt())
    val bitmap = Bitmap.createBitmap(900,page.y + footer + 36,Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(0xfffafaf7.toInt())
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tile.tint.toInt() }
    canvas.drawRoundRect(32f,56f,868f,bottom.toFloat(),48f,48f,paint)
    if (tableBottom > tableTop) {
        paint.color = 0x99ffffff.toInt()
        canvas.drawRoundRect(64f,tableTop.toFloat(),836f,tableBottom.toFloat(),20f,20f,paint)
    }
    for (block in page.blocks) {
        canvas.save()
        canvas.translate(block.x.toFloat(),block.y.toFloat())
        block.layout.draw(canvas)
        canvas.restore()
    }
    return bitmap
}

suspend fun shareCard(context: Context, tile: Tile) {
    val frame = withContext(Dispatchers.Default) { render(tile) }
    val file = try { withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir,"cards").apply { mkdirs() }
        dir.listFiles()?.filter { System.currentTimeMillis()-it.lastModified() > 86400000 }?.forEach { it.delete() }
        val file = File.createTempFile("the-max-", ".png",dir)
        file.outputStream().use { frame.compress(Bitmap.CompressFormat.PNG,100,it) }
        file
    
    } } finally { frame.recycle() }
    withContext(Dispatchers.Main.immediate) {
        val uri = FileProvider.getUriForFile(context,"co.themax.chat.files",file)
        val intent = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM,uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent,"Compartir tarjeta"))
    }
}
