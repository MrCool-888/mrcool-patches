package com.openbubbles.wear.bridge

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.PowerManager

object BridgeInfo {
    const val BRIDGE_VERSION = "3.0.0"
    fun isOpenBubblesPackage(name: String?): Boolean = name == "com.openbubbles.messaging"
    fun isPhoneLocked(context: Context): Boolean = runCatching {
        context.getSystemService(KeyguardManager::class.java)?.isDeviceLocked == true
    }.getOrDefault(false)
    fun isPhoneAwake(context: Context): Boolean = runCatching {
        context.getSystemService(PowerManager::class.java)?.isInteractive == true
    }.getOrDefault(true)
    fun phoneName(): String = listOf(Build.MANUFACTURER.orEmpty(), Build.MODEL.orEmpty())
        .filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Android phone" }
}
