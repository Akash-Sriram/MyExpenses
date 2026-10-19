package org.totschnig.myexpenses.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.ui.animation.ExpandAnimation

class ExpansionPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : LinearLayout(context, attrs, defStyleAttr, defStyleRes) {

    private lateinit var headerIndicator: ExpansionHandle
    private lateinit var expansionContent: View
    private var expansionTrigger: View? = null
    var listener: Listener? = null

    override fun onFinishInflate() {
        super.onFinishInflate()
        expansionTrigger = findViewById(R.id.expansionTrigger)
        expansionContent = findViewById(R.id.expansionContent)
        headerIndicator = findViewById(R.id.headerIndicator)
        updateIndicator()
        val trigger = expansionTrigger ?: headerIndicator
        trigger.setOnClickListener {
            val visible = expansionContent.visibility == VISIBLE
            headerIndicator.rotate(visible, listener)
            // if android:animateLayoutChanges is true we go with the default animation,
            // which works well, unless we are in a list view
            if (hasNoDefaultTransition()) {
                val expandAni = ExpandAnimation(expansionContent, 250)
                expansionContent.startAnimation(expandAni)
            } else {
                expansionContent.visibility = if (visible) GONE else VISIBLE
            }
        }
    }

    private fun hasNoDefaultTransition(): Boolean = layoutTransition == null

    private fun updateIndicator() {
        headerIndicator.setExpanded(expansionContent.visibility == VISIBLE)
    }
}
