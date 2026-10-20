package eltos.simpledialogfragment.form

import android.os.Parcel
import android.os.Parcelable
import java.math.BigDecimal

class AmountInput : FormElement<AmountInput, AmountInputViewHolder> {
    var fractionDigits: Int = 0
    var amount: BigDecimal? = null
    var max: BigDecimal? = null
    var maxExceededError: String? = null
    var min: BigDecimal? = null
    var underMinError: String? = null
    var withTypeSwitch: Boolean? = null

    protected constructor(resultKey: String) : super(resultKey)

    fun fractionDigits(i: Int): AmountInput {
        fractionDigits = i
        return this
    }

    fun amount(amount: BigDecimal): AmountInput {
        this.amount = amount
        return this
    }

    fun max(amount: BigDecimal, maxExceededError: String): AmountInput {
        this.max = amount
        this.maxExceededError = maxExceededError
        return this
    }

    fun min(amount: BigDecimal, underMinError: String): AmountInput {
        this.min = amount
        this.underMinError = underMinError
        return this
    }

    fun withTypeSwitch(withTypeSwitch: Boolean?): AmountInput {
        this.withTypeSwitch = withTypeSwitch
        return this
    }

    override fun buildViewHolder(): AmountInputViewHolder = AmountInputViewHolder(this)

    protected constructor(inParcel: Parcel) : super(inParcel) {
        fractionDigits = inParcel.readInt()
        amount = inParcel.readString()?.let { BigDecimal(it) }
        max = inParcel.readString()?.let { BigDecimal(it) }
        maxExceededError = inParcel.readString()
        min = inParcel.readString()?.let { BigDecimal(it) }
        underMinError = inParcel.readString()
        withTypeSwitch = when (inParcel.readInt()) {
            1 -> true
            -1 -> false
            else -> null
        }
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        super.writeToParcel(dest, flags)
        dest.writeInt(fractionDigits)
        dest.writeString(amount?.toString())
        dest.writeString(max?.toString())
        dest.writeString(maxExceededError)
        dest.writeString(min?.toString())
        dest.writeString(underMinError)
        dest.writeInt(withTypeSwitch?.let { if (it) 1 else -1 } ?: 0)
    }

    override fun describeContents(): Int = 0

    companion object {
        fun plain(resultKey: String): AmountInput = AmountInput(resultKey)

        @JvmField
        val CREATOR: Parcelable.Creator<AmountInput> = object : Parcelable.Creator<AmountInput> {
            override fun createFromParcel(inParcel: Parcel): AmountInput = AmountInput(inParcel)
            override fun newArray(size: Int): Array<AmountInput?> = arrayOfNulls(size)
        }
    }
}
