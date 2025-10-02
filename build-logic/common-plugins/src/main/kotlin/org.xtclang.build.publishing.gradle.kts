import org.gradle.plugins.signing.SigningExtension

/**
 * XDK Publishing Convention
 *
 * Provides centralized credential management for publishing to various repositories.
 * Registers the `xdkPublishingCredentials` extension for unified access to publishing
 * credentials from properties and environment variables.
 */

val xdkPublishingCredentials = extensions.create<XdkPublishingCredentials>("xdkPublishingCredentials")

// Configure signing plugin if available (applied by Vanniktech plugin)
plugins.withId("signing") {
    configure<SigningExtension> {
        val creds = xdkPublishingCredentials

        // Try to get the in-memory key - prefer signing.key over legacy signingInMemoryKey
        val inMemoryKey = creds.signingKey.orElse(creds.signingInMemoryKey).orElse("")
        val keyId = creds.signingKeyId.orElse("")
        val password = creds.signingPassword.orElse("")  // Can be empty for keys without passwords

        // Check if we have minimum required credentials for in-memory signing (key and keyId)
        val hasInMemoryKey = inMemoryKey.map { it.isNotEmpty() }.get()
        val hasKeyId = keyId.map { it.isNotEmpty() }.get()

        if (hasInMemoryKey && hasKeyId) {
            // Use empty string for password if not set (some keys don't have passwords)
            useInMemoryPgpKeys(keyId.get(), inMemoryKey.get(), password.get())
            logger.info("[publishing] Signing configured with in-memory GPG key (keyId: ${keyId.get()}, password: ${if (password.get().isEmpty()) "none" else "set"})")
        } else {
            val keyRingFile = creds.signingSecretKeyRingFile.get()
            if (hasKeyId && keyRingFile.isNotEmpty()) {
                logger.info("[publishing] Signing configured with keyring file: $keyRingFile (keyId: ${keyId.get()})")
            } else {
                logger.info("[publishing] Signing not configured - missing credentials (hasKey=${hasInMemoryKey}, hasKeyId=${hasKeyId})")
            }
        }
    }
}
