package org.totschnig.myexpenses.dialog

import android.app.Dialog
import android.graphics.drawable.Animatable2.AnimationCallback
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.totschnig.myexpenses.BuildConfig
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.databinding.MoreInfoBinding
import org.totschnig.myexpenses.util.TranslatorsHelper.buildTranslationCredits
import org.totschnig.myexpenses.util.Utils
import org.totschnig.myexpenses.util.distrib.DistributionHelper
import org.totschnig.myexpenses.util.ui.UiUtils
import org.totschnig.myexpenses.util.updater.AppUpdateManager

class MoreInfoDialogFragment : DialogViewBinding<MoreInfoBinding>() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val builder = initBuilder {
            MoreInfoBinding.inflate(it)
        }
        binding.aboutVersionCode.text = DistributionHelper.getVersionInfo(context)

        binding.btnCheckForUpdates.setOnClickListener {
            binding.btnCheckForUpdates.isEnabled = false
            binding.btnCheckForUpdates.setText(R.string.checking_for_updates)
            lifecycleScope.launch {
                val result = AppUpdateManager.checkUpdate(context)
                AppUpdateManager.recordCheckTime(context)
                binding.btnCheckForUpdates.isEnabled = true
                binding.btnCheckForUpdates.setText(R.string.check_for_updates)

                result.fold(
                    onSuccess = { releaseInfo ->
                        if (releaseInfo != null) {
                            AppUpdateDialogFragment.newInstance(releaseInfo)
                                .show(parentFragmentManager, AppUpdateDialogFragment.FRAGMENT_TAG)
                        } else {
                            Toast.makeText(context, R.string.update_check_latest, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onFailure = { error ->
                        val msg = getString(R.string.update_check_failed, error.localizedMessage ?: "Unknown error")
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
        binding.projectContainer.text = Utils.makeBulletList(
            context,
            Utils.getProjectDependencies(context)
                .map { project: Map<String, String> ->
                    val name = project["name"]
                    "${if (project.containsKey("extra_info")) "$name (${project["extra_info"]})" else name}, from ${project["url"]}, licenced under ${project["licence"]}"
                }.toList(), R.drawable.ic_menu_forward
        )
        binding.additionalContainer.text =
            Utils.makeBulletList(context, resources.getStringArray(R.array.additional_credits).toList(), R.drawable.ic_menu_forward)

        binding.translatorsContainer.text =
            Utils.makeBulletList(context, context.buildTranslationCredits(), R.drawable.ic_menu_forward)


        val nounIcons = listOf<CharSequence>(*resources.getStringArray(R.array.noun_icon_credits))
        //Warning TypedArray.close is API 31 -> We can not use use (tryWithResources)
        resources.obtainTypedArray(R.array.noun_icon_credits_keys).let {
            val drawablePadding = UiUtils.dp2Px(8f, resources)
            val verticalMargin = UiUtils.dp2Px(2f, resources)
            var i = 0
            while (i < it.length()) {
                val textView = TextView(context)
                textView.gravity = Gravity.CENTER_VERTICAL
                val layoutParams =
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                layoutParams.topMargin = verticalMargin
                layoutParams.bottomMargin = verticalMargin
                textView.layoutParams = layoutParams
                textView.compoundDrawablePadding = drawablePadding
                textView.text = nounIcons[i]
                textView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    it.getResourceId(i, 0),
                    0,
                    0,
                    0
                )
                binding.nounIconsContainer.addView(textView)
                i++
            }
            it.recycle()
        }

        //noinspection SetTextI18n
        binding.copyRight.text = "© 2011 - ${BuildConfig.BUILD_DATE.year} Michael Totschnig"
        return builder.setTitle(R.string.pref_more_info_dialog_title)
            .setPositiveButton(android.R.string.ok, null)
            .create()
    }

    private fun ImageView.startAnimation() {
        (background as? AnimatedVectorDrawable)?.apply {
            start()
            registerAnimationCallback( object: AnimationCallback() {
                override fun onAnimationEnd(drawable: Drawable) {
                    start()
                }
            })
        }
    }

    override fun onStart() {
        super.onStart()
        binding.useanimationsHeart.startAnimation()
        binding.useanimationsNotificationv4.startAnimation()
    }


}