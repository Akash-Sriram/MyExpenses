package org.totschnig.myexpenses.testutils

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.PerformException
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withClassName
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.util.HumanReadables
import androidx.test.espresso.util.TreeIterables
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.anyOf
import org.hamcrest.Matchers.endsWith
import java.util.concurrent.TimeoutException

object Espresso {

    @JvmStatic
    fun openActionBarOverflowMenu(isCab: Boolean = false) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        onView(isRoot()).perform(TransitionBridgingViewAction())

        onView(if (isCab) localizedContextualOverFlowButtonMatcher(context) else localizedOverFlowButtonMatcher(context)).perform(click())
    }

    @JvmStatic
    fun checkEffectiveVisible(vararg viewIds: Int) {
        for (resId in viewIds) {
            onView(withId(resId)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
        }
    }

    @JvmStatic
    fun checkEffectiveGone(vararg viewIds: Int) {
        for (resId in viewIds) {
            onView(withId(resId)).check(matches(withEffectiveVisibility(Visibility.GONE)))
        }
    }

    /**
     * Handles the cases where the app is transitioning between a contextual action bar and a
     * non contextual action bar.
     */
    private class TransitionBridgingViewAction : ViewAction {
        override fun perform(controller: UiController, view: View) {
            var loops = 0
            while (isTransitioningBetweenActionBars(view) && loops < 100) {
                loops++
                controller.loopMainThreadForAtLeast(50)
            }
        }

        override fun getDescription(): String = "Handle transition between action bar and action bar context."

        override fun getConstraints(): Matcher<View> = isRoot()

        private fun isTransitioningBetweenActionBars(view: View): Boolean {
            var actionButtonCount = 0
            for (child in TreeIterables.breadthFirstViewTraversal(view)) {
                if (localizedOverFlowButtonMatcher(view.context).matches(child)) {
                    actionButtonCount++
                }
            }
            return actionButtonCount > 1
        }
    }

    private fun localizedContextualOverFlowButtonMatcher(context: Context): Matcher<View> =
        allOf(localizedOverFlowButtonMatcher(context), isDescendantOfA(withClassName(endsWith("ActionBarContextView"))))

    @SuppressLint("PrivateResource")
    private fun localizedOverFlowButtonMatcher(context: Context): Matcher<View> =
        anyOf(
            allOf(
                isDisplayed(),
                withContentDescription(context.getString(androidx.appcompat.R.string.abc_action_menu_overflow_description))
            ),
            allOf(isDisplayed(), withClassName(endsWith("OverflowMenuButton")))
        )

    @JvmStatic
    fun wait(viewMatcher: Matcher<View>, millis: Long): ViewAction {
        return object : ViewAction {
            override fun getConstraints(): Matcher<View> = isDisplayed()

            override fun getDescription(): String = "wait for matcher <$viewMatcher> during $millis millis."

            override fun perform(uiController: UiController, view: View) {
                uiController.loopMainThreadUntilIdle()
                val startTime = System.currentTimeMillis()
                val endTime = startTime + millis

                do {
                    if (viewMatcher.matches(view)) {
                        return
                    }
                    uiController.loopMainThreadForAtLeast(50)
                } while (System.currentTimeMillis() < endTime)

                throw PerformException.Builder()
                    .withActionDescription(description)
                    .withViewDescription(HumanReadables.describe(view))
                    .withCause(TimeoutException())
                    .build()
            }
        }
    }
}
