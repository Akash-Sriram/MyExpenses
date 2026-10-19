package org.totschnig.ocr

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import android.graphics.ImageDecoder
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.snackbar.Snackbar
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.dialog.DialogViewBinding
import org.totschnig.myexpenses.feature.OcrHost
import org.totschnig.ocr.databinding.ScanPreviewBinding

class ScanPreviewFragment : DialogViewBinding<ScanPreviewBinding>() {
    private lateinit var viewModel: ScanPreviewViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DaggerOcrComponent.builder()
            .appComponent((requireActivity().application as MyApplication).appComponent).build()
            .inject(this)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        viewModel = ViewModelProvider(this)[ScanPreviewViewModel::class.java]
        viewModel.getResult().observe(this) { result ->
            (activity as? OcrHost)?.processOcrResult(result, scanUri)
            dismiss()
        }
        return initBuilder {
            ScanPreviewBinding.inflate(it)
        }
            .setCancelable(false)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.button_scan, null)
            .create()
            .apply {
                setOnShowListener {
                    loadImage()
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        getButton(AlertDialog.BUTTON_POSITIVE)?.let {
                            it.isEnabled = false
                        }
                        showSnackBar(
                            getString(
                                R.string.ocr_recognition_info,
                                viewModel.getOcrInfo(requireContext())
                            ), Snackbar.LENGTH_INDEFINITE, null
                        )
                        activity?.let { viewModel.runTextRecognition(scanUri, it) }
                    }
                }
            }
    }

    private fun loadImage() {
        val uri = scanUri
        try {
            val source = ImageDecoder.createSource(requireContext().contentResolver, uri)
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val targetW = binding.imageView.width
                val targetH = binding.imageView.height
                if (targetW > 0 && targetH > 0 && (info.size.width > targetW || info.size.height > targetH)) {
                    decoder.setTargetSize(targetW, targetH)
                }
            }
            binding.imageView.setImageBitmap(bitmap)
        } catch (_: Exception) {
            binding.imageView.setImageURI(uri)
        }
    }

    fun handleData(intent: Intent?) {
        viewModel.handleData(intent)
    }

    private val scanUri: Uri
        get() = requireArguments().getParcelable(KEY_FILE)!!

    companion object {
        const val KEY_FILE = "file"
        fun with(uri: Uri) = ScanPreviewFragment().apply {
            isCancelable = false
            arguments = Bundle().apply {
                putParcelable(KEY_FILE, uri)
            }
        }
    }
}