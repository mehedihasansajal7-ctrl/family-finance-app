package com.familyfinance.app.core

/**
 * Public links and shareable message templates.
 *
 * The APK is hosted in a **public** GitHub releases repo so a family member can install the app
 * without any login. This URL must never point at the private source repo — keep it in sync with
 * the `family-finance-downloads` releases.
 */
object AppLinks {

    /** Public, no-login APK download URL. Stable "latest" URL. */
    const val APK_DOWNLOAD_URL =
        "https://github.com/mehedihasansajal7-ctrl/family-finance-downloads/releases/latest/download/FamilyFinance.apk"

    /**
     * The message a member shares alongside an invitation code: install instructions, the download
     * link, and the single-use code. Deliberately amount-free and free of any private data.
     */
    fun inviteMessage(code: String): String = buildString {
        append("Family Finance app e join korun!\n\n")
        append("1. App install korun (Android 8.0 ba tar upor):\n")
        append(APK_DOWNLOAD_URL)
        append("\n\n2. App khule nijer email diye sign up korun, tarpor ei code din:\n")
        append(code.trim().uppercase())
        append("\n\nCode single-use ar expire hoye jay.")
    }
}
