package org.xvm.lsp.treesitter

/**
 * Native library platform detection for tree-sitter.
 */
object Platform {
    /** Platform identifier (e.g., "darwin-arm64", "linux-x64", "windows-x64") */
    val id: String

    /** Library file extension (e.g., ".dylib", ".so", ".dll") */
    val libExtension: String

    /** Library name prefix ("lib" on Unix, empty on Windows) */
    val libPrefix: String

    init {
        val osName = System.getProperty("os.name").lowercase()
        val osArch = System.getProperty("os.arch").lowercase()
        val isArm = "aarch64" in osArch || "arm64" in osArch

        when {
            "mac" in osName || "darwin" in osName -> {
                id = if (isArm) "darwin-arm64" else "darwin-x64"
                libExtension = ".dylib"
                libPrefix = "lib"
            }
            "linux" in osName -> {
                id = if (isArm) "linux-arm64" else "linux-x64"
                libExtension = ".so"
                libPrefix = "lib"
            }
            "windows" in osName -> {
                id = "windows-x64"
                libExtension = ".dll"
                libPrefix = ""
            }
            else -> throw IllegalStateException("Unsupported platform: $osName/$osArch")
        }
    }

    /** Returns the native library filename for the given base name (e.g., "tree-sitter-xtc" -> "libtree-sitter-xtc.dylib") */
    fun libraryFileName(baseName: String): String = "$libPrefix$baseName$libExtension"

    /** Returns the resource path for a native library (e.g., "/native/darwin-arm64/libtree-sitter-xtc.dylib") */
    fun resourcePath(baseName: String): String = "/native/$id/${libraryFileName(baseName)}"
}
