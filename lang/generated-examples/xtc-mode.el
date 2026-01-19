;;; xtc-mode.el --- Major mode for editing Ecstasy files -*- lexical-binding: t; -*-

;; Generated from XTC language model
;; Language: Ecstasy
;; File extensions: .x, .xtc

;;; Commentary:
;; This major mode provides syntax highlighting for Ecstasy (XTC) source files.
;; It is automatically generated from the XTC language model.

;;; Code:

(defconst xtc-control-keywords
  '("if" "else" "switch" "case" "default" "for" "while" "do" "break" "continue" "return")
  "Control flow keywords in Ecstasy.")

(defconst xtc-exception-keywords
  '("try" "catch" "finally" "throw" "using" "assert")
  "Exception handling keywords in Ecstasy.")

(defconst xtc-declaration-keywords
  '("construct" "function" "typedef" "import" "module" "package" "class" "interface" "mixin" "service" "const" "enum" "struct" "val" "var")
  "Declaration keywords in Ecstasy.")

(defconst xtc-modifier-keywords
  '("public" "protected" "private" "static" "immutable" "conditional" "allow" "avoid" "prefer" "desired" "required" "optional" "embedded" "inject")
  "Modifier keywords in Ecstasy.")

(defconst xtc-type-relation-keywords
  '("extends" "implements" "delegates" "incorporates" "into")
  "Type relation keywords in Ecstasy.")

(defconst xtc-builtin-types
  '("Bit" "Boolean" "Byte" "Char" "Int" "Int8" "Int16" "Int32" "Int64" "Int128" "IntN" "UInt" "UInt8" "UInt16" "UInt32" "UInt64" "UInt128" "UIntN" "Dec" "Dec32" "Dec64" "Dec128" "DecN" "Float" "Float8e4" "Float8e5" "Float16" "Float32" "Float64" "Float128" "FloatN" "BFloat16" "String" "Char" "Object" "Enum" "Exception" "Const" "Service" "Module" "Package" "Array" "List" "Set" "Map" "Range" "Interval" "Tuple" "Function" "Method" "Property" "Type" "Class" "Nullable" "Orderable" "Hashable" "Stringable" "Iterator" "Iterable" "Collection" "Sequence" "Void" "Null" "True" "False")
  "Built-in types in Ecstasy.")

(defconst xtc-boolean-constants
  '("True" "False")
  "Boolean constants in Ecstasy.")

(defconst xtc-null-constant
  '("Null")
  "Null constant in Ecstasy.")

(defconst xtc-font-lock-keywords
  (list
   ;; Control flow keywords
   `(,(regexp-opt xtc-control-keywords 'words) . font-lock-keyword-face)
   ;; Exception handling keywords
   `(,(regexp-opt xtc-exception-keywords 'words) . font-lock-keyword-face)
   ;; Declaration keywords
   `(,(regexp-opt xtc-declaration-keywords 'words) . font-lock-keyword-face)
   ;; Modifier keywords
   `(,(regexp-opt xtc-modifier-keywords 'words) . font-lock-builtin-face)
   ;; Type relation keywords
   `(,(regexp-opt xtc-type-relation-keywords 'words) . font-lock-keyword-face)
   ;; Built-in types
   `(,(regexp-opt xtc-builtin-types 'words) . font-lock-type-face)
   ;; Annotations
   '("@[A-Za-z_][A-Za-z0-9_]*" . font-lock-preprocessor-face)
   ;; Type names
   '("\\b[A-Z][A-Za-z0-9_]*\\b" . font-lock-type-face)
   ;; Function definitions
   '("\\b\\([a-z_][A-Za-z0-9_]*\\)\\s-*(" 1 font-lock-function-name-face)
   ;; Boolean constants
   `(,(regexp-opt xtc-boolean-constants 'words) . font-lock-constant-face)
   ;; Null constant
   `(,(regexp-opt xtc-null-constant 'words) . font-lock-constant-face)
   ;; Numbers
   '("\\b[0-9][0-9_]*\\(?:\\.[0-9][0-9_]*\\)?\\(?:[eE][+-]?[0-9]+\\)?\\b" . font-lock-constant-face)
   '("\\b0[xX][0-9a-fA-F][0-9a-fA-F_]*\\b" . font-lock-constant-face)
   '("\\b0[bB][01][01_]*\\b" . font-lock-constant-face)
   )
  "Font lock keywords for Ecstasy mode.")

(defvar xtc-mode-syntax-table
  (let ((table (make-syntax-table)))
    ;; C-style comments
    (modify-syntax-entry ?/ ". 124b" table)
    (modify-syntax-entry ?* ". 23" table)
    (modify-syntax-entry ?\n "> b" table)
    ;; Strings
    (modify-syntax-entry ?\" "\"" table)
    (modify-syntax-entry ?\' "\"" table)
    (modify-syntax-entry ?\\ "\\" table)
    ;; Operators
    (modify-syntax-entry ?+ "." table)
    (modify-syntax-entry ?- "." table)
    (modify-syntax-entry ?= "." table)
    (modify-syntax-entry ?< "." table)
    (modify-syntax-entry ?> "." table)
    (modify-syntax-entry ?& "." table)
    (modify-syntax-entry ?| "." table)
    ;; Parentheses
    (modify-syntax-entry ?\( "()" table)
    (modify-syntax-entry ?\) ")(" table)
    (modify-syntax-entry ?\[ "(]" table)
    (modify-syntax-entry ?\] ")[" table)
    (modify-syntax-entry ?{ "(}" table)
    (modify-syntax-entry ?} "){" table)
    table)
  "Syntax table for `xtc-mode'.")

(defcustom xtc-indent-offset 4
  "Number of spaces for each indentation step in `xtc-mode'."
  :type 'integer
  :safe 'integerp
  :group 'xtc)

;;;###autoload
(define-derived-mode xtc-mode prog-mode "Ecstasy"
  "Major mode for editing Ecstasy source files."
  :syntax-table xtc-mode-syntax-table
  (setq-local font-lock-defaults '(xtc-font-lock-keywords))
  (setq-local comment-start "// ")
  (setq-local comment-end "")
  (setq-local comment-start-skip "\\(//+\\|/\\*+\\)\\s-*")
  (setq-local indent-tabs-mode nil)
  (setq-local tab-width xtc-indent-offset))

;;;###autoload
(add-to-list 'auto-mode-alist '("\\.x\\'" . xtc-mode))
;;;###autoload
(add-to-list 'auto-mode-alist '("\\.xtc\\'" . xtc-mode))

(provide 'xtc-mode)

;;; xtc-mode.el ends here
