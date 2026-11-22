package com.union.campus

import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.union.campus.ui.common.ExternalLinks
import com.union.campus.ui.common.openExternalLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Captures intents without launching a browser, logging in or performing any transaction. */
@RunWith(AndroidJUnit4::class)
class ExternalLinkHandoffTest {
    @Test
    fun passwordRecoveryIntent_staysOnTheOfficialAuthHost_withoutExtras() {
        var captured: Intent? = null
        var failures = 0
        val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun startActivity(intent: Intent) { captured = Intent(intent) }
        }
        assertTrue(openExternalLink(ExternalLinks.RetrievePassword, context) { failures++ })
        val intent = requireNotNull(captured)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https", intent.data?.scheme)
        assertEquals("authserver.zzife.edu.cn", intent.data?.host)
        assertNull(intent.data?.userInfo)
        assertNull(intent.extras)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertEquals(0, failures)
    }

    @Test
    fun unavailableHandler_andMissingContext_eachReportOneFailure_withoutThrowing() {
        var failures = 0
        val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun startActivity(intent: Intent) { throw ActivityNotFoundException("fixture only") }
        }
        assertFalse(openExternalLink(ExternalLinks.ShowerTopUp, context) { failures++ })
        assertEquals(1, failures)
        assertFalse(openExternalLink(ExternalLinks.ElectricTopUp, null) { failures++ })
        assertEquals(2, failures)
    }
}