package org.totschnig.myexpenses.activity

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import android.widget.ImageView
import com.squareup.picasso.Picasso
import org.totschnig.myexpenses.R

class SimpleImageActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        setContentView(R.layout.simple_image)
        Picasso.get().load(intent.data).fit().into(findViewById<ImageView>(R.id.imageView))
    }
}
