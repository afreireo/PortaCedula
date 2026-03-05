package com.example.portacedula

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object PdfGenerator {

    private const val A4_WIDTH = 595.27f
    private const val A4_HEIGHT = 841.89f
    
    private const val MARGIN_TOP = 56.69f
    private const val MARGIN_LEFT = 39.68f
    
    private const val CARD_WIDTH = 243.78f
    private const val CARD_HEIGHT = 153.07f
    private const val GAP = 28.35f
    
    private const val CORNER_RADIUS = 10f

    fun generateAndShare(context: Context, card: IdCard) {
        val frontUriStr = card.frontUri
        val backUriStr = card.backUri

        if (frontUriStr == null || backUriStr == null) {
            Toast.makeText(context, "Faltan imágenes para generar el PDF", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(context, "Generando PDF...", Toast.LENGTH_SHORT).show()

        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH.toInt(), A4_HEIGHT.toInt(), 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        try {
            val frontBitmap = loadBitmap(context, Uri.parse(frontUriStr))
            val backBitmap = loadBitmap(context, Uri.parse(backUriStr))

            if (frontBitmap == null || backBitmap == null) {
                throw Exception("No se pudieron cargar las imágenes")
            }

            // Dibujar Cara Frontal
            val frontRect = RectF(MARGIN_LEFT, MARGIN_TOP, MARGIN_LEFT + CARD_WIDTH, MARGIN_TOP + CARD_HEIGHT)
            drawRoundedBitmap(canvas, frontBitmap, frontRect, CORNER_RADIUS)

            // Dibujar Cara Reversa
            val backLeft = MARGIN_LEFT + CARD_WIDTH + GAP
            val backRect = RectF(backLeft, MARGIN_TOP, backLeft + CARD_WIDTH, MARGIN_TOP + CARD_HEIGHT)
            drawRoundedBitmap(canvas, backBitmap, backRect, CORNER_RADIUS)

            frontBitmap.recycle()
            backBitmap.recycle()

        } catch (e: Exception) {
            Log.e("PdfGenerator", "Error: ${e.message}")
            Toast.makeText(context, "Error al procesar imágenes", Toast.LENGTH_SHORT).show()
            document.close()
            return
        }

        document.finishPage(page)

        val imagesDir = File(context.cacheDir, "images")
        if (!imagesDir.exists()) imagesDir.mkdirs()

        val file = File(imagesDir, "Cedula.pdf")
        try {
            FileOutputStream(file).use { document.writeTo(it) }
            document.close()
            shareFile(context, file)
        } catch (e: Exception) {
            Log.e("PdfGenerator", "Error saving: ${e.message}")
            Toast.makeText(context, "Error al guardar PDF", Toast.LENGTH_SHORT).show()
        }
    }

    private fun drawRoundedBitmap(canvas: Canvas, bitmap: Bitmap, rect: RectF, radius: Float) {
        val path = Path()
        path.addRoundRect(rect, radius, radius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(path)
        canvas.drawBitmap(bitmap, null, rect, null)
        canvas.restore()
    }

    private fun loadBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { 
                BitmapFactory.decodeStream(it)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun shareFile(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Compartir Cédula")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Error al compartir", Toast.LENGTH_SHORT).show()
        }
    }
}
