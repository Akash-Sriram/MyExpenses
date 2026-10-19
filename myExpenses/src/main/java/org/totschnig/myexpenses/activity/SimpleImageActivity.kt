package org.totschnig.myexpenses.activity

import android.app.Activity
import android.graphics.ImageDecoder
import android.os.Bundle
import android.view.WindowManager
import android.widget.ImageView
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.util.crashreporting.CrashHandler

class SimpleImageActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        setContentView(R.layout.simple_image)
        val imageView = findViewById<ImageView>(R.id.imageView)
        val uri = intent.data ?: return
        try {
            val source = ImageDecoder.createSource(contentResolver, uri)
            val bitmap = ImageDecoder.decodeBitmap(source)
            imageView.setImageBitmap(bitmap)
        } catch (e: Exception) {
            CrashHandler.report(e)
            imageView.setImageURI(uri)
        }
    }
}
