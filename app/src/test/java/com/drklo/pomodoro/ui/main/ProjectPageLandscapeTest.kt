package com.drklo.pomodoro.ui.main

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.drklo.pomodoro.R
import com.drklo.pomodoro.data.model.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment

/**
 * The landscape layout was reported broken on a Galaxy S23: the dial came out as an ellipse and the
 * details beside it were cut off. Both are layout arithmetic, which is what a test can hold still —
 * the bug reached a published release because nobody turns the phone sideways on every change.
 *
 * The sizes here are that phone's landscape window in dp.
 */
@RunWith(AndroidJUnit4::class)
class ProjectPageLandscapeTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val project = Project(
        id = 1,
        name = "Work",
        focusMinutes = 25,
        shortBreakMinutes = 5,
        pomodorosPerSession = 4,
        pomodoroColor = 0xFFB74B4B.toInt(),
        breakColor = 0xFF4B7BB7.toInt(),
        dailyGoal = 8,
        longBreakEnabled = true,
        longBreakMinutes = 15,
        longBreakInterval = 4,
        orderIndex = 0
    )

    private fun layoutLandscape(widthDp: Int = 892, heightDp: Int = 412) {
        // The window itself is resized rather than wrapping the page in a fixed-size Box: a Box
        // larger than the window is silently clamped to it, and the page would then be measured
        // against Robolectric's default 320dp screen while the test claimed to be testing an S23.
        RuntimeEnvironment.setQualifiers("w${widthDp}dp-h${heightDp}dp-land")
        compose.setContent {
            ProjectPage(
                project = project,
                state = null,
                isLandscape = true,
                holdFinishedColor = false,
                actions = PageActions(
                    onTap = {},
                    onReset = {},
                    onSeek = {},
                    onChangePhase = {},
                    onChooseProject = {}
                )
            )
        }
    }

    private fun dialBounds() = compose
        .onNodeWithContentDescription(context.getString(R.string.cd_play_pause))
        .getUnclippedBoundsInRoot()

    @Test
    fun theDialStaysACircleOnAWideShortScreen() {
        layoutLandscape()

        val bounds = dialBounds()
        val width = bounds.width.value
        val height = bounds.height.value

        // A tenth of a dp of rounding is fine; the reported bug was a ratio of about two to one.
        assertEquals("dial is not square: ${width}x$height", width, height, 0.1f)
    }

    @Test
    fun theDialFillsTheSpaceItIsGivenAndNoMore() {
        layoutLandscape()

        // 412dp of window, less 24dp of screen padding on each side and 8dp of the dial's own,
        // leaves 348dp of height for it. Height is the binding constraint on a landscape phone, so
        // the circle should grow into that and stop there.
        //
        // Both bounds matter. Without the lower one a 1dp dot would satisfy "square"; without the
        // upper one the layout this replaced also passes — it sized the dial off the *width* of its
        // half of the row and let the result hang out of the space it was given.
        val side = dialBounds().height.value
        assertTrue("dial is only ${side}dp tall in a 412dp window", side > 300f)
        assertTrue("dial is ${side}dp tall but was given 348dp", side <= 348.1f)
    }

    @Test
    fun theClockIsNotCutOffAtTheBottom() {
        layoutLandscape()

        // "25 min" while idle sits last in the details column, so it is the first thing to fall off
        // the bottom — and it is the one thing this screen exists to show.
        val clock = compose.onNodeWithText("25 ${context.getString(R.string.minutes_short)}")
        clock.assertIsDisplayed()

        val bounds = clock.getUnclippedBoundsInRoot()
        assertTrue(
            "clock spans ${bounds.top.value}..${bounds.bottom.value}dp, outside the 412dp window",
            bounds.top.value >= -0.1f && bounds.bottom.value <= 412.1f
        )
    }

    @Test
    fun itDegradesOnAWindowShorterThanAnyRealPhone() {
        layoutLandscape(widthDp = 960, heightDp = 320)

        val bounds = dialBounds()
        assertEquals(bounds.width.value, bounds.height.value, 0.1f)
        assertTrue("dial ${bounds.height.value}dp overflows a 320dp window", bounds.height.value <= 320f)
    }
}
