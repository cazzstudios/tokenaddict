package com.tokenaddict.app.ui

import android.provider.Settings
import android.widget.FrameLayout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CountdownDigitViewTest {

    private lateinit var view: CountdownDigitView

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
        view = CountdownDigitView(context)
    }

    @After
    fun tearDown() {
        (view.parent as? FrameLayout)?.removeView(view)
    }

    @Test
    fun setDigitValue_42_displaysText42() {
        view.setDigitValue(42, animate = false)
        assertEquals("42", view.text.toString())
    }

    @Test
    fun setDigitValue_5_displaysText05() {
        view.setDigitValue(5, animate = false)
        assertEquals("05", view.text.toString())
    }

    @Test
    fun setDigitValue_changesValue_triggersAnimation() {
        view.setDigitValue(10, animate = false)
        assertEquals("10", view.text.toString())

        view.setDigitValue(20, animate = true)

        val animatorField = CountdownDigitView::class.java.getDeclaredField("currentAnimator")
        animatorField.isAccessible = true
        assertNotNull("Animator should be active after value change", animatorField.get(view))

        ShadowLooper.idleMainLooper()
        ShadowLooper.idleMainLooper()

        assertEquals("20", view.text.toString())
    }

    @Test
    fun setDigitValue_sameValue_doesNotTriggerAnimation() {
        view.setDigitValue(42, animate = false)
        assertEquals("42", view.text.toString())

        view.setDigitValue(42, animate = true)
        ShadowLooper.idleMainLooper()

        val animatorField = CountdownDigitView::class.java.getDeclaredField("currentAnimator")
        animatorField.isAccessible = true
        assertEquals("Animator should be null for same value", null, animatorField.get(view))
        assertEquals("42", view.text.toString())
    }

    @Test
    fun detach_cancelsAnimation_noCrash() {
        val activityController = Robolectric.buildActivity(android.app.Activity::class.java)
            .create().start().resume()
        val activity = activityController.get()
        val container = FrameLayout(activity)
        activity.setContentView(container)
        container.addView(view)

        view.setDigitValue(10, animate = false)
        view.setDigitValue(20, animate = true)

        container.removeView(view)

        ShadowLooper.idleMainLooper()
        ShadowLooper.idleMainLooper()

        view.setDigitValue(30, animate = false)
        assertEquals("30", view.text.toString())

        activityController.pause().stop().destroy()
    }

    @Test
    fun noAnimationWhenScaleZero() {
        Settings.Global.putFloat(
            RuntimeEnvironment.getApplication().contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )

        view.setDigitValue(10, animate = false)
        assertEquals("10", view.text.toString())

        view.setDigitValue(20, animate = true)

        val animatorField = CountdownDigitView::class.java.getDeclaredField("currentAnimator")
        animatorField.isAccessible = true
        assertEquals("Animator should be null when scale is zero", null, animatorField.get(view))
        assertEquals("20", view.text.toString())
    }
}
