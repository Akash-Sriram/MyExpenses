package org.totschnig.myexpenses.adapter

import android.content.Context
import android.database.DataSetObserver
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListAdapter
import android.widget.SpinnerAdapter
import android.widget.TextView
import org.totschnig.myexpenses.R

/**
 * Decorator Adapter to allow a Spinner to show a 'Nothing Selected...' initially
 * displayed instead of the first choice in the Adapter.
 * [How to make an Android Spinner with initial text "Select One"?](https://stackoverflow.com/a/12221309/1199911)
 */
open class NothingSelectedSpinnerAdapter(
    protected val adapter: SpinnerAdapter,
    protected val nothingSelectedLayout: Int,
    protected val nothingSelectedDropdownLayout: Int,
    protected val context: Context
) : SpinnerAdapter, ListAdapter {

    /**
     * Use this constructor to have NO 'Select One...' item, instead use
     * the standard prompt or nothing at all.
     * @param spinnerAdapter wrapped Adapter.
     * @param nothingSelectedLayout layout for nothing selected, perhaps you want text grayed out...
     */
    constructor(spinnerAdapter: SpinnerAdapter, nothingSelectedLayout: Int, context: Context) :
            this(spinnerAdapter, nothingSelectedLayout, -1, context)

    protected val layoutInflater: LayoutInflater = LayoutInflater.from(context)

    companion object {
        protected const val EXTRA = 1
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        if (position == 0) return getNothingSelectedView(parent)
        return adapter.getView(position - EXTRA, null, parent)
    }

    /**
     * View to show in Spinner with Nothing Selected.
     * Override this to do something dynamic...
     */
    protected open fun getNothingSelectedView(parent: ViewGroup): View {
        val view = layoutInflater.inflate(nothingSelectedLayout, parent, false)
        (view as TextView).setText(R.string.select)
        return view
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        if (position == 0) {
            return if (nothingSelectedDropdownLayout == -1) View(context)
            else getNothingSelectedDropdownView(parent)
        }
        return adapter.getDropDownView(position - EXTRA, null, parent)
    }

    /**
     * Override this to do something dynamic...
     */
    protected open fun getNothingSelectedDropdownView(parent: ViewGroup): View {
        return layoutInflater.inflate(nothingSelectedDropdownLayout, parent, false)
    }

    override fun getCount(): Int {
        val count = adapter.count
        return if (count == 0) 0 else count + EXTRA
    }

    override fun getItem(position: Int): Any? {
        return if (position == 0) null else adapter.getItem(position - EXTRA)
    }

    override fun getItemViewType(position: Int): Int = 0

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long {
        return if (position >= EXTRA) adapter.getItemId(position - EXTRA) else (position - EXTRA).toLong()
    }

    override fun hasStableIds(): Boolean = adapter.hasStableIds()

    override fun isEmpty(): Boolean = adapter.isEmpty

    override fun registerDataSetObserver(observer: DataSetObserver) {
        adapter.registerDataSetObserver(observer)
    }

    override fun unregisterDataSetObserver(observer: DataSetObserver) {
        adapter.unregisterDataSetObserver(observer)
    }

    override fun areAllItemsEnabled(): Boolean = false

    override fun isEnabled(position: Int): Boolean = position != 0
}
