package com.keepalive

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

object SignatureVerifier {
    fun isTrusted(context: Context): Boolean {
        val expected = normalize(BuildConfig.EXPECTED_SIGNING_CERT_SHA256)
        if (expected.isBlank()) return true
        return getSigningCertificateHashes(context).any { it == expected }
    }

    private fun getSigningCertificateHashes(context: Context): List<String> {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        }

        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners?.toList().orEmpty()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures?.toList().orEmpty()
        }

        return signatures.map { signature ->
            val digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
            digest.joinToString("") { byte -> "%02X".format(byte) }
        }
    }

    private fun normalize(value: String): String {
        return value.replace(":", "").replace(" ", "").trim().uppercase()
    }
}