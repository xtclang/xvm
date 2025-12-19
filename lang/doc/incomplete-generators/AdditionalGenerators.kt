package org.xtclang.tooling.generators

import org.xtclang.tooling.model.*

/**
 * Generates Eclipse plugin components for syntax highlighting
 */
class EclipseGenerator(private val model: LanguageModel) {
    
    fun generatePluginXml(): String = """
<?xml version="1.0" encoding="UTF-8"?>
<?eclipse version="3.4"?>
<plugin>
   <extension point="org.eclipse.ui.editors">
      <editor
            class="org.xtclang.eclipse.XtcEditor"
            contributorClass="org.eclipse.ui.texteditor.BasicTextEditorActionContributor"
            extensions="${model.fileExtensions.joinToString(",")}"
            icon="icons/xtc.png"
            id="org.xtclang.eclipse.editor"
            name="${model.name} Editor">
      </editor>
   </extension>
   
   <extension point="org.eclipse.core.contenttype.contentTypes">
      <content-type
            base-type="org.eclipse.core.runtime.text"
            file-extensions="${model.fileExtensions.joinToString(",")}"
            id="org.xtclang.eclipse.contenttype"
            name="${model.name} Source File"
            priority="normal">
      </content-type>
   </extension>
   
   <extension point="org.eclipse.ui.editors.documentSetupParticipants">
      <participant
            class="org.xtclang.eclipse.XtcDocumentSetupParticipant"
            contentTypeId="org.xtclang.eclipse.contenttype"
            id="org.xtclang.eclipse.documentSetup">
      </participant>
   </extension>
</plugin>
""".trimIndent()
    
    fun generatePartitionScanner(): String = """
package org.xtclang.eclipse;

import org.eclipse.jface.text.rules.*;
import org.eclipse.jface.text.*;
import java.util.*;

/**
 * Partition scanner for ${model.name} - divides document into regions
 * (code, strings, comments) for syntax highlighting.
 */
public class XtcPartitionScanner extends RuleBasedPartitionScanner {
    
    public static final String XTC_PARTITIONING = "__xtc_partitioning";
    
    public static final String XTC_COMMENT = "__xtc_comment";
    public static final String XTC_DOC_COMMENT = "__xtc_doc_comment";
    public static final String XTC_STRING = "__xtc_string";
    public static final String XTC_TEMPLATE_STRING = "__xtc_template_string";
    
    public static final String[] PARTITION_TYPES = {
        XTC_COMMENT,
        XTC_DOC_COMMENT, 
        XTC_STRING,
        XTC_TEMPLATE_STRING
    };
    
    public XtcPartitionScanner() {
        IToken comment = new Token(XTC_COMMENT);
        IToken docComment = new Token(XTC_DOC_COMMENT);
        IToken string = new Token(XTC_STRING);
        IToken templateString = new Token(XTC_TEMPLATE_STRING);
        
        List<IPredicateRule> rules = new ArrayList<>();
        
        // Doc comments: /** ... */
        rules.add(new MultiLineRule("/**", "*/", docComment, (char) 0, true));
        
        // Block comments: /* ... */
        rules.add(new MultiLineRule("/*", "*/", comment, (char) 0, true));
        
        // Line comments: // ...
        rules.add(new EndOfLineRule("//", comment));
        
        // Template strings: $"..."
        rules.add(new MultiLineRule("\$\"", "\"", templateString, '\\'));
        
        // Regular strings: "..."
        rules.add(new MultiLineRule("\"", "\"", string, '\\'));
        
        // Character literals: '...'
        rules.add(new SingleLineRule("'", "'", string, '\\'));
        
        setPredicateRules(rules.toArray(new IPredicateRule[0]));
    }
}
""".trimIndent()
    
    fun generateCodeScanner(): String = """
package org.xtclang.eclipse;

import org.eclipse.jface.text.*;
import org.eclipse.jface.text.rules.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.RGB;
import java.util.*;

/**
 * Code scanner for ${model.name} syntax highlighting.
 */
public class XtcCodeScanner extends RuleBasedScanner {
    
    // Color definitions
    private static final RGB KEYWORD_COLOR = new RGB(127, 0, 85);      // Purple
    private static final RGB TYPE_COLOR = new RGB(0, 0, 192);          // Blue
    private static final RGB STRING_COLOR = new RGB(42, 0, 255);       // Dark blue
    private static final RGB NUMBER_COLOR = new RGB(0, 128, 0);        // Green
    private static final RGB ANNOTATION_COLOR = new RGB(100, 100, 100); // Gray
    private static final RGB OPERATOR_COLOR = new RGB(0, 0, 0);        // Black
    
    private static final String[] KEYWORDS = {
        ${model.keywords.sorted().joinToString(",\n        ") { "\"$it\"" }}
    };
    
    private static final String[] BUILTIN_TYPES = {
        "Bit", "Boolean", "Byte", "Char", "Dec", "Float",
        "Int", "Int8", "Int16", "Int32", "Int64", "Int128", "IntN",
        "UInt", "UInt8", "UInt16", "UInt32", "UInt64", "UInt128", "UIntN",
        "String", "Object", "Void", "Array", "List", "Map", "Set"
    };
    
    public XtcCodeScanner(ColorManager colorManager) {
        IToken keyword = new Token(new TextAttribute(
            colorManager.getColor(KEYWORD_COLOR), null, SWT.BOLD));
        IToken type = new Token(new TextAttribute(
            colorManager.getColor(TYPE_COLOR), null, SWT.BOLD));
        IToken string = new Token(new TextAttribute(
            colorManager.getColor(STRING_COLOR)));
        IToken number = new Token(new TextAttribute(
            colorManager.getColor(NUMBER_COLOR)));
        IToken annotation = new Token(new TextAttribute(
            colorManager.getColor(ANNOTATION_COLOR)));
        IToken defaultToken = new Token(new TextAttribute(
            colorManager.getColor(new RGB(0, 0, 0))));
        
        List<IRule> rules = new ArrayList<>();
        
        // Whitespace
        rules.add(new WhitespaceRule(c -> Character.isWhitespace(c)));
        
        // Annotations: @Name
        rules.add(new PatternRule("@", null, annotation, (char) 0, true) {
            @Override
            protected boolean sequenceDetected(ICharacterScanner scanner, char[] sequence, boolean eofAllowed) {
                int c = scanner.read();
                if (Character.isJavaIdentifierStart(c)) {
                    do {
                        c = scanner.read();
                    } while (Character.isJavaIdentifierPart(c));
                    scanner.unread();
                    return true;
                }
                scanner.unread();
                return false;
            }
        });
        
        // Numbers (hex, binary, float, int)
        rules.add(new NumberRule(number));
        
        // Keywords and types
        WordRule wordRule = new WordRule(new IWordDetector() {
            @Override
            public boolean isWordStart(char c) {
                return Character.isJavaIdentifierStart(c);
            }
            @Override
            public boolean isWordPart(char c) {
                return Character.isJavaIdentifierPart(c);
            }
        }, defaultToken);
        
        for (String kw : KEYWORDS) {
            wordRule.addWord(kw, keyword);
        }
        for (String t : BUILTIN_TYPES) {
            wordRule.addWord(t, type);
        }
        // Boolean and null literals
        wordRule.addWord("True", keyword);
        wordRule.addWord("False", keyword);
        wordRule.addWord("Null", keyword);
        
        rules.add(wordRule);
        
        setRules(rules.toArray(new IRule[0]));
    }
}
""".trimIndent()
}

/**
 * Generates Monaco Editor (VS Code web, various online editors) language support
 */
class MonacoGenerator(private val model: LanguageModel) {
    
    fun generateLanguageDefinition(): String = """
import * as monaco from 'monaco-editor';

/**
 * ${model.name} language definition for Monaco Editor
 * 
 * Use in web-based editors like:
 * - VS Code for Web
 * - GitHub Codespaces  
 * - StackBlitz
 * - CodeSandbox
 * - Custom web IDEs
 */

// Register the language
monaco.languages.register({
    id: '${model.name.lowercase()}',
    extensions: [${model.fileExtensions.joinToString(", ") { "'.$it'" }}],
    aliases: ['${model.name}', '${model.name.lowercase()}'],
    mimetypes: ['text/x-${model.name.lowercase()}']
});

// Define the language configuration (brackets, comments, etc.)
monaco.languages.setLanguageConfiguration('${model.name.lowercase()}', {
    comments: {
        lineComment: '${model.comments.lineComment}',
        blockComment: ['${model.comments.blockCommentStart}', '${model.comments.blockCommentEnd}']
    },
    brackets: [
        ['{', '}'],
        ['[', ']'],
        ['(', ')'],
        ['<', '>']
    ],
    autoClosingPairs: [
        { open: '{', close: '}' },
        { open: '[', close: ']' },
        { open: '(', close: ')' },
        { open: '<', close: '>' },
        { open: '"', close: '"', notIn: ['string'] },
        { open: "'", close: "'", notIn: ['string'] },
        { open: '/*', close: ' */' }
    ],
    surroundingPairs: [
        { open: '{', close: '}' },
        { open: '[', close: ']' },
        { open: '(', close: ')' },
        { open: '<', close: '>' },
        { open: '"', close: '"' },
        { open: "'", close: "'" }
    ],
    folding: {
        markers: {
            start: /^\s*\/\/\s*#?region\b/,
            end: /^\s*\/\/\s*#?endregion\b/
        }
    },
    indentationRules: {
        increaseIndentPattern: /^.*\{[^}"']*${'$'}|^.*\([^)"']*${'$'}/,
        decreaseIndentPattern: /^\s*[\}\)]/
    }
});

// Define the monarch tokenizer (syntax highlighting)
monaco.languages.setMonarchTokensProvider('${model.name.lowercase()}', {
    
    // Keywords
    keywords: [
        ${model.keywords.sorted().joinToString(",\n        ") { "'$it'" }}
    ],
    
    // Type keywords
    typeKeywords: [
        'Bit', 'Boolean', 'Byte', 'Char', 'Dec', 'Float',
        'Int', 'Int8', 'Int16', 'Int32', 'Int64', 'Int128', 'IntN',
        'UInt', 'UInt8', 'UInt16', 'UInt32', 'UInt64', 'UInt128', 'UIntN',
        'String', 'Object', 'Void', 'Array', 'List', 'Map', 'Set',
        'Tuple', 'Function', 'Type', 'Class', 'Enum', 'Exception'
    ],
    
    // Operators
    operators: [
        ${model.operators.map { "'${escapeJs(it.symbol)}'" }.joinToString(", ")}
    ],
    
    // Symbols for operators
    symbols: /[=><!~?:&|+\-*\/\^%]+/,
    
    // Escapes
    escapes: /\\\\(?:[abfnrtv\\\\"']|x[0-9A-Fa-f]{1,4}|u[0-9A-Fa-f]{4}|U[0-9A-Fa-f]{8})/,
    
    // Tokenizer rules
    tokenizer: {
        root: [
            // Identifiers and keywords
            [/[a-z_][\w]*/, {
                cases: {
                    '@keywords': 'keyword',
                    '@default': 'identifier'
                }
            }],
            
            // Type identifiers (PascalCase)
            [/[A-Z][\w]*/, {
                cases: {
                    '@typeKeywords': 'type.identifier',
                    '@default': 'type.identifier'
                }
            }],
            
            // Whitespace
            { include: '@whitespace' },
            
            // Annotations
            [/@[a-zA-Z_][\w]*/, 'annotation'],
            
            // Delimiters and operators
            [/[{}()\[\]]/, '@brackets'],
            [/[<>](?!@symbols)/, '@brackets'],
            
            [/@symbols/, {
                cases: {
                    '@operators': 'operator',
                    '@default': ''
                }
            }],
            
            // Numbers
            [/\d*\.\d+([eE][\-+]?\d+)?/, 'number.float'],
            [/0[xX][0-9a-fA-F][0-9a-fA-F_]*/, 'number.hex'],
            [/0[bB][01][01_]*/, 'number.binary'],
            [/\d[\d_]*/, 'number'],
            
            // Delimiter
            [/[;,.]/, 'delimiter'],
            
            // Strings
            [/\$"/, { token: 'string.quote', bracket: '@open', next: '@templatestring' }],
            [/"([^"\\]|\\.)*${'$'}/, 'string.invalid'],  // non-terminated
            [/"/, { token: 'string.quote', bracket: '@open', next: '@string' }],
            
            // Characters
            [/'[^\\']'/, 'string'],
            [/'/, 'string.invalid']
        ],
        
        comment: [
            [/[^\/*]+/, 'comment'],
            [/\/\*/, 'comment', '@push'],  // Nested comment
            [/\*\//, 'comment', '@pop'],
            [/[\/*]/, 'comment']
        ],
        
        doccomment: [
            [/[^\/*]+/, 'comment.doc'],
            [/@\w+/, 'comment.doc.tag'],
            [/\/\*/, 'comment.doc', '@push'],
            [/\*\//, 'comment.doc', '@pop'],
            [/[\/*]/, 'comment.doc']
        ],
        
        string: [
            [/[^\\"]+/, 'string'],
            [/@escapes/, 'string.escape'],
            [/\\./, 'string.escape.invalid'],
            [/"/, { token: 'string.quote', bracket: '@close', next: '@pop' }]
        ],
        
        templatestring: [
            [/[^\\"\{]+/, 'string'],
            [/@escapes/, 'string.escape'],
            [/\{/, { token: 'delimiter.bracket', next: '@templateexpr' }],
            [/\\./, 'string.escape.invalid'],
            [/"/, { token: 'string.quote', bracket: '@close', next: '@pop' }]
        ],
        
        templateexpr: [
            [/\}/, { token: 'delimiter.bracket', next: '@pop' }],
            { include: 'root' }
        ],
        
        whitespace: [
            [/[ \t\r\n]+/, 'white'],
            [/\/\*\*(?!\/)/, 'comment.doc', '@doccomment'],
            [/\/\*/, 'comment', '@comment'],
            [/\/\/.*${'$'}/, 'comment']
        ]
    }
});

// Register completion item provider (basic)
monaco.languages.registerCompletionItemProvider('${model.name.lowercase()}', {
    provideCompletionItems: (model, position) => {
        const suggestions = [
            // Keywords
            ${model.keywords.take(10).joinToString(",\n            ") { kw ->
                """{ label: '$kw', kind: monaco.languages.CompletionItemKind.Keyword, insertText: '$kw' }"""
            }},
            // Snippets
            {
                label: 'module',
                kind: monaco.languages.CompletionItemKind.Snippet,
                insertText: 'module ${'$'}{1:ModuleName} {\n\t${'$'}0\n}',
                insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
                documentation: 'Create a new module'
            },
            {
                label: 'class',
                kind: monaco.languages.CompletionItemKind.Snippet,
                insertText: 'class ${'$'}{1:ClassName} {\n\t${'$'}0\n}',
                insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
                documentation: 'Create a new class'
            }
        ];
        return { suggestions };
    }
});

export default '${model.name.lowercase()}';
""".trimIndent()
    
    private fun escapeJs(s: String): String = s
        .replace("\\", "\\\\")
        .replace("'", "\\'")
}

/**
 * Generates Vim/Neovim syntax files
 */
class VimGenerator(private val model: LanguageModel) {
    
    fun generateSyntax(): String = buildString {
        appendLine("\" Vim syntax file")
        appendLine("\" Language: ${model.name}")
        appendLine("\" Generated from XTC language model")
        appendLine()
        appendLine("if exists('b:current_syntax')")
        appendLine("  finish")
        appendLine("endif")
        appendLine()
        appendLine("let s:cpo_save = &cpo")
        appendLine("set cpo&vim")
        appendLine()
        
        // Keywords
        appendLine("\" Keywords")
        val controlKw = listOf("if", "else", "switch", "case", "default", "for", "while", "do", 
                               "foreach", "break", "continue", "return", "try", "catch", "finally", 
                               "throw", "assert", "using")
        val declKw = listOf("module", "package", "class", "interface", "mixin", "service", 
                           "const", "enum", "typedef", "import", "construct")
        val modifierKw = listOf("public", "protected", "private", "static", "abstract", 
                               "final", "immutable", "extends", "implements", "incorporates", "into")
        
        appendLine("syn keyword xtcControl ${controlKw.joinToString(" ")}")
        appendLine("syn keyword xtcDeclaration ${declKw.joinToString(" ")}")
        appendLine("syn keyword xtcModifier ${modifierKw.joinToString(" ")}")
        appendLine("syn keyword xtcOperatorKw new this super outer is as inject conditional void")
        appendLine()
        
        // Types
        appendLine("\" Built-in types")
        appendLine("syn keyword xtcType Bit Boolean Byte Char Dec Float Int Int8 Int16 Int32 Int64 Int128 IntN")
        appendLine("syn keyword xtcType UInt UInt8 UInt16 UInt32 UInt64 UInt128 UIntN String Object Void")
        appendLine("syn keyword xtcType Array List Map Set Tuple Function Type Class Enum Exception")
        appendLine()
        
        // Literals
        appendLine("\" Literals")
        appendLine("syn keyword xtcBoolean True False")
        appendLine("syn keyword xtcNull Null")
        appendLine()
        
        // Numbers
        appendLine("\" Numbers")
        appendLine("syn match xtcNumber '\\<\\d[\\d_]*\\>'")
        appendLine("syn match xtcNumber '\\<0[xX][0-9a-fA-F][0-9a-fA-F_]*\\>'")
        appendLine("syn match xtcNumber '\\<0[bB][01][01_]*\\>'")
        appendLine("syn match xtcFloat '\\<\\d[\\d_]*\\.\\d[\\d_]*\\([eE][+-]\\?\\d\\+\\)\\?\\>'")
        appendLine()
        
        // Strings
        appendLine("\" Strings")
        appendLine("syn region xtcString start='\"' skip='\\\\.' end='\"'")
        appendLine("syn region xtcTemplateString start='\\$\"' skip='\\\\.' end='\"'")
        appendLine("syn match xtcCharacter \"'[^'\\\\]'\"")
        appendLine("syn match xtcCharacter \"'\\\\.'\"")
        appendLine()
        
        // Comments
        appendLine("\" Comments")
        appendLine("syn region xtcComment start='//' end='$'")
        appendLine("syn region xtcComment start='/\\*' end='\\*/' contains=xtcComment")
        appendLine("syn region xtcDocComment start='/\\*\\*' end='\\*/' contains=xtcDocTag")
        appendLine("syn match xtcDocTag '@\\w\\+' contained")
        appendLine()
        
        // Annotations
        appendLine("\" Annotations")
        appendLine("syn match xtcAnnotation '@[A-Za-z_][A-Za-z0-9_]*'")
        appendLine()
        
        // Type names (PascalCase identifiers)
        appendLine("\" Type names")
        appendLine("syn match xtcTypeName '\\<[A-Z][A-Za-z0-9_]*\\>'")
        appendLine()
        
        // Operators
        appendLine("\" Operators")
        appendLine("syn match xtcOperator '[+\\-*/%&|^~<>=!?:.]'")
        appendLine("syn match xtcOperator '&&\\|||\\|\\^\\^'")
        appendLine("syn match xtcOperator '==\\|!=\\|<=\\|>=\\|<=>'")
        appendLine("syn match xtcOperator '<<\\|>>\\|>>>'")
        appendLine("syn match xtcOperator '\\.\\.\\|\\.\\.<'")
        appendLine("syn match xtcOperator '?:\\|?\\.'")
        appendLine()
        
        // Highlight groups
        appendLine("\" Highlighting")
        appendLine("hi def link xtcControl Keyword")
        appendLine("hi def link xtcDeclaration Keyword")
        appendLine("hi def link xtcModifier StorageClass")
        appendLine("hi def link xtcOperatorKw Keyword")
        appendLine("hi def link xtcType Type")
        appendLine("hi def link xtcTypeName Type")
        appendLine("hi def link xtcBoolean Boolean")
        appendLine("hi def link xtcNull Constant")
        appendLine("hi def link xtcNumber Number")
        appendLine("hi def link xtcFloat Float")
        appendLine("hi def link xtcString String")
        appendLine("hi def link xtcTemplateString String")
        appendLine("hi def link xtcCharacter Character")
        appendLine("hi def link xtcComment Comment")
        appendLine("hi def link xtcDocComment SpecialComment")
        appendLine("hi def link xtcDocTag Special")
        appendLine("hi def link xtcAnnotation PreProc")
        appendLine("hi def link xtcOperator Operator")
        appendLine()
        
        appendLine("let b:current_syntax = 'xtc'")
        appendLine()
        appendLine("let &cpo = s:cpo_save")
        appendLine("unlet s:cpo_save")
    }
    
    fun generateFtdetect(): String = """
" Detect ${model.name} files
au BufRead,BufNewFile ${model.fileExtensions.joinToString(",") { "*.$it" }} set filetype=xtc
""".trimIndent()
    
    fun generateIndent(): String = """
" Vim indent file
" Language: ${model.name}

if exists('b:did_indent')
  finish
endif
let b:did_indent = 1

setlocal indentexpr=GetXtcIndent()
setlocal indentkeys=0{,0},0),0],!^F,o,O,e

if exists('*GetXtcIndent')
  finish
endif

function! GetXtcIndent()
  let lnum = prevnonblank(v:lnum - 1)
  if lnum == 0
    return 0
  endif
  
  let line = getline(lnum)
  let ind = indent(lnum)
  
  " Increase indent after opening braces
  if line =~ '[{(\[]\s*$'
    let ind += shiftwidth()
  endif
  
  " Decrease indent for closing braces
  let thisline = getline(v:lnum)
  if thisline =~ '^\s*[})\]]'
    let ind -= shiftwidth()
  endif
  
  return ind
endfunction
""".trimIndent()
}

/**
 * Generates Emacs major mode
 */
class EmacsGenerator(private val model: LanguageModel) {
    
    fun generateMajorMode(): String = buildString {
        appendLine(";;; xtc-mode.el --- Major mode for ${model.name} programming language -*- lexical-binding: t -*-")
        appendLine()
        appendLine(";; Generated from XTC language model")
        appendLine(";; Author: xtclang.org")
        appendLine(";; Keywords: languages")
        appendLine()
        appendLine(";;; Commentary:")
        appendLine(";; This is a major mode for editing ${model.name} source files.")
        appendLine()
        appendLine(";;; Code:")
        appendLine()
        appendLine("(require 'cc-mode)")
        appendLine()
        
        // Keywords
        appendLine("(defvar xtc-keywords")
        appendLine("  '(${model.keywords.sorted().joinToString(" ") { "\"$it\"" }})")
        appendLine("  \"${model.name} keywords.\")")
        appendLine()
        
        // Types
        appendLine("(defvar xtc-types")
        appendLine("  '(\"Bit\" \"Boolean\" \"Byte\" \"Char\" \"Dec\" \"Float\"")
        appendLine("    \"Int\" \"Int8\" \"Int16\" \"Int32\" \"Int64\" \"Int128\" \"IntN\"")
        appendLine("    \"UInt\" \"UInt8\" \"UInt16\" \"UInt32\" \"UInt64\" \"UInt128\" \"UIntN\"")
        appendLine("    \"String\" \"Object\" \"Void\" \"Array\" \"List\" \"Map\" \"Set\"")
        appendLine("    \"Tuple\" \"Function\" \"Type\" \"Class\" \"Enum\" \"Exception\")")
        appendLine("  \"${model.name} built-in types.\")")
        appendLine()
        
        // Constants
        appendLine("(defvar xtc-constants")
        appendLine("  '(\"True\" \"False\" \"Null\")")
        appendLine("  \"${model.name} constants.\")")
        appendLine()
        
        // Font-lock keywords
        appendLine("(defvar xtc-font-lock-keywords")
        appendLine("  (list")
        appendLine("   ;; Keywords")
        appendLine("   (cons (regexp-opt xtc-keywords 'words) 'font-lock-keyword-face)")
        appendLine("   ;; Types")
        appendLine("   (cons (regexp-opt xtc-types 'words) 'font-lock-type-face)")
        appendLine("   ;; Constants")
        appendLine("   (cons (regexp-opt xtc-constants 'words) 'font-lock-constant-face)")
        appendLine("   ;; Annotations")
        appendLine("   '(\"@[A-Za-z_][A-Za-z0-9_]*\" . font-lock-preprocessor-face)")
        appendLine("   ;; Type names (PascalCase)")
        appendLine("   '(\"\\\\<[A-Z][A-Za-z0-9_]*\\\\>\" . font-lock-type-face)")
        appendLine("   ;; Function declarations")
        appendLine("   '(\"\\\\<\\\\([a-z_][A-Za-z0-9_]*\\\\)\\\\s-*(\" 1 font-lock-function-name-face)")
        appendLine("  )")
        appendLine("  \"Syntax highlighting for ${model.name}.\")")
        appendLine()
        
        // Syntax table
        appendLine("(defvar xtc-mode-syntax-table")
        appendLine("  (let ((st (make-syntax-table)))")
        appendLine("    ;; Comments")
        appendLine("    (modify-syntax-entry ?/ \". 124b\" st)")
        appendLine("    (modify-syntax-entry ?* \". 23\" st)")
        appendLine("    (modify-syntax-entry ?\\n \"> b\" st)")
        appendLine("    ;; Strings")
        appendLine("    (modify-syntax-entry ?\\\" \"\\\"\" st)")
        appendLine("    (modify-syntax-entry ?\\' \"\\\"\" st)")
        appendLine("    (modify-syntax-entry ?\\\\ \"\\\\\" st)")
        appendLine("    ;; Brackets")
        appendLine("    (modify-syntax-entry ?{ \"(}\" st)")
        appendLine("    (modify-syntax-entry ?} \"){\" st)")
        appendLine("    (modify-syntax-entry ?[ \"(]\" st)")
        appendLine("    (modify-syntax-entry ?] \")[\" st)")
        appendLine("    (modify-syntax-entry ?< \"(>\" st)")
        appendLine("    (modify-syntax-entry ?> \")<\" st)")
        appendLine("    ;; Annotation prefix")
        appendLine("    (modify-syntax-entry ?@ \"_\" st)")
        appendLine("    st)")
        appendLine("  \"Syntax table for `xtc-mode'.\")")
        appendLine()
        
        // Mode definition
        appendLine(";;;###autoload")
        appendLine("(define-derived-mode xtc-mode prog-mode \"${model.name}\"")
        appendLine("  \"Major mode for editing ${model.name} source files.\"")
        appendLine("  :syntax-table xtc-mode-syntax-table")
        appendLine("  (setq-local font-lock-defaults '(xtc-font-lock-keywords))")
        appendLine("  (setq-local comment-start \"// \")")
        appendLine("  (setq-local comment-end \"\")")
        appendLine("  (setq-local comment-start-skip \"\\\\(//+\\\\|/\\\\*+\\\\)\\\\s *\")")
        appendLine("  (setq-local indent-tabs-mode nil)")
        appendLine("  (setq-local tab-width 4)")
        appendLine("  (setq-local standard-indent 4))")
        appendLine()
        
        // Auto-mode-alist
        appendLine(";;;###autoload")
        model.fileExtensions.forEach { ext ->
            appendLine("(add-to-list 'auto-mode-alist '(\"\\\\.${ext}\\\\'\" . xtc-mode))")
        }
        appendLine()
        
        appendLine("(provide 'xtc-mode)")
        appendLine()
        appendLine(";;; xtc-mode.el ends here")
    }
}
