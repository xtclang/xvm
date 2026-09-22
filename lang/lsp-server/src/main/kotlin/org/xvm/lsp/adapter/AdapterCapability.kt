package org.xvm.lsp.adapter

/** Optional protocol features supported by an adapter, independent of document synchronization. */
enum class AdapterCapability {
    HOVER,
    COMPLETION,
    DEFINITION,
    REFERENCES,
    DOCUMENT_SYMBOL,
    DOCUMENT_HIGHLIGHT,
    SELECTION_RANGE,
    FOLDING_RANGE,
    RENAME,
    CODE_ACTION,
    FORMATTING,
    RANGE_FORMATTING,
    ON_TYPE_FORMATTING,
    DOCUMENT_LINK,
    SIGNATURE_HELP,
    SEMANTIC_TOKENS,
    WORKSPACE_SYMBOL,
    CODE_LENS,
    LINKED_EDITING,
}
