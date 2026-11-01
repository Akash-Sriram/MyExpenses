package org.totschnig.myexpenses.dialog

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.databinding.DialogAppUpdateBinding
import org.totschnig.myexpenses.util.updater.AppUpdateManager
import org.totschnig.myexpenses.util.updater.ReleaseInfo
import java.io.File

class AppUpdateDialogFragment : BaseDialogFragment() {

    private var _binding: DialogAppUpdateBinding? = null
    private val binding get() = _binding!!

    private var downloadedApkFile: File? = null
    private var isDownloading = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = requireArguments()
        val tagName = args.getString(KEY_TAG_NAME) ?: ""
        val releaseName = args.getString(KEY_NAME) ?: tagName
        val body = args.getString(KEY_BODY) ?: ""
        val downloadUrl = args.getString(KEY_DOWNLOAD_URL) ?: ""
        val apkName = args.getString(KEY_APK_NAME) ?: "update.apk"
        val htmlUrl = args.getString(KEY_HTML_URL) ?: ""
        val size = args.getLong(KEY_SIZE, 0L)

        val releaseInfo = ReleaseInfo(
            tagName = tagName,
            name = releaseName,
            body = body,
            downloadUrl = downloadUrl,
            apkName = apkName,
            htmlUrl = htmlUrl,
            size = size
        )

        _binding = DialogAppUpdateBinding.inflate(LayoutInflater.from(requireContext()))

        binding.updateVersionTitle.text = if (releaseName.isNotBlank() && releaseName != tagName) {
            "$releaseName ($tagName)"
        } else {
            "Version $tagName"
        }

        binding.updateChangelogText.text = if (body.isNotBlank()) body else "No release notes provided."

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.in_app_update_available_title)
            .setView(binding.root)
            .setPositiveButton(R.string.in_app_update_download_install, null)
            .setNeutralButton(R.string.in_app_update_view_on_github) { _, _ ->
                if (htmlUrl.isNotBlank()) {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(htmlUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(browserIntent)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            val positiveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveBtn.setOnClickListener {
                handlePositiveAction(positiveBtn, releaseInfo)
            }
        }

        return dialog
    }

    private fun handlePositiveAction(button: Button, releaseInfo: ReleaseInfo) {
        val ctx = context ?: return

        downloadedApkFile?.let { apkFile ->
            if (apkFile.exists()) {
                if (!AppUpdateManager.canRequestPackageInstalls(ctx)) {
                    Toast.makeText(ctx, R.string.in_app_update_permission_prompt, Toast.LENGTH_LONG).show()
                    startActivity(AppUpdateManager.createManageUnknownAppSourcesIntent(ctx))
                } else {
                    AppUpdateManager.installApk(ctx, apkFile)
                    dismissAllowingStateLoss()
                }
                return
            }
        }

        if (isDownloading) return

        isDownloading = true
        button.isEnabled = false
        binding.updateProgressContainer.visibility = View.VISIBLE
        binding.updateProgressBar.progress = 0
        binding.updateStatusText.text = getString(R.string.in_app_update_downloading, "0%")

        lifecycleScope.launch {
            val result = AppUpdateManager.downloadApk(ctx, releaseInfo) { progress ->
                binding.updateProgressBar.progress = progress
                binding.updateStatusText.text = getString(R.string.in_app_update_downloading, "$progress%")
            }

            isDownloading = false
            button.isEnabled = true

            result.fold(
                onSuccess = { file ->
                    downloadedApkFile = file
                    binding.updateStatusText.text = getString(R.string.in_app_update_download_complete)
                    button.setText(R.string.in_app_update_install)

                    if (!AppUpdateManager.canRequestPackageInstalls(ctx)) {
                        Toast.makeText(ctx, R.string.in_app_update_permission_prompt, Toast.LENGTH_LONG).show()
                        startActivity(AppUpdateManager.createManageUnknownAppSourcesIntent(ctx))
                    } else {
                        AppUpdateManager.installApk(ctx, file)
                        dismissAllowingStateLoss()
                    }
                },
                onFailure = { error ->
                    val errorMsg = error.localizedMessage ?: "Network error"
                    binding.updateStatusText.text = getString(R.string.in_app_update_download_failed, errorMsg)
                    Toast.makeText(ctx, getString(R.string.in_app_update_download_failed, errorMsg), Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val FRAGMENT_TAG = "APP_UPDATE_DIALOG"

        private const val KEY_TAG_NAME = "tag_name"
        private const val KEY_NAME = "name"
        private const val KEY_BODY = "body"
        private const val KEY_DOWNLOAD_URL = "download_url"
        private const val KEY_APK_NAME = "apk_name"
        private const val KEY_HTML_URL = "html_url"
        private const val KEY_SIZE = "size"

        fun newInstance(releaseInfo: ReleaseInfo): AppUpdateDialogFragment {
            return AppUpdateDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(KEY_TAG_NAME, releaseInfo.tagName)
                    putString(KEY_NAME, releaseInfo.name)
                    putString(KEY_BODY, releaseInfo.body)
                    putString(KEY_DOWNLOAD_URL, releaseInfo.downloadUrl)
                    putString(KEY_APK_NAME, releaseInfo.apkName)
                    putString(KEY_HTML_URL, releaseInfo.htmlUrl)
                    putLong(KEY_SIZE, releaseInfo.size)
                }
            }
        }
    }
}
