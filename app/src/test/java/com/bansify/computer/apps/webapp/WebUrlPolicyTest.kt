package com.bansify.computer.apps.webapp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebUrlPolicyTest {
    @Test fun acceptsOrdinaryHttpUrls() {
        assertTrue(WebUrlPolicy.isWebUrl("https://example.org/path?a=b"))
        assertTrue(WebUrlPolicy.isWebUrl("http://example.org:8080/"))
    }
    @Test fun rejectsDangerousOrMalformedUrls() {
        listOf("file:///data/test", "javascript:alert(1)", "https://evil.test@host.test/", "https://host.test\\@evil.test", "https://host.test/\n", "https://", "https://host.test:99999", "https://a.test/" + "a".repeat(2048)).forEach {
            assertFalse(it, WebUrlPolicy.isWebUrl(it))
        }
    }
}
