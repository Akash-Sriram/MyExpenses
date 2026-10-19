package org.totschnig.myexpenses.util.cursor

import android.database.Cursor
import android.database.CursorWrapper

abstract class CursorWrapperHelper(cursor: Cursor?) : CursorWrapper(cursor) {
    @JvmField
    protected var mPos = -1

    override fun move(offset: Int): Boolean {
        return moveToPosition(mPos + offset)
    }

    override fun moveToFirst(): Boolean {
        return moveToPosition(0)
    }

    override fun moveToLast(): Boolean {
        return moveToPosition(count - 1)
    }

    override fun moveToNext(): Boolean {
        return moveToPosition(mPos + 1)
    }

    override fun moveToPrevious(): Boolean {
        return moveToPosition(mPos - 1)
    }

    override fun isFirst(): Boolean {
        return mPos == 0 && count != 0
    }

    override fun isLast(): Boolean {
        val cnt = count
        return mPos == cnt - 1 && cnt != 0
    }

    override fun isBeforeFirst(): Boolean {
        if (count == 0) {
            return true
        }
        return mPos == -1
    }

    override fun isAfterLast(): Boolean {
        if (count == 0) {
            return true
        }
        return mPos == count
    }

    override fun getPosition(): Int {
        return mPos
    }

    override fun moveToPosition(pos: Int): Boolean {
        val count = count
        if (pos >= count) {
            mPos = count
            return false
        }
        if (pos < 0) {
            mPos = -1
            return false
        }

        val moved = super.moveToPosition(getMappedPosition(pos))
        if (moved) {
            mPos = pos
        }
        return moved
    }

    protected abstract fun getMappedPosition(pos: Int): Int
}
