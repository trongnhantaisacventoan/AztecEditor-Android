package org.wordpress.aztec

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.wordpress.aztec.spans.MarkSpan

@RunWith(RobolectricTestRunner::class)
class MarkSpanTest {
    @Mock
    val markSpan = Mockito.mock(MarkSpan::class.java)
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
            Mockito.`when`(markSpan.safelyParseColor("")).thenCallRealMethod()
            result = markSpan.safelyParseColor("")
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
            Mockito.`when`(markSpan.safelyParseColor(null)).thenCallRealMethod()
            result = markSpan.safelyParseColor(null)
        } catch (e: Exception) {
            error = true
        }
        Assert.assertFalse(error)
        Assert.assertEquals(null, result)
    }
}