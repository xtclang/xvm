/**
 * XDK Publishing Convention
 *
 * Provides centralized credential management for publishing to various repositories.
 * Registers the `xdkPublishingCredentials` extension for unified access to publishing
 * credentials from properties and environment variables.
 */

val xdkPublishingCredentials = extensions.create<XdkPublishingCredentials>("xdkPublishingCredentials")