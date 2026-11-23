package org.xvm.lsp.adapter

/**
 * A rename that an adapter refuses for a reason the client should show, instead of answering it with no edit after
 * `prepareRename` accepted the name. The server answers `textDocument/rename` with the reason as its error message.
 */
class RenameRefused(
    reason: String,
) : RuntimeException(reason)
