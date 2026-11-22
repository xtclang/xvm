# Ecstasy build modes

| Mode | Build, Rebuild and Run use |
|------|----------------------------|
| **Ecstasy compiler (embedded)**, the default | The language server's compiler and its bundled XDK, with nothing to sync, also in a folder without a Gradle build. It needs the compiler language adapter. In a folder with a Gradle build, the build's own settings, such as `runXtc` arguments, JVM options and its XDK version, do not apply. |
| **Gradle** | The folder's Gradle build, with all of its settings, as on the command line. |
| **Automatic** | Gradle when the folder has a Gradle build, and the embedded compiler otherwise. |

The next build or run uses the mode you choose, with no restart. The setting is `xtc.build.mode`.
