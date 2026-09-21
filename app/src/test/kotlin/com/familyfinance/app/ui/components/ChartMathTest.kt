package com.familyfinance.app.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Chart maths, tested without a rendering harness.
 *
 * Extracting the maths from the Canvas is what makes these assertions possible at all — and it means
 * a change to how the donut closes, or how bars are scaled, is caught by a fast JVM test rather than
 * by someone noticing a sliver on a device.
 */
class ChartMathTest {

    @Test
    fun `shares sum to exactly one`() {
        val shares = ChartMath.shares(listOf(25f, 25f, 50f))
        assertThat(shares.sum()).isWithin(0.0001f).of(1f)
        assertThat(shares).containsExactly(0.25f, 0.25f, 0.5f).inOrder()
    }

    @Test
    fun `shares of an empty list is empty`() {
        assertThat(ChartMath.shares(emptyList())).isEmpty()
    }

    @Test
    fun `shares of all zeros does not divide by zero`() {
        val shares = ChartMath.shares(listOf(0f, 0f))
        assertThat(shares).containsExactly(0f, 0f).inOrder()
    }

    @Test
    fun `negative and non-finite values are treated as zero`() {
        val shares = ChartMath.shares(listOf(-5f, Float.NaN, 10f))
        assertThat(shares[0]).isEqualTo(0f)
        assertThat(shares[1]).isEqualTo(0f)
        assertThat(shares[2]).isEqualTo(1f)
    }

    @Test
    fun `normalise scales against the largest value`() {
        val normalised = ChartMath.normalise(listOf(10f, 20f, 40f))
        assertThat(normalised).containsExactly(0.25f, 0.5f, 1f).inOrder()
    }

    @Test
    fun `normalise of an all-zero series returns zeros rather than NaN`() {
        val normalised = ChartMath.normalise(listOf(0f, 0f, 0f))
        assertThat(normalised).containsExactly(0f, 0f, 0f).inOrder()
    }

    @Test
    fun `normalise handles a single point`() {
        assertThat(ChartMath.normalise(listOf(7f))).containsExactly(1f)
    }

    @Test
    fun `normalise of an empty series is empty`() {
        assertThat(ChartMath.normalise(emptyList())).isEmpty()
    }

    @Test
    fun `shares survive many awkward divisions`() {
        // Three equal slices is the classic case where naive rounding leaves a visible gap.
        val shares = ChartMath.shares(listOf(1f, 1f, 1f))
        assertThat(shares.sum()).isWithin(0.0001f).of(1f)
    }
}
