package org.xvm.lsp.protocol

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class BuildResponseTest {
    @Test
    fun `a build's summary lists its modules' outcomes and its diagnostics at one-based locations`(
        @TempDir root: Path,
    ) {
        val source = root.resolve("App.x")
        val build =
            BuildResponse(
                succeeded = false,
                modules =
                    listOf(
                        BuiltModule("Lib", "", succeeded = true, artifact = "/out/Lib.xtc"),
                        BuiltModule("App", ""),
                        BuiltModule("Tool", "", skipped = true),
                    ),
                diagnostics =
                    listOf(
                        DocumentDiagnostics(
                            source.toUri().toString(),
                            listOf(
                                BuildDiagnostic(
                                    BuildRange(BuildPosition(1, 4), BuildPosition(1, 9)),
                                    1,
                                    "COMPILER-43",
                                    message = "no such name",
                                ),
                                BuildDiagnostic(BuildRange(BuildPosition(0, 0), BuildPosition(0, 1)), 2, message = "unused"),
                                BuildDiagnostic(BuildRange(BuildPosition(2, 0), BuildPosition(2, 1)), 3, message = "noted"),
                                BuildDiagnostic(BuildRange(BuildPosition(3, 0), BuildPosition(3, 1)), 4, message = "hinted"),
                            ),
                        ),
                        DocumentDiagnostics("untitled:Scratch", listOf(BuildDiagnostic(message = "no severity"))),
                    ),
            )

        assertThat(build.summary()).containsExactly(
            "  ✓ Lib -> /out/Lib.xtc",
            "  ✗ App",
            "  - Tool (not built: a dependency failed)",
            "$source:2:5: error: no such name [COMPILER-43]",
            "$source:1:1: warning: unused",
            "$source:3:1: info: noted",
            "$source:4:1: hint: hinted",
            "untitled:Scratch:1:1: error: no severity",
        )
    }

    @Test
    fun `a cancelled build's summary ends saying so`() {
        assertThat(BuildResponse(cancelled = true, modules = listOf(BuiltModule("App", "", succeeded = true))).summary())
            .containsExactly("  ✓ App", "Build cancelled.")
    }
}
