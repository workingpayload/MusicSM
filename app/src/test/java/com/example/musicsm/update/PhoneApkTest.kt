package com.example.musicsm.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneApkTest {

    @Test
    fun `the phone build is picked, the watch build and other files are not`() {
        assertTrue(isPhoneApk("MusicSM.v3.0.0.apk"))
        assertTrue(isPhoneApk("MusicSM-v2.3.0.APK"))
        assertFalse(isPhoneApk("MusicSM.Wear.v.1.0.0.apk"))
        assertFalse(isPhoneApk("musicsm-wear-release.apk"))
        assertFalse(isPhoneApk("mapping.txt"))
    }
}
