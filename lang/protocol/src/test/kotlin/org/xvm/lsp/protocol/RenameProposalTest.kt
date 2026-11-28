package org.xvm.lsp.protocol

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The hash that a rename's proposal names a document's text by, which the server, the IntelliJ plugin and VS Code's
 * extension each compute: the same for texts that differ only in the kind of their line breaks, and other for any other
 * difference. VS Code's extension checks its own hash against the same [HASH].
 */
class RenameProposalTest {
    @Test
    fun `a text's hash is its SHA-256 with each line break as a newline`() {
        assertThat(RenameProposal.textHash("class Other {\n}\n")).isEqualTo(HASH)
        assertThat(RenameProposal.textHash("class Other {\r\n}\r\n")).isEqualTo(HASH)
        assertThat(RenameProposal.textHash("class Other {\r}\r")).isEqualTo(HASH)
        assertThat(RenameProposal.textHash("class Other {\n}")).isNotEqualTo(HASH)
        assertThat(RenameProposal.textHash("class Another {\n}\n")).isNotEqualTo(HASH)
    }

    private companion object {
        /** `printf 'class Other {\n}\n' | shasum -a 256` */
        const val HASH = "24387ab61b227358c8984aac4ad34e3a48230b95557358d6a20112944842391d"
    }
}
