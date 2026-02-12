package com.xihale.snirect.util

import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

object CertUtil {
    fun isCaCertInstalled(): Boolean {
        return try {
            val certBytes = core.Core.getCACertificate() ?: return false
            if (certBytes.isEmpty()) return false
            
            val cf = CertificateFactory.getInstance("X.509")
            val caCert = cf.generateCertificate(java.io.ByteArrayInputStream(certBytes)) as X509Certificate
            
            val keyStore = KeyStore.getInstance("AndroidCAStore")
            keyStore.load(null)
            val aliases = keyStore.aliases()
            while (aliases.hasMoreElements()) {
                val alias = aliases.nextElement()
                val cert = keyStore.getCertificate(alias) as? X509Certificate
                if (cert != null && cert.issuerX500Principal == caCert.subjectX500Principal) {
                    if (cert.publicKey == caCert.publicKey) {
                        return true
                    }
                }
            }
            false
        } catch (e: Exception) {
            AppLogger.e("Error checking trust store", e)
            false
        }
    }
}
