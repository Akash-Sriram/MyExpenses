/*   This file is part of My Expenses.
 *   My Expenses is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   My Expenses is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with My Expenses.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.totschnig.myexpenses.activity

import android.content.res.Configuration
import android.util.SparseArray
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.Locale

internal object ConfigurationHelper {
    private const val SUFFIX_MASK = "_MASK"
    private const val FIELD_SCREENLAYOUT = "screenLayout"
    private const val FIELD_UIMODE = "uiMode"
    private const val FIELD_MNC = "mnc"
    private const val FIELD_MCC = "mcc"
    private const val PREFIX_UI_MODE = "UI_MODE_"
    private const val PREFIX_TOUCHSCREEN = "TOUCHSCREEN_"
    private const val PREFIX_SCREENLAYOUT = "SCREENLAYOUT_"
    private const val PREFIX_ORIENTATION = "ORIENTATION_"
    private const val PREFIX_NAVIGATIONHIDDEN = "NAVIGATIONHIDDEN_"
    private const val PREFIX_NAVIGATION = "NAVIGATION_"
    private const val PREFIX_KEYBOARDHIDDEN = "KEYBOARDHIDDEN_"
    private const val PREFIX_KEYBOARD = "KEYBOARD_"
    private const val PREFIX_HARDKEYBOARDHIDDEN = "HARDKEYBOARDHIDDEN_"

    // #### FROM ACRA ####
    /**
     * Creates a [JSONObject] listing all values human-readable from the provided Configuration instance.
     */
    fun configToJson(conf: Configuration): JSONObject {
        val result = JSONObject()
        val valueArrays = getValueArrays()
        for (f in conf.javaClass.fields) {
            try {
                if (!Modifier.isStatic(f.modifiers)) {
                    val fieldName = f.name
                    try {
                        if (f.type == Int::class.javaPrimitiveType) {
                            result.put(fieldName, getFieldValueName(valueArrays, conf, f))
                        } else if (f.get(conf) != null) {
                            result.put(fieldName, f.get(conf))
                        }
                    } catch (e: JSONException) {
                        Timber.w(e, "Could not collect configuration field %s", fieldName)
                    }
                }
            } catch (e: IllegalAccessException) {
                Timber.w(e, "Error while inspecting device configuration")
            } catch (e: IllegalArgumentException) {
                Timber.w(e, "Error while inspecting device configuration")
            }
        }
        return result
    }

    private fun getValueArrays(): Map<String, SparseArray<String>> {
        val valueArrays = HashMap<String, SparseArray<String>>()
        val hardKeyboardHiddenValues = SparseArray<String>()
        val keyboardValues = SparseArray<String>()
        val keyboardHiddenValues = SparseArray<String>()
        val navigationValues = SparseArray<String>()
        val navigationHiddenValues = SparseArray<String>()
        val orientationValues = SparseArray<String>()
        val screenLayoutValues = SparseArray<String>()
        val touchScreenValues = SparseArray<String>()
        val uiModeValues = SparseArray<String>()

        for (f in Configuration::class.java.fields) {
            if (Modifier.isStatic(f.modifiers) && Modifier.isFinal(f.modifiers)) {
                val fieldName = f.name
                try {
                    when {
                        fieldName.startsWith(PREFIX_HARDKEYBOARDHIDDEN) ->
                            hardKeyboardHiddenValues.put(f.getInt(null), fieldName)
                        fieldName.startsWith(PREFIX_KEYBOARD) ->
                            keyboardValues.put(f.getInt(null), fieldName)
                        fieldName.startsWith(PREFIX_KEYBOARDHIDDEN) ->
                            keyboardHiddenValues.put(f.getInt(null), fieldName)
                        fieldName.startsWith(PREFIX_NAVIGATION) ->
                            navigationValues.put(f.getInt(null), fieldName)
                        fieldName.startsWith(PREFIX_NAVIGATIONHIDDEN) ->
                            navigationHiddenValues.put(f.getInt(null), fieldName)
                        fieldName.startsWith(PREFIX_ORIENTATION) ->
                            orientationValues.put(f.getInt(null), fieldName)
                        fieldName.startsWith(PREFIX_SCREENLAYOUT) ->
                            screenLayoutValues.put(f.getInt(null), fieldName)
                        fieldName.startsWith(PREFIX_TOUCHSCREEN) ->
                            touchScreenValues.put(f.getInt(null), fieldName)
                        fieldName.startsWith(PREFIX_UI_MODE) ->
                            uiModeValues.put(f.getInt(null), fieldName)
                    }
                } catch (e: IllegalAccessException) {
                    Timber.w(e, "Error while inspecting device configuration")
                } catch (e: IllegalArgumentException) {
                    Timber.w(e, "Error while inspecting device configuration")
                }
            }
        }

        valueArrays[PREFIX_HARDKEYBOARDHIDDEN] = hardKeyboardHiddenValues
        valueArrays[PREFIX_KEYBOARD] = keyboardValues
        valueArrays[PREFIX_KEYBOARDHIDDEN] = keyboardHiddenValues
        valueArrays[PREFIX_NAVIGATION] = navigationValues
        valueArrays[PREFIX_NAVIGATIONHIDDEN] = navigationHiddenValues
        valueArrays[PREFIX_ORIENTATION] = orientationValues
        valueArrays[PREFIX_SCREENLAYOUT] = screenLayoutValues
        valueArrays[PREFIX_TOUCHSCREEN] = touchScreenValues
        valueArrays[PREFIX_UI_MODE] = uiModeValues
        return valueArrays
    }

    @Throws(IllegalAccessException::class)
    private fun getFieldValueName(
        valueArrays: Map<String, SparseArray<String>>,
        conf: Configuration,
        f: Field
    ): Any {
        val fieldName = f.name
        return when (fieldName) {
            FIELD_MCC, FIELD_MNC -> f.getInt(conf)
            FIELD_UIMODE -> activeFlags(valueArrays[PREFIX_UI_MODE]!!, f.getInt(conf))
            FIELD_SCREENLAYOUT -> activeFlags(valueArrays[PREFIX_SCREENLAYOUT]!!, f.getInt(conf))
            else -> {
                val values = valueArrays[fieldName.uppercase(Locale.ROOT) + '_']
                    ?: return f.getInt(conf)
                values.get(f.getInt(conf)) ?: f.getInt(conf)
            }
        }
    }

    private fun activeFlags(valueNames: SparseArray<String>, bitfield: Int): String {
        val result = StringBuilder()
        for (i in 0 until valueNames.size()) {
            val maskValue = valueNames.keyAt(i)
            if (valueNames.get(maskValue).endsWith(SUFFIX_MASK)) {
                val value = bitfield and maskValue
                if (value > 0) {
                    if (result.isNotEmpty()) result.append('+')
                    result.append(valueNames.get(value))
                }
            }
        }
        return result.toString()
    }
}
