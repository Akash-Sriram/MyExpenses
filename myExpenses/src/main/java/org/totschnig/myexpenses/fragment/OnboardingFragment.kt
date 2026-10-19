package org.totschnig.myexpenses.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.annotation.MenuRes
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.activity.OnboardingActivity
import org.totschnig.myexpenses.databinding.OnboardingWizardBinding
import org.totschnig.myexpenses.preference.PrefHandler
import javax.inject.Inject

abstract class OnboardingFragment : Fragment() {
    private var _binding: OnboardingWizardBinding? = null
    private val binding get() = _binding!!
    lateinit var nextButton: View
    lateinit var toolbar: Toolbar

    @Inject
    lateinit var prefHandler: PrefHandler

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = OnboardingWizardBinding.inflate(inflater, container, false)
        binding.headerText.text = title
        binding.onboardingContent.layoutResource = layoutResId
        bindView(binding.onboardingContent.inflate())
        configureNavigation()
        return binding.root
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        configureView(savedInstanceState)
    }

    protected abstract val title: CharSequence

    private fun configureNavigation() {
        toolbar = binding.navigationBar.onboardingMenu
        if (menuResId != 0) {
            toolbar.inflateMenu(menuResId)
            setupMenu()
        }
        nextButton = ViewCompat.requireViewById(binding.navigationBar.root, navigationButtonId)
        nextButton.setOnClickListener { onNextButtonClicked() }
    }

    protected open val navigationButtonId: Int
        @IdRes get() = R.id.suw_navbar_next

    protected abstract fun configureView(savedInstanceState: Bundle?)

    @get:LayoutRes
    protected abstract val layoutResId: Int
    abstract fun bindView(view: View)

    protected open val menuResId: Int
        @MenuRes get() = 0

    protected open fun onNextButtonClicked() {
        hostActivity.navigateNext()
    }

    open fun setupMenu() {}

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    val hostActivity
        get() = requireActivity() as OnboardingActivity
}
