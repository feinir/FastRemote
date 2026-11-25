// SPDX-License-Identifier: MPL-2.0
package packaging

import org.gradle.api.GradleException
import java.io.File
import java.util.Properties
import java.util.Base64

data class EmbeddedAgentKeys(
    val manifestKeyId: String,
    val manifestPublicKey: String,
)

fun loadEmbeddedAgentKeys(keyFile: File, product: Boolean): EmbeddedAgentKeys {
    if (!keyFile.isFile) {
        throw GradleException("Missing ${keyFile.name}; create it before building this Agent mode")
    }
    val properties = Properties().apply {
        keyFile.inputStream().use(::load)
    }
    val commonNames = setOf(
        "request-encryption.key-id",
        "request-encryption.public-key-base64",
        "entitlement.online.key-id",
        "entitlement.online.public-key-base64",
        "entitlement.offline.key-id",
        "entitlement.offline.public-key-base64",
    )
    val productNames = commonNames + setOf(
        "integrity-manifest.key-id",
        "integrity-manifest.public-key-base64",
    )
    if (properties.stringPropertyNames() != if (product) productNames else commonNames) {
        throw GradleException("${keyFile.name} contains missing or unsupported properties")
    }
    if (properties.stringPropertyNames().any { name ->
            properties.getProperty(name).trim() == "REPLACE_BEFORE_RELEASE"
        }) {
        throw GradleException("Replace every placeholder in ${keyFile.name} before building")
    }
    val keyId = properties.getProperty("request-encryption.key-id").trim()
    val publicKey = properties.getProperty("request-encryption.public-key-base64").trim()
    if (!keyId.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) || !publicKey.matches(Regex("[A-Za-z0-9+/]+={0,2}"))) {
        throw GradleException("Request encryption key ID or public key encoding is invalid")
    }
    val requestPublicKeyBytes = try {
        Base64.getDecoder().decode(publicKey)
    } catch (error: IllegalArgumentException) {
        throw GradleException("Request encryption public key is not valid Base64", error)
    }
    if (requestPublicKeyBytes.size != 32) {
        throw GradleException("Request encryption public key must be a 32-byte X25519 HPKE public key")
    }
    listOf("online", "offline").forEach { purpose ->
        val keyId = properties.getProperty("entitlement.$purpose.key-id").trim()
        val encoded = properties.getProperty("entitlement.$purpose.public-key-base64").trim()
        if (!keyId.matches(Regex("[A-Za-z0-9._-]+"))) {
            throw GradleException("Entitlement signing key ID is invalid")
        }
        val decoded = try {
            Base64.getDecoder().decode(encoded)
        } catch (error: IllegalArgumentException) {
            throw GradleException("Entitlement public key is not valid Base64", error)
        }
        if (decoded.size != 32) {
            throw GradleException("Entitlement public key must be a 32-byte Ed25519 public key")
        }
    }
    val manifestKeyId = properties.getProperty("integrity-manifest.key-id", "").trim()
    if (product) {
        val manifestPublicKey = properties.getProperty("integrity-manifest.public-key-base64").trim()
        val manifestPublicKeyBytes = try {
            Base64.getDecoder().decode(manifestPublicKey)
        } catch (error: IllegalArgumentException) {
            throw GradleException("Release manifest public key is not valid Base64", error)
        }
        if (!manifestKeyId.matches(Regex("[A-Za-z0-9._-]+")) || manifestPublicKeyBytes.size != 32) {
            throw GradleException("Release manifest key must be a named 32-byte Ed25519 public key")
        }
    }
    return EmbeddedAgentKeys(
        manifestKeyId = manifestKeyId,
        manifestPublicKey = properties.getProperty("integrity-manifest.public-key-base64", "").trim(),
    )
}
