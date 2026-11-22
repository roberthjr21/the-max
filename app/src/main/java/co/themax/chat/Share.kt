package co.themax.chat

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File

fun shareCard(context: Context, tile: Tile) {
    val frame = Bitmap.createBitmap(900,1200,Bitmap.Config.ARGB_8888)
    val canvas = Canvas(frame)
    canvas.drawColor(0xfffafaf7.toInt())
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = tile.tint.toInt()
    canvas.drawRoundRect(40f,220f,860f,1030f,54f,54f,paint)
    fun line(text: String,y: Float,size: Float,lines: Int,color: Int = 0xff102c34.toInt()): Float {
        val style = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; this.color = color; typeface = Typeface.create("sans-serif",Typeface.BOLD)
        }
        val block = StaticLayout.Builder.obtain(text,0,text.length,style,730).setMaxLines(lines)
            .setEllipsize(android.text.TextUtils.TruncateAt.END).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
        canvas.save(); canvas.translate(84f,y); block.draw(canvas); canvas.restore()
        return y + block.height
    }
    line("The Max",100f,64f,1,0xff16894d.toInt())
    var y = line(tile.title,300f,57f,2) + 54
    for (pair in listOfNotNull(tile.left,tile.right)) {
        y = line(pair.first,y,38f,1)
        y = line(pair.second,y,76f,1) + 20
    }
    if (tile.detail.isNotEmpty()) y = line(tile.detail,y,50f,5) + 24
    if (tile.note.isNotEmpty()) line(tile.note,y,30f,3)
    val dir = File(context.cacheDir,"cards").apply { mkdirs() }
    dir.listFiles()?.filter { System.currentTimeMillis()-it.lastModified() > 86400000 }?.forEach { it.delete() }
    val file = File.createTempFile("the-max-", ".png",dir)
    file.outputStream().use { frame.compress(Bitmap.CompressFormat.PNG,100,it) }
    frame.recycle()
    val uri = FileProvider.getUriForFile(context,"co.themax.chat.files",file)
    val intent = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM,uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent,"Compartir tarjeta"))
}
