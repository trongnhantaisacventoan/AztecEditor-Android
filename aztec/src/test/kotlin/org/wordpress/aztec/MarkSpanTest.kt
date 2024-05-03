package org.wordpress.aztec

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.wordpress.aztec.spans.MarkSpan

@RunWith(RobolectricTestRunner::class)
class MarkSpanTest {
    /**
     * Test used to confirm two crashes related are fixed.
     *
     * https://github.com/wordpress-mobile/WordPress-Android/issues/20738
     */
    @Test
    fun `Calling MarkSpan#safelyParseColor with empty string should not cause a crash`() {
        var error = false
        var result: Int? = null
        try {
            val span = mockk<MarkSpan>()
            every { span.safelyParseColor("") } coAnswers { callOriginal() }
            result = span.safelyParseColor("")
        } catch (e: Exception) {
            error = true
        }
        Assert.assertFalse(error)
        Assert.assertEquals(null, result)
    }

    /**
     * Test used to confirm two crashes related are fixed.
     *
     * https://github.com/wordpress-mobile/WordPress-Android/issues/20694
     */
    @Test
    fun `Calling MarkSpan#safelyParseColor with null string should not cause a crash`() {
        var error = false
        var result: Int? = null
        try {
            val span = mockk<MarkSpan>()
            every { span.safelyParseColor(null) } coAnswers { callOriginal() }
            result = span.safelyParseColor(null)
        } catch (e: Exception) {
            error = true
        }
        Assert.assertFalse(error)
        Assert.assertEquals(null, result)
    }
}