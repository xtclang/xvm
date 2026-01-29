package org.xvm.lsp.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Diagnostic")
class DiagnosticTest {

    @Test
    @DisplayName("error() should create error diagnostic")
    fun errorShouldCreateErrorDiagnostic() {
        val location = Location.of("file:///test.x", 10, 5)

        val diagnostic = Diagnostic.error(location, "Undefined variable 'x'")

        assertThat(diagnostic.severity).isEqualTo(Diagnostic.Severity.ERROR)
        assertThat(diagnostic.message).isEqualTo("Undefined variable 'x'")
        assertThat(diagnostic.location).isEqualTo(location)
        assertThat(diagnostic.source).isEqualTo("xtc")
    }

    @Test
    @DisplayName("warning() should create warning diagnostic")
    fun warningShouldCreateWarningDiagnostic() {
        val location = Location.of("file:///test.x", 5, 0)

        val diagnostic = Diagnostic.warning(location, "Unused variable")

        assertThat(diagnostic.severity).isEqualTo(Diagnostic.Severity.WARNING)
    }

    @Test
    @DisplayName("diagnostics should be immutable")
    fun diagnosticsShouldBeImmutable() {
        val loc = Location.of("file:///test.x", 1, 1)
        val d1 = Diagnostic.error(loc, "Error 1")
        val d2 = Diagnostic.error(loc, "Error 1")

        assertThat(d1).isEqualTo(d2)
        assertThat(d1.hashCode()).isEqualTo(d2.hashCode())
    }
}
