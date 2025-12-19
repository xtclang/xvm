/**
 * Monaco Editor Language Definition for Ecstasy (XTC)
 * Generated from XTC language model
 * 
 * Use this in web-based editors like:
 * - VS Code for Web
 * - GitHub Codespaces
 * - StackBlitz
 * - CodeSandbox
 * - Custom web IDEs
 */

import * as monaco from 'monaco-editor';

// =============================================================================
// Language Registration
// =============================================================================

monaco.languages.register({
    id: 'xtc',
    extensions: ['.x', '.xtc'],
    aliases: ['Ecstasy', 'XTC', 'xtc'],
    mimetypes: ['text/x-xtc']
});

// =============================================================================
// Language Configuration
// =============================================================================

monaco.languages.setLanguageConfiguration('xtc', {
    comments: {
        lineComment: '//',
        blockComment: ['/*', '*/']
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
        { open: '/*', close: ' */', notIn: ['string'] }
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
        offSide: false,
        markers: {
            start: /^\s*\/\/\s*#?region\b/,
            end: /^\s*\/\/\s*#?endregion\b/
        }
    },
    indentationRules: {
        increaseIndentPattern: /^.*\{[^}"']*$|^.*\([^)"']*$/,
        decreaseIndentPattern: /^\s*[\}\)]/
    },
    wordPattern: /(-?\d*\.\d\w*)|([^\`\~\!\@\#\%\^\&\*\(\)\-\=\+\[\{\]\}\\\|\;\:\'\"\,\.\<\>\/\?\s]+)/g
});

// =============================================================================
// Monarch Tokenizer (Syntax Highlighting)
// =============================================================================

monaco.languages.setMonarchTokensProvider('xtc', {
    
    // Default token
    defaultToken: 'invalid',
    
    // Token postfix
    tokenPostfix: '.xtc',
    
    // Keywords
    keywords: [
        'if', 'else', 'switch', 'case', 'default',
        'for', 'while', 'do', 'foreach',
        'break', 'continue', 'return',
        'try', 'catch', 'finally', 'throw', 'using',
        'assert', 'new', 'this', 'super', 'outer',
        'is', 'as', 'void'
    ],
    
    // Declaration keywords
    declarationKeywords: [
        'module', 'package', 'class', 'interface', 'mixin', 'service',
        'const', 'enum', 'typedef', 'import', 'construct'
    ],
    
    // Modifier keywords
    modifierKeywords: [
        'public', 'protected', 'private', 'static',
        'abstract', 'final', 'immutable',
        'extends', 'implements', 'incorporates', 'into',
        'inject', 'conditional'
    ],
    
    // Type keywords
    typeKeywords: [
        'Bit', 'Boolean', 'Byte', 'Char', 'Dec', 'Float',
        'Int', 'Int8', 'Int16', 'Int32', 'Int64', 'Int128', 'IntN',
        'UInt', 'UInt8', 'UInt16', 'UInt32', 'UInt64', 'UInt128', 'UIntN',
        'String', 'Object', 'Void', 'Array', 'List', 'Map', 'Set',
        'Tuple', 'Function', 'Type', 'Class', 'Enum', 'Exception',
        'Iterator', 'Iterable', 'Collection', 'Sequence', 'Range', 'Interval'
    ],
    
    // Operators
    operators: [
        '=', '>', '<', '!', '~', '?', ':',
        '==', '<=', '>=', '!=', '<=>',
        '&&', '||', '^^', '++', '--',
        '+', '-', '*', '/', '&', '|', '^', '%',
        '<<', '>>', '>>>',
        '+=', '-=', '*=', '/=', '&=', '|=', '^=',
        '%=', '<<=', '>>=', '>>>=', ':=', '?=',
        '..', '..<', '?.', '?:'
    ],
    
    // Symbols for operators
    symbols: /[=><!~?:&|+\-*\/\^%]+/,
    
    // Escape sequences
    escapes: /\\(?:[abfnrtv\\"']|x[0-9A-Fa-f]{1,4}|u[0-9A-Fa-f]{4}|U[0-9A-Fa-f]{8})/,
    
    // Digits
    digits: /\d+(_+\d+)*/,
    
    // Tokenizer rules
    tokenizer: {
        root: [
            // Identifiers and keywords
            [/[a-z_$][\w$]*/, {
                cases: {
                    '@keywords': 'keyword',
                    '@declarationKeywords': 'keyword.declaration',
                    '@modifierKeywords': 'keyword.modifier',
                    '@default': 'identifier'
                }
            }],
            
            // Type identifiers (PascalCase)
            [/[A-Z][\w$]*/, {
                cases: {
                    '@typeKeywords': 'type.identifier',
                    'True|False': 'constant.language',
                    'Null': 'constant.language',
                    '@default': 'type.identifier'
                }
            }],
            
            // Whitespace
            { include: '@whitespace' },
            
            // Annotations
            [/@[A-Za-z_][\w]*/, 'annotation'],
            
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
            [/(@digits)[eE]([\-+]?(@digits))?/, 'number.float'],
            [/(@digits)\.(@digits)([eE][\-+]?(@digits))?/, 'number.float'],
            [/0[xX][0-9a-fA-F][0-9a-fA-F_]*/, 'number.hex'],
            [/0[bB][01][01_]*/, 'number.binary'],
            [/(@digits)/, 'number'],
            
            // Delimiter
            [/[;,.]/, 'delimiter'],
            
            // Template strings
            [/\$"/, { token: 'string.quote', bracket: '@open', next: '@templatestring' }],
            
            // Strings
            [/"([^"\\]|\\.)*$/, 'string.invalid'],  // non-terminated string
            [/"/, { token: 'string.quote', bracket: '@open', next: '@string' }],
            
            // Characters
            [/'[^\\']'/, 'string'],
            [/(')(@escapes)(')/, ['string', 'string.escape', 'string']],
            [/'/, 'string.invalid']
        ],
        
        comment: [
            [/[^\/*]+/, 'comment'],
            [/\/\*/, 'comment', '@push'],  // Nested comment
            ['\\*/', 'comment', '@pop'],
            [/[\/*]/, 'comment']
        ],
        
        doccomment: [
            [/[^\/*]+/, 'comment.doc'],
            [/@\w+/, 'comment.doc.tag'],
            [/\/\*/, 'comment.doc', '@push'],
            ['\\*/', 'comment.doc', '@pop'],
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
            [/\/\/.*$/, 'comment']
        ]
    }
});

// =============================================================================
// Completion Provider (Basic)
// =============================================================================

monaco.languages.registerCompletionItemProvider('xtc', {
    provideCompletionItems: (model, position) => {
        const word = model.getWordUntilPosition(position);
        const range = {
            startLineNumber: position.lineNumber,
            endLineNumber: position.lineNumber,
            startColumn: word.startColumn,
            endColumn: word.endColumn
        };
        
        const suggestions = [
            // Keywords
            ...['if', 'else', 'for', 'while', 'return', 'try', 'catch', 'throw', 'assert'].map(kw => ({
                label: kw,
                kind: monaco.languages.CompletionItemKind.Keyword,
                insertText: kw,
                range
            })),
            
            // Declaration keywords
            ...['class', 'interface', 'module', 'service', 'mixin', 'enum', 'const'].map(kw => ({
                label: kw,
                kind: monaco.languages.CompletionItemKind.Keyword,
                insertText: kw,
                range
            })),
            
            // Types
            ...['Int', 'String', 'Boolean', 'Array', 'Map', 'List', 'Set'].map(t => ({
                label: t,
                kind: monaco.languages.CompletionItemKind.Class,
                insertText: t,
                range
            })),
            
            // Snippets
            {
                label: 'module',
                kind: monaco.languages.CompletionItemKind.Snippet,
                insertText: 'module ${1:ModuleName} {\n\t$0\n}',
                insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
                documentation: 'Create a new module',
                range
            },
            {
                label: 'class',
                kind: monaco.languages.CompletionItemKind.Snippet,
                insertText: 'class ${1:ClassName} {\n\t$0\n}',
                insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
                documentation: 'Create a new class',
                range
            },
            {
                label: 'service',
                kind: monaco.languages.CompletionItemKind.Snippet,
                insertText: 'service ${1:ServiceName} {\n\t$0\n}',
                insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
                documentation: 'Create a new service',
                range
            },
            {
                label: 'ifelse',
                kind: monaco.languages.CompletionItemKind.Snippet,
                insertText: 'if (${1:condition}) {\n\t$2\n} else {\n\t$0\n}',
                insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
                documentation: 'If-else statement',
                range
            },
            {
                label: 'for',
                kind: monaco.languages.CompletionItemKind.Snippet,
                insertText: 'for (Int ${1:i} = 0; $1 < ${2:count}; $1++) {\n\t$0\n}',
                insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
                documentation: 'For loop',
                range
            },
            {
                label: 'foreach',
                kind: monaco.languages.CompletionItemKind.Snippet,
                insertText: 'for (${1:Type} ${2:item} : ${3:collection}) {\n\t$0\n}',
                insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
                documentation: 'For-each loop',
                range
            },
            {
                label: 'trycatch',
                kind: monaco.languages.CompletionItemKind.Snippet,
                insertText: 'try {\n\t$1\n} catch (${2:Exception} ${3:e}) {\n\t$0\n}',
                insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
                documentation: 'Try-catch block',
                range
            },
            {
                label: 'inject',
                kind: monaco.languages.CompletionItemKind.Snippet,
                insertText: '@Inject ${1:Type} ${2:name};',
                insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
                documentation: 'Inject dependency',
                range
            }
        ];
        
        return { suggestions };
    }
});

// =============================================================================
// Hover Provider
// =============================================================================

monaco.languages.registerHoverProvider('xtc', {
    provideHover: (model, position) => {
        const word = model.getWordAtPosition(position);
        if (!word) return null;
        
        const hoverInfo: { [key: string]: string } = {
            'module': 'Declares a module - the top-level organizational unit in Ecstasy.',
            'class': 'Declares a class - a template for creating objects.',
            'interface': 'Declares an interface - a contract that classes can implement.',
            'mixin': 'Declares a mixin - reusable functionality that can be incorporated.',
            'service': 'Declares a service - an asynchronous processing unit with isolated state.',
            'const': 'Declares a const class - an immutable value class.',
            'enum': 'Declares an enumeration - a fixed set of named values.',
            'assert': 'Validates that a condition is true.',
            '@Inject': 'Injects a dependency from the container.',
            'immutable': 'Marks a type or value as deeply immutable.'
        };
        
        const info = hoverInfo[word.word];
        if (info) {
            return {
                range: new monaco.Range(
                    position.lineNumber,
                    word.startColumn,
                    position.lineNumber,
                    word.endColumn
                ),
                contents: [
                    { value: `**${word.word}**` },
                    { value: info }
                ]
            };
        }
        return null;
    }
});

export default 'xtc';
