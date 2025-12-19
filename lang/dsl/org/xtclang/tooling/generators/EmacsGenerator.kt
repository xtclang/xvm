package org.xtclang.tooling.generators

import org.xtclang.tooling.model.KeywordCategory
import org.xtclang.tooling.model.LanguageModel

/**
 * Generates Emacs major mode files (.el)
 *
 * Emacs major modes are supported by:
 * - GNU Emacs (native support)
 * - Aquamacs (macOS Emacs)
 * - Spacemacs (Emacs distribution)
 * - Doom Emacs (Emacs distribution)
 * - XEmacs (legacy, mostly compatible)
 * - evil-mode users (Vim emulation in Emacs)
 * - MELPA/ELPA package managers
 *
 * Emacs major modes use:
 * - define-derived-mode: To define the mode based on prog-mode
 * - font-lock-keywords: For syntax highlighting
 * - Regular expressions with Emacs regex syntax
 */
class EmacsGenerator(private val model: LanguageModel) {

    fun generate(): String = buildString {
        appendLine(";;; xtc-mode.el --- Major mode for editing ${model.name} files -*- lexical-binding: t; -*-")
        appendLine()
        appendLine(";; Generated from XTC language model")
        appendLine(";; Language: ${model.name}")
        appendLine(";; File extensions: ${model.fileExtensions.joinToString(", ") { ".$it" }}")
        appendLine()
        appendLine(";;; Commentary:")
        appendLine(";; This major mode provides syntax highlighting for ${model.name} (XTC) source files.")
        appendLine(";; It is automatically generated from the XTC language model.")
        appendLine()
        appendLine(";;; Code:")
        appendLine()

        // Control keywords from model
        val controlKeywords = model.keywordsByCategory(KeywordCategory.CONTROL)
        appendLine("(defconst xtc-control-keywords")
        appendLine("  '(${controlKeywords.joinToString(" ") { "\"$it\"" }})")
        appendLine("  \"Control flow keywords in ${model.name}.\")")
        appendLine()

        // Exception keywords from model
        val exceptionKeywords = model.keywordsByCategory(KeywordCategory.EXCEPTION)
            .filter { !it.contains(":") }
        appendLine("(defconst xtc-exception-keywords")
        appendLine("  '(${exceptionKeywords.joinToString(" ") { "\"$it\"" }})")
        appendLine("  \"Exception handling keywords in ${model.name}.\")")
        appendLine()

        // Declaration keywords from model
        val declarationKeywords = model.keywordsByCategory(KeywordCategory.DECLARATION)
        appendLine("(defconst xtc-declaration-keywords")
        appendLine("  '(${declarationKeywords.joinToString(" ") { "\"$it\"" }})")
        appendLine("  \"Declaration keywords in ${model.name}.\")")
        appendLine()

        // Modifier keywords from model
        val modifierKeywords = model.keywordsByCategory(KeywordCategory.MODIFIER)
        appendLine("(defconst xtc-modifier-keywords")
        appendLine("  '(${modifierKeywords.joinToString(" ") { "\"$it\"" }})")
        appendLine("  \"Modifier keywords in ${model.name}.\")")
        appendLine()

        // Type relation keywords from model
        val typeRelationKeywords = model.keywordsByCategory(KeywordCategory.TYPE_RELATION)
        appendLine("(defconst xtc-type-relation-keywords")
        appendLine("  '(${typeRelationKeywords.joinToString(" ") { "\"$it\"" }})")
        appendLine("  \"Type relation keywords in ${model.name}.\")")
        appendLine()

        // Built-in types from model
        appendLine("(defconst xtc-builtin-types")
        appendLine("  '(${model.builtinTypes.joinToString(" ") { "\"$it\"" }})")
        appendLine("  \"Built-in types in ${model.name}.\")")
        appendLine()

        // Font-lock keywords
        appendLine("(defconst xtc-font-lock-keywords")
        appendLine("  (list")

        // Control keywords
        if (controlKeywords.isNotEmpty()) {
            appendLine("   ;; Control flow keywords")
            appendLine("   `(,(regexp-opt xtc-control-keywords 'words) . font-lock-keyword-face)")
        }

        // Exception keywords
        if (exceptionKeywords.isNotEmpty()) {
            appendLine("   ;; Exception handling keywords")
            appendLine("   `(,(regexp-opt xtc-exception-keywords 'words) . font-lock-keyword-face)")
        }

        // Declaration keywords
        if (declarationKeywords.isNotEmpty()) {
            appendLine("   ;; Declaration keywords")
            appendLine("   `(,(regexp-opt xtc-declaration-keywords 'words) . font-lock-keyword-face)")
        }

        // Modifier keywords
        if (modifierKeywords.isNotEmpty()) {
            appendLine("   ;; Modifier keywords")
            appendLine("   `(,(regexp-opt xtc-modifier-keywords 'words) . font-lock-builtin-face)")
        }

        // Type relation keywords
        if (typeRelationKeywords.isNotEmpty()) {
            appendLine("   ;; Type relation keywords")
            appendLine("   `(,(regexp-opt xtc-type-relation-keywords 'words) . font-lock-keyword-face)")
        }

        // Built-in types
        if (model.builtinTypes.isNotEmpty()) {
            appendLine("   ;; Built-in types")
            appendLine("   `(,(regexp-opt xtc-builtin-types 'words) . font-lock-type-face)")
        }

        // Annotations
        appendLine("   ;; Annotations")
        appendLine("   '(\"@[A-Za-z_][A-Za-z0-9_]*\" . font-lock-preprocessor-face)")

        // Type names (PascalCase)
        appendLine("   ;; Type names")
        appendLine("   '(\"\\\\b[A-Z][A-Za-z0-9_]*\\\\b\" . font-lock-type-face)")

        // Function definitions
        appendLine("   ;; Function definitions")
        appendLine("   '(\"\\\\b\\\\([a-z_][A-Za-z0-9_]*\\\\)\\\\s-*(\" 1 font-lock-function-name-face)")

        // Numbers
        appendLine("   ;; Numbers")
        appendLine("   '(\"\\\\b[0-9][0-9_]*\\\\(?:\\\\.[0-9][0-9_]*\\\\)?\\\\(?:[eE][+-]?[0-9]+\\\\)?\\\\b\" . font-lock-constant-face)")
        appendLine("   '(\"\\\\b0[xX][0-9a-fA-F][0-9a-fA-F_]*\\\\b\" . font-lock-constant-face)")
        appendLine("   '(\"\\\\b0[bB][01][01_]*\\\\b\" . font-lock-constant-face)")

        appendLine("   )")
        appendLine("  \"Font lock keywords for ${model.name} mode.\")")
        appendLine()

        // Syntax table
        appendLine("(defvar xtc-mode-syntax-table")
        appendLine("  (let ((table (make-syntax-table)))")
        appendLine("    ;; C-style comments")
        appendLine("    (modify-syntax-entry ?/ \". 124b\" table)")
        appendLine("    (modify-syntax-entry ?* \". 23\" table)")
        appendLine("    (modify-syntax-entry ?\\n \"> b\" table)")
        appendLine("    ;; Strings")
        appendLine("    (modify-syntax-entry ?\\\" \"\\\"\" table)")
        appendLine("    (modify-syntax-entry ?\\' \"\\\"\" table)")
        appendLine("    (modify-syntax-entry ?\\\\ \"\\\\\" table)")
        appendLine("    ;; Operators")
        appendLine("    (modify-syntax-entry ?+ \".\" table)")
        appendLine("    (modify-syntax-entry ?- \".\" table)")
        appendLine("    (modify-syntax-entry ?= \".\" table)")
        appendLine("    (modify-syntax-entry ?< \".\" table)")
        appendLine("    (modify-syntax-entry ?> \".\" table)")
        appendLine("    (modify-syntax-entry ?& \".\" table)")
        appendLine("    (modify-syntax-entry ?| \".\" table)")
        appendLine("    ;; Parentheses")
        appendLine("    (modify-syntax-entry ?\\( \"()\" table)")
        appendLine("    (modify-syntax-entry ?\\) \")(\" table)")
        appendLine("    (modify-syntax-entry ?\\[ \"(]\" table)")
        appendLine("    (modify-syntax-entry ?\\] \")[\" table)")
        appendLine("    (modify-syntax-entry ?{ \"(}\" table)")
        appendLine("    (modify-syntax-entry ?} \"){\" table)")
        appendLine("    table)")
        appendLine("  \"Syntax table for `xtc-mode'.\")")
        appendLine()

        // Indentation
        appendLine("(defcustom xtc-indent-offset 4")
        appendLine("  \"Number of spaces for each indentation step in `xtc-mode'.\"")
        appendLine("  :type 'integer")
        appendLine("  :safe 'integerp")
        appendLine("  :group 'xtc)")
        appendLine()

        // Mode definition
        appendLine(";;;###autoload")
        appendLine("(define-derived-mode xtc-mode prog-mode \"${model.name}\"")
        appendLine("  \"Major mode for editing ${model.name} source files.\"")
        appendLine("  :syntax-table xtc-mode-syntax-table")
        appendLine("  (setq-local font-lock-defaults '(xtc-font-lock-keywords))")
        appendLine("  (setq-local comment-start \"// \")")
        appendLine("  (setq-local comment-end \"\")")
        appendLine("  (setq-local comment-start-skip \"\\\\(//+\\\\|/\\\\*+\\\\)\\\\s-*\")")
        appendLine("  (setq-local indent-tabs-mode nil)")
        appendLine("  (setq-local tab-width xtc-indent-offset))")
        appendLine()

        // File associations
        for (ext in model.fileExtensions) {
            appendLine(";;;###autoload")
            appendLine("(add-to-list 'auto-mode-alist '(\"\\\\.$ext\\\\'\" . xtc-mode))")
        }
        appendLine()

        appendLine("(provide 'xtc-mode)")
        appendLine()
        appendLine(";;; xtc-mode.el ends here")
    }
}
