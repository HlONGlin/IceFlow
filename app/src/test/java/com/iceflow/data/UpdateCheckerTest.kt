package com.iceflow.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test fun comparesSemanticVersionsAndIgnoresTagPrefixes() {
        assertTrue(UpdateChecker.isNewer("v0.2.0", "0.1.9"))
        assertTrue(UpdateChecker.isNewer("1.0.0", "0.99.99"))
        assertFalse(UpdateChecker.isNewer("0.1.0", "v0.1.0"))
        assertFalse(UpdateChecker.isNewer("0.1.0-beta.1", "0.1.0"))
    }
}
