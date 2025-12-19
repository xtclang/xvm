;;; xtc-mode.el --- Major mode for Ecstasy (XTC) programming language -*- lexical-binding: t -*-

;; Copyright (C) 2024 xtclang.org
;; Generated from XTC language model

;; Author: xtclang.org
;; Keywords: languages, ecstasy, xtc
;; Version: 1.0.0
;; Package-Requires: ((emacs "26.1"))

;;; Commentary:

;; This is a major mode for editing Ecstasy (XTC) source files.
;; It provides syntax highlighting, indentation, and basic IDE features.

;;; Code:

(require 'cc-mode)

;; =============================================================================
;; Keywords and Types
;; =============================================================================

(defvar xtc-keywords
  '("if" "else" "switch" "case" "default"
    "for" "while" "do" "foreach"
    "break" "continue" "return"
    "try" "catch" "finally" "throw" "using"
    "assert" "new" "this" "super" "outer"
    "is" "as" "void")
  "XTC control and other keywords.")

(defvar xtc-declaration-keywords
  '("module" "package" "class" "interface" "mixin" "service"
    "const" "enum" "typedef" "import" "construct")
  "XTC declaration keywords.")

(defvar xtc-modifier-keywords
  '("public" "protected" "private" "static"
    "abstract" "final" "immutable"
    "extends" "implements" "incorporates" "into"
    "inject" "conditional")
  "XTC modifier keywords.")

(defvar xtc-builtin-types
  '("Bit" "Boolean" "Byte" "Char" "Dec" "Float"
    "Int" "Int8" "Int16" "Int32" "Int64" "Int128" "IntN"
    "UInt" "UInt8" "UInt16" "UInt32" "UInt64" "UInt128" "UIntN"
    "String" "Object" "Void" "Array" "List" "Map" "Set"
    "Tuple" "Function" "Type" "Class" "Enum" "Exception"
    "Iterator" "Iterable" "Collection" "Sequence" "Range" "Interval"
    "Nullable" "Orderable" "Hashable" "Stringable")
  "XTC built-in types.")

(defvar xtc-constants
  '("True" "False" "Null")
  "XTC constant literals.")

;; =============================================================================
;; Font Lock (Syntax Highlighting)
;; =============================================================================

(defvar xtc-font-lock-keywords
  (list
   ;; Annotations
   '("@[A-Za-z_][A-Za-z0-9_]*" . font-lock-preprocessor-face)
   
   ;; Keywords
   (cons (regexp-opt xtc-keywords 'words) 'font-lock-keyword-face)
   
   ;; Declaration keywords
   (cons (regexp-opt xtc-declaration-keywords 'words) 'font-lock-keyword-face)
   
   ;; Modifier keywords
   (cons (regexp-opt xtc-modifier-keywords 'words) 'font-lock-type-face)
   
   ;; Built-in types
   (cons (regexp-opt xtc-builtin-types 'words) 'font-lock-type-face)
   
   ;; Constants
   (cons (regexp-opt xtc-constants 'words) 'font-lock-constant-face)
   
   ;; Type names (PascalCase)
   '("\\<[A-Z][A-Za-z0-9_]*\\>" . font-lock-type-face)
   
   ;; Function declarations
   '("\\<\\([a-z_][A-Za-z0-9_]*\\)\\s-*(" 1 font-lock-function-name-face)
   
   ;; Constructor declarations
   '("\\<\\(construct\\|finally\\)\\s-*(" 1 font-lock-function-name-face)
   
   ;; Module/package names
   '("\\<\\(module\\|package\\)\\s-+\\([a-zA-Z_][a-zA-Z0-9_.]*\\)"
     2 font-lock-constant-face)
   
   ;; Numbers
   '("\\<[0-9][0-9_]*\\(\\.[0-9][0-9_]*\\)?\\([eE][+-]?[0-9]+\\)?\\>" . font-lock-constant-face)
   '("\\<0[xX][0-9a-fA-F][0-9a-fA-F_]*\\>" . font-lock-constant-face)
   '("\\<0[bB][01][01_]*\\>" . font-lock-constant-face))
  "Font lock keywords for XTC mode.")

;; =============================================================================
;; Syntax Table
;; =============================================================================

(defvar xtc-mode-syntax-table
  (let ((st (make-syntax-table)))
    ;; Comments: // and /* */
    (modify-syntax-entry ?/ ". 124b" st)
    (modify-syntax-entry ?* ". 23" st)
    (modify-syntax-entry ?\n "> b" st)
    
    ;; Strings
    (modify-syntax-entry ?\" "\"" st)
    (modify-syntax-entry ?' "\"" st)
    (modify-syntax-entry ?\\ "\\" st)
    
    ;; Brackets
    (modify-syntax-entry ?{ "(}" st)
    (modify-syntax-entry ?} "){" st)
    (modify-syntax-entry ?[ "(]" st)
    (modify-syntax-entry ?] ")[" st)
    (modify-syntax-entry ?( "()" st)
    (modify-syntax-entry ?) ")(" st)
    (modify-syntax-entry ?< "(>" st)
    (modify-syntax-entry ?> ")<" st)
    
    ;; Symbol constituents
    (modify-syntax-entry ?_ "_" st)
    (modify-syntax-entry ?@ "_" st)
    (modify-syntax-entry ?$ "_" st)
    
    st)
  "Syntax table for `xtc-mode'.")

;; =============================================================================
;; Indentation
;; =============================================================================

(defun xtc-indent-line ()
  "Indent current line as XTC code."
  (interactive)
  (let ((indent (xtc-calculate-indent)))
    (when indent
      (indent-line-to indent))))

(defun xtc-calculate-indent ()
  "Calculate the indentation for the current line."
  (save-excursion
    (beginning-of-line)
    (if (bobp)
        0
      (let ((not-indented t)
            cur-indent)
        ;; Check for closing brace
        (if (looking-at "^[ \t]*[})]")
            (progn
              (forward-line -1)
              (setq cur-indent (- (current-indentation) tab-width))
              (when (< cur-indent 0)
                (setq cur-indent 0)))
          ;; Not a closing brace
          (save-excursion
            (while not-indented
              (forward-line -1)
              (cond
               ;; Previous line ends with opening brace
               ((looking-at ".*[{(][ \t]*$")
                (setq cur-indent (+ (current-indentation) tab-width))
                (setq not-indented nil))
               ;; Beginning of buffer
               ((bobp)
                (setq cur-indent 0)
                (setq not-indented nil))
               ;; Default: use previous line's indentation
               (t
                (setq cur-indent (current-indentation))
                (setq not-indented nil))))))
        cur-indent))))

;; =============================================================================
;; Mode Definition
;; =============================================================================

;;;###autoload
(define-derived-mode xtc-mode prog-mode "Ecstasy"
  "Major mode for editing Ecstasy (XTC) source files.

\\{xtc-mode-map}"
  :syntax-table xtc-mode-syntax-table
  
  ;; Font lock
  (setq-local font-lock-defaults '(xtc-font-lock-keywords))
  
  ;; Comments
  (setq-local comment-start "// ")
  (setq-local comment-end "")
  (setq-local comment-start-skip "\\(//+\\|/\\*+\\)\\s *")
  (setq-local comment-multi-line t)
  
  ;; Indentation
  (setq-local indent-line-function 'xtc-indent-line)
  (setq-local indent-tabs-mode nil)
  (setq-local tab-width 4)
  (setq-local standard-indent 4)
  
  ;; Paragraphs
  (setq-local paragraph-start (concat "$\\|" page-delimiter))
  (setq-local paragraph-separate paragraph-start)
  (setq-local paragraph-ignore-fill-prefix t)
  
  ;; Fill
  (setq-local adaptive-fill-mode t)
  
  ;; Imenu
  (setq-local imenu-generic-expression
              '(("Class" "^\\s-*\\(class\\|interface\\|mixin\\|service\\|const\\|enum\\)\\s-+\\([A-Za-z_][A-Za-z0-9_]*\\)" 2)
                ("Method" "^\\s-*\\([A-Za-z_][A-Za-z0-9_<>,\\s]*\\)\\s-+\\([a-z_][A-Za-z0-9_]*\\)\\s-*(" 2)
                ("Module" "^\\s-*module\\s-+\\([A-Za-z_][A-Za-z0-9_.]*\\)" 1))))

;; =============================================================================
;; File Association
;; =============================================================================

;;;###autoload
(add-to-list 'auto-mode-alist '("\\.x\\'" . xtc-mode))
;;;###autoload
(add-to-list 'auto-mode-alist '("\\.xtc\\'" . xtc-mode))

(provide 'xtc-mode)

;;; xtc-mode.el ends here
