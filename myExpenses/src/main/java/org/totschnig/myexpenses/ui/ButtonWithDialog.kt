package org.totschnig.myexpenses.ui

import android.content.Context
import android.content.ContextWrapper
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import android.os.Bundle
import androidx.core.os.BundleCompat
import com.google.android.material.button.MaterialButton
import org.totschnig.myexpenses.util.crashreporting.CrashHandler
import org.totschnig.myexpenses.util.ui.getActivity

abstract class ButtonWithDialog<T: DialogFragment> constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : MaterialButton(context, attrs, defStyleAttr) {

    abstract val fragmentTag: String

    var dialogShown = false

    private fun showDialog() {
        val picker = buildDialog()
        attachListener(picker)
        picker.show((context.getActivity() as FragmentActivity).supportFragmentManager, fragmentTag)
        dialogShown = true
    }

    abstract fun buildDialog(): T

    override fun onSaveInstanceState(): Parcelable {
        val bundle = Bundle()
        bundle.putParcelable(KEY_SUPER_STATE, super.onSaveInstanceState())
        bundle.putBoolean(KEY_DIALOG_SHOWN, dialogShown)
        onSaveCustomState(bundle)
        return bundle
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is Bundle) {
            super.onRestoreInstanceState(BundleCompat.getParcelable(state, KEY_SUPER_STATE, Parcelable::class.java))
            dialogShown = state.getBoolean(KEY_DIALOG_SHOWN, false)
            onRestoreCustomState(state)
        } else {
            super.onRestoreInstanceState(state)
        }
        update()
        if (dialogShown) {
            reAttachListener((context.getActivity() as FragmentActivity).supportFragmentManager)
        }
    }

    protected open fun onSaveCustomState(bundle: Bundle) {}
    protected open fun onRestoreCustomState(bundle: Bundle) {}

    abstract fun update()

    abstract fun attachListener(dialogFragment: T)

    @Suppress("UNCHECKED_CAST")
    private fun reAttachListener(supportFragmentManager: FragmentManager) {
        (supportFragmentManager.findFragmentByTag(fragmentTag) as? T)?.apply {
            attachListener(this)
        } ?: kotlin.run {
            CrashHandler.report(Exception("reAttachListener failed"))
        }
    }

    protected val host: Host
        get() {
            var context = context
            while (context is ContextWrapper) {
                if (context is Host) {
                    return context
                }
                context = context.baseContext
            }
            throw IllegalStateException("Host context does not implement interface")
        }

    interface Host {
        fun onValueSet(view: View)
    }

    open fun onClick() {
        showDialog()
    }

    final override fun setOnClickListener(l: OnClickListener?) {
        super.setOnClickListener(l)
    }

    init {
        setOnClickListener { onClick() }
    }

    companion object {
        private const val KEY_SUPER_STATE = "super_state"
        private const val KEY_DIALOG_SHOWN = "dialog_shown"
    }
}