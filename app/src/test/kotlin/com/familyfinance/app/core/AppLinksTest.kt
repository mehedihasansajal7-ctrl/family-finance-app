package com.familyfinance.app.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppLinksTest {

    @Test
    fun `the invite message contains the code and the public download link`() {
        val message = AppLinks.inviteMessage("abcd-1234")

        assertThat(message).contains("ABCD-1234")
        assertThat(message).contains(AppLinks.APK_DOWNLOAD_URL)
    }

    @Test
    fun `the code is normalised to uppercase and trimmed`() {
        assertThat(AppLinks.inviteMessage("  k7qp-3m2x  ")).contains("K7QP-3M2X")
    }

    @Test
    fun `the download link is a public latest-release url`() {
        assertThat(AppLinks.APK_DOWNLOAD_URL).contains("/releases/latest/download/")
        assertThat(AppLinks.APK_DOWNLOAD_URL).endsWith(".apk")
    }

    @Test
    fun `the message never contains private financial language`() {
        val message = AppLinks.inviteMessage("XXXX-XXXX")
        listOf("balance", "amount", "income", "account", "৳").forEach { word ->
            assertThat(message).doesNotContain(word)
        }
    }
}
