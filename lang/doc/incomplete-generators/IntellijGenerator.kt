package org.xtclang.tooling.generators

import org.xtclang.tooling.model.*

/**
 * Generates IntelliJ IDEA plugin components:
 * - JFlex lexer specification
 * - Token types
 * - Syntax highlighter
 * - Language registration
 */
class IntellijGenerator(private val model: LanguageModel) {
    
    private val packageName = "org.xtclang.intellij"
    private val languageName = model.name.lowercase()
    private val languageNameCap = model.name.replaceFirstChar { it.uppercase() }
    
    /**
     * Generate JFlex lexer specification
     */
    fun generateLexer(): String = buildString {
        appendLine("package $packageName.lexer;")
        appendLine()
        appendLine("import com.intellij.lexer.FlexLexer;")
        appendLine("import com.intellij.psi.tree.IElementType;")
        appendLine("import static $packageName.psi.${languageNameCap}Types.*;")
        appendLine()
        appendLine("%%")
        appendLine()
        appendLine("%class ${languageNameCap}Lexer")
        appendLine("%implements FlexLexer")
        appendLine("%unicode")
        appendLine("%function advance")
        appendLine("%type IElementType")
        appendLine()
        appendLine("%state STRING TEMPLATE_STRING BLOCK_COMMENT DOC_COMMENT")
        appendLine()
        appendLine("%{")
        appendLine("  private int commentDepth = 0;")
        appendLine("  private StringBuilder stringBuilder = new StringBuilder();")
        appendLine("%}")
        appendLine()
        
        // Macros
        appendLine("// Macros")
        appendLine("DIGIT = [0-9]")
        appendLine("HEX_DIGIT = [0-9a-fA-F]")
        appendLine("BIN_DIGIT = [01]")
        appendLine("LETTER = [a-zA-Z_]")
        appendLine("IDENTIFIER = {LETTER}({LETTER}|{DIGIT})*")
        appendLine("WHITESPACE = [ \\t\\r\\n]+")
        appendLine("LINE_TERMINATOR = \\r|\\n|\\r\\n")
        appendLine()
        appendLine("INT_LITERAL = {DIGIT}({DIGIT}|_)*")
        appendLine("HEX_LITERAL = 0[xX]{HEX_DIGIT}({HEX_DIGIT}|_)*")
        appendLine("BIN_LITERAL = 0[bB]{BIN_DIGIT}({BIN_DIGIT}|_)*")
        appendLine("FLOAT_LITERAL = {INT_LITERAL}\\.{INT_LITERAL}([eE][+-]?{INT_LITERAL})?")
        appendLine()
        appendLine("%%")
        appendLine()
        
        // Initial state rules
        appendLine("<YYINITIAL> {")
        appendLine()
        
        // Whitespace
        appendLine("  {WHITESPACE}           { return com.intellij.psi.TokenType.WHITE_SPACE; }")
        appendLine()
        
        // Comments
        appendLine("  // Comments")
        appendLine("  \"/**\"                  { yybegin(DOC_COMMENT); commentDepth = 1; return DOC_COMMENT_START; }")
        appendLine("  \"/*\"                   { yybegin(BLOCK_COMMENT); commentDepth = 1; return BLOCK_COMMENT_START; }")
        appendLine("  \"//\" [^\\r\\n]*         { return LINE_COMMENT; }")
        appendLine()
        
        // Keywords
        appendLine("  // Keywords")
        model.keywords.sorted().forEach { keyword ->
            val tokenName = keyword.uppercase()
            appendLine("  \"$keyword\"             { return $tokenName; }")
        }
        appendLine()
        
        // Literals
        appendLine("  // Literals")
        appendLine("  \"True\"                 { return TRUE; }")
        appendLine("  \"False\"                { return FALSE; }")
        appendLine("  \"Null\"                 { return NULL; }")
        appendLine()
        
        // Numbers
        appendLine("  // Numbers")
        appendLine("  {HEX_LITERAL}          { return HEX_LITERAL; }")
        appendLine("  {BIN_LITERAL}          { return BIN_LITERAL; }")
        appendLine("  {FLOAT_LITERAL}        { return FLOAT_LITERAL; }")
        appendLine("  {INT_LITERAL}          { return INT_LITERAL; }")
        appendLine()
        
        // Strings
        appendLine("  // Strings")
        appendLine("  \\$\"                    { yybegin(TEMPLATE_STRING); return TEMPLATE_STRING_START; }")
        appendLine("  \"                      { yybegin(STRING); return STRING_START; }")
        appendLine("  '([^'\\\\]|\\\\.)'      { return CHAR_LITERAL; }")
        appendLine()
        
        // Annotations
        appendLine("  // Annotations")
        appendLine("  @{IDENTIFIER}          { return ANNOTATION; }")
        appendLine()
        
        // Operators (sorted by length descending to match longest first)
        appendLine("  // Operators")
        val sortedOps = model.operators.sortedByDescending { it.symbol.length }
        sortedOps.forEach { op ->
            val escaped = escapeRegex(op.symbol)
            val tokenName = operatorTokenName(op.symbol)
            appendLine("  \"${op.symbol}\"${" ".repeat(20 - op.symbol.length)}{ return $tokenName; }")
        }
        appendLine()
        
        // Brackets and punctuation
        appendLine("  // Punctuation")
        appendLine("  \"(\"                    { return L_PAREN; }")
        appendLine("  \")\"                    { return R_PAREN; }")
        appendLine("  \"{\"                    { return L_BRACE; }")
        appendLine("  \"}\"                    { return R_BRACE; }")
        appendLine("  \"[\"                    { return L_BRACKET; }")
        appendLine("  \"]\"                    { return R_BRACKET; }")
        appendLine("  \"<\"                    { return L_ANGLE; }")
        appendLine("  \">\"                    { return R_ANGLE; }")
        appendLine("  \",\"                    { return COMMA; }")
        appendLine("  \";\"                    { return SEMICOLON; }")
        appendLine("  \":\"                    { return COLON; }")
        appendLine("  \"?\"                    { return QUESTION; }")
        appendLine()
        
        // Identifiers
        appendLine("  // Identifiers")
        appendLine("  {IDENTIFIER}           { return IDENTIFIER; }")
        appendLine()
        
        // Catch-all
        appendLine("  [^]                    { return com.intellij.psi.TokenType.BAD_CHARACTER; }")
        appendLine("}")
        appendLine()
        
        // String state
        appendLine("<STRING> {")
        appendLine("  \"                      { yybegin(YYINITIAL); return STRING_END; }")
        appendLine("  \\\\[ntrbf\\\\\"']       { return STRING_ESCAPE; }")
        appendLine("  \\\\u[0-9a-fA-F]{4}     { return STRING_ESCAPE; }")
        appendLine("  [^\"\\\\]+              { return STRING_CONTENT; }")
        appendLine("  \\\\.                   { return STRING_ESCAPE; }")
        appendLine("}")
        appendLine()
        
        // Template string state
        appendLine("<TEMPLATE_STRING> {")
        appendLine("  \"                      { yybegin(YYINITIAL); return TEMPLATE_STRING_END; }")
        appendLine("  \\\\[ntrbf\\\\\"']       { return STRING_ESCAPE; }")
        appendLine("  \"\\{\"                  { return TEMPLATE_EXPR_START; }")
        appendLine("  \"\\}\"                  { return TEMPLATE_EXPR_END; }")
        appendLine("  [^\"\\\\\\{\\}]+         { return STRING_CONTENT; }")
        appendLine("  \\\\.                   { return STRING_ESCAPE; }")
        appendLine("}")
        appendLine()
        
        // Block comment state
        appendLine("<BLOCK_COMMENT> {")
        appendLine("  \"/*\"                   { commentDepth++; return BLOCK_COMMENT_CONTENT; }")
        appendLine("  \"*/\"                   { if (--commentDepth == 0) { yybegin(YYINITIAL); return BLOCK_COMMENT_END; } return BLOCK_COMMENT_CONTENT; }")
        appendLine("  [^/*]+                 { return BLOCK_COMMENT_CONTENT; }")
        appendLine("  [/*]                   { return BLOCK_COMMENT_CONTENT; }")
        appendLine("}")
        appendLine()
        
        // Doc comment state
        appendLine("<DOC_COMMENT> {")
        appendLine("  \"/*\"                   { commentDepth++; return DOC_COMMENT_CONTENT; }")
        appendLine("  \"*/\"                   { if (--commentDepth == 0) { yybegin(YYINITIAL); return DOC_COMMENT_END; } return DOC_COMMENT_CONTENT; }")
        appendLine("  @[a-zA-Z]+             { return DOC_TAG; }")
        appendLine("  [^/*@]+                { return DOC_COMMENT_CONTENT; }")
        appendLine("  [/*@]                  { return DOC_COMMENT_CONTENT; }")
        appendLine("}")
    }
    
    /**
     * Generate token types enum
     */
    fun generateTokenTypes(): String = buildString {
        appendLine("package $packageName.psi;")
        appendLine()
        appendLine("import com.intellij.psi.tree.IElementType;")
        appendLine("import com.intellij.psi.tree.TokenSet;")
        appendLine("import $packageName.${languageNameCap}Language;")
        appendLine()
        appendLine("public interface ${languageNameCap}Types {")
        appendLine()
        
        // Keywords
        appendLine("    // Keywords")
        model.keywords.sorted().forEach { keyword ->
            appendLine("    IElementType ${keyword.uppercase()} = new ${languageNameCap}TokenType(\"${keyword.uppercase()}\");")
        }
        appendLine()
        
        // Literals
        appendLine("    // Literals")
        appendLine("    IElementType TRUE = new ${languageNameCap}TokenType(\"TRUE\");")
        appendLine("    IElementType FALSE = new ${languageNameCap}TokenType(\"FALSE\");")
        appendLine("    IElementType NULL = new ${languageNameCap}TokenType(\"NULL\");")
        appendLine("    IElementType INT_LITERAL = new ${languageNameCap}TokenType(\"INT_LITERAL\");")
        appendLine("    IElementType FLOAT_LITERAL = new ${languageNameCap}TokenType(\"FLOAT_LITERAL\");")
        appendLine("    IElementType HEX_LITERAL = new ${languageNameCap}TokenType(\"HEX_LITERAL\");")
        appendLine("    IElementType BIN_LITERAL = new ${languageNameCap}TokenType(\"BIN_LITERAL\");")
        appendLine("    IElementType CHAR_LITERAL = new ${languageNameCap}TokenType(\"CHAR_LITERAL\");")
        appendLine()
        
        // Strings
        appendLine("    // Strings")
        appendLine("    IElementType STRING_START = new ${languageNameCap}TokenType(\"STRING_START\");")
        appendLine("    IElementType STRING_END = new ${languageNameCap}TokenType(\"STRING_END\");")
        appendLine("    IElementType STRING_CONTENT = new ${languageNameCap}TokenType(\"STRING_CONTENT\");")
        appendLine("    IElementType STRING_ESCAPE = new ${languageNameCap}TokenType(\"STRING_ESCAPE\");")
        appendLine("    IElementType TEMPLATE_STRING_START = new ${languageNameCap}TokenType(\"TEMPLATE_STRING_START\");")
        appendLine("    IElementType TEMPLATE_STRING_END = new ${languageNameCap}TokenType(\"TEMPLATE_STRING_END\");")
        appendLine("    IElementType TEMPLATE_EXPR_START = new ${languageNameCap}TokenType(\"TEMPLATE_EXPR_START\");")
        appendLine("    IElementType TEMPLATE_EXPR_END = new ${languageNameCap}TokenType(\"TEMPLATE_EXPR_END\");")
        appendLine()
        
        // Comments
        appendLine("    // Comments")
        appendLine("    IElementType LINE_COMMENT = new ${languageNameCap}TokenType(\"LINE_COMMENT\");")
        appendLine("    IElementType BLOCK_COMMENT_START = new ${languageNameCap}TokenType(\"BLOCK_COMMENT_START\");")
        appendLine("    IElementType BLOCK_COMMENT_END = new ${languageNameCap}TokenType(\"BLOCK_COMMENT_END\");")
        appendLine("    IElementType BLOCK_COMMENT_CONTENT = new ${languageNameCap}TokenType(\"BLOCK_COMMENT_CONTENT\");")
        appendLine("    IElementType DOC_COMMENT_START = new ${languageNameCap}TokenType(\"DOC_COMMENT_START\");")
        appendLine("    IElementType DOC_COMMENT_END = new ${languageNameCap}TokenType(\"DOC_COMMENT_END\");")
        appendLine("    IElementType DOC_COMMENT_CONTENT = new ${languageNameCap}TokenType(\"DOC_COMMENT_CONTENT\");")
        appendLine("    IElementType DOC_TAG = new ${languageNameCap}TokenType(\"DOC_TAG\");")
        appendLine()
        
        // Operators
        appendLine("    // Operators")
        model.operators.forEach { op ->
            val tokenName = operatorTokenName(op.symbol)
            appendLine("    IElementType $tokenName = new ${languageNameCap}TokenType(\"$tokenName\");")
        }
        appendLine()
        
        // Punctuation
        appendLine("    // Punctuation")
        appendLine("    IElementType L_PAREN = new ${languageNameCap}TokenType(\"L_PAREN\");")
        appendLine("    IElementType R_PAREN = new ${languageNameCap}TokenType(\"R_PAREN\");")
        appendLine("    IElementType L_BRACE = new ${languageNameCap}TokenType(\"L_BRACE\");")
        appendLine("    IElementType R_BRACE = new ${languageNameCap}TokenType(\"R_BRACE\");")
        appendLine("    IElementType L_BRACKET = new ${languageNameCap}TokenType(\"L_BRACKET\");")
        appendLine("    IElementType R_BRACKET = new ${languageNameCap}TokenType(\"R_BRACKET\");")
        appendLine("    IElementType L_ANGLE = new ${languageNameCap}TokenType(\"L_ANGLE\");")
        appendLine("    IElementType R_ANGLE = new ${languageNameCap}TokenType(\"R_ANGLE\");")
        appendLine("    IElementType COMMA = new ${languageNameCap}TokenType(\"COMMA\");")
        appendLine("    IElementType SEMICOLON = new ${languageNameCap}TokenType(\"SEMICOLON\");")
        appendLine("    IElementType COLON = new ${languageNameCap}TokenType(\"COLON\");")
        appendLine("    IElementType QUESTION = new ${languageNameCap}TokenType(\"QUESTION\");")
        appendLine()
        
        // Other
        appendLine("    // Other")
        appendLine("    IElementType IDENTIFIER = new ${languageNameCap}TokenType(\"IDENTIFIER\");")
        appendLine("    IElementType ANNOTATION = new ${languageNameCap}TokenType(\"ANNOTATION\");")
        appendLine()
        
        // Token sets
        appendLine("    // Token Sets")
        appendLine("    TokenSet KEYWORDS = TokenSet.create(")
        val keywordList = model.keywords.sorted().map { "        ${it.uppercase()}" }.joinToString(",\n")
        appendLine(keywordList)
        appendLine("    );")
        appendLine()
        appendLine("    TokenSet COMMENTS = TokenSet.create(")
        appendLine("        LINE_COMMENT, BLOCK_COMMENT_START, BLOCK_COMMENT_END, BLOCK_COMMENT_CONTENT,")
        appendLine("        DOC_COMMENT_START, DOC_COMMENT_END, DOC_COMMENT_CONTENT, DOC_TAG")
        appendLine("    );")
        appendLine()
        appendLine("    TokenSet STRINGS = TokenSet.create(")
        appendLine("        STRING_START, STRING_END, STRING_CONTENT, STRING_ESCAPE,")
        appendLine("        TEMPLATE_STRING_START, TEMPLATE_STRING_END, TEMPLATE_EXPR_START, TEMPLATE_EXPR_END")
        appendLine("    );")
        appendLine()
        appendLine("    TokenSet NUMBERS = TokenSet.create(")
        appendLine("        INT_LITERAL, FLOAT_LITERAL, HEX_LITERAL, BIN_LITERAL")
        appendLine("    );")
        appendLine()
        appendLine("}")
    }
    
    /**
     * Generate syntax highlighter
     */
    fun generateSyntaxHighlighter(): String = buildString {
        appendLine("package $packageName.highlighting;")
        appendLine()
        appendLine("import com.intellij.lexer.Lexer;")
        appendLine("import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;")
        appendLine("import com.intellij.openapi.editor.HighlighterColors;")
        appendLine("import com.intellij.openapi.editor.colors.TextAttributesKey;")
        appendLine("import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;")
        appendLine("import com.intellij.psi.tree.IElementType;")
        appendLine("import $packageName.lexer.${languageNameCap}LexerAdapter;")
        appendLine("import $packageName.psi.${languageNameCap}Types;")
        appendLine("import org.jetbrains.annotations.NotNull;")
        appendLine()
        appendLine("import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;")
        appendLine()
        appendLine("public class ${languageNameCap}SyntaxHighlighter extends SyntaxHighlighterBase {")
        appendLine()
        
        // Text attribute keys
        appendLine("    // Text Attribute Keys")
        appendLine("    public static final TextAttributesKey KEYWORD =")
        appendLine("        createTextAttributesKey(\"XTC_KEYWORD\", DefaultLanguageHighlighterColors.KEYWORD);")
        appendLine("    public static final TextAttributesKey STRING =")
        appendLine("        createTextAttributesKey(\"XTC_STRING\", DefaultLanguageHighlighterColors.STRING);")
        appendLine("    public static final TextAttributesKey NUMBER =")
        appendLine("        createTextAttributesKey(\"XTC_NUMBER\", DefaultLanguageHighlighterColors.NUMBER);")
        appendLine("    public static final TextAttributesKey LINE_COMMENT =")
        appendLine("        createTextAttributesKey(\"XTC_LINE_COMMENT\", DefaultLanguageHighlighterColors.LINE_COMMENT);")
        appendLine("    public static final TextAttributesKey BLOCK_COMMENT =")
        appendLine("        createTextAttributesKey(\"XTC_BLOCK_COMMENT\", DefaultLanguageHighlighterColors.BLOCK_COMMENT);")
        appendLine("    public static final TextAttributesKey DOC_COMMENT =")
        appendLine("        createTextAttributesKey(\"XTC_DOC_COMMENT\", DefaultLanguageHighlighterColors.DOC_COMMENT);")
        appendLine("    public static final TextAttributesKey OPERATION_SIGN =")
        appendLine("        createTextAttributesKey(\"XTC_OPERATION_SIGN\", DefaultLanguageHighlighterColors.OPERATION_SIGN);")
        appendLine("    public static final TextAttributesKey PARENTHESES =")
        appendLine("        createTextAttributesKey(\"XTC_PARENTHESES\", DefaultLanguageHighlighterColors.PARENTHESES);")
        appendLine("    public static final TextAttributesKey BRACKETS =")
        appendLine("        createTextAttributesKey(\"XTC_BRACKETS\", DefaultLanguageHighlighterColors.BRACKETS);")
        appendLine("    public static final TextAttributesKey BRACES =")
        appendLine("        createTextAttributesKey(\"XTC_BRACES\", DefaultLanguageHighlighterColors.BRACES);")
        appendLine("    public static final TextAttributesKey IDENTIFIER =")
        appendLine("        createTextAttributesKey(\"XTC_IDENTIFIER\", DefaultLanguageHighlighterColors.IDENTIFIER);")
        appendLine("    public static final TextAttributesKey ANNOTATION =")
        appendLine("        createTextAttributesKey(\"XTC_ANNOTATION\", DefaultLanguageHighlighterColors.METADATA);")
        appendLine("    public static final TextAttributesKey BAD_CHARACTER =")
        appendLine("        createTextAttributesKey(\"XTC_BAD_CHARACTER\", HighlighterColors.BAD_CHARACTER);")
        appendLine()
        
        // Arrays for attributes
        appendLine("    private static final TextAttributesKey[] KEYWORD_KEYS = new TextAttributesKey[]{KEYWORD};")
        appendLine("    private static final TextAttributesKey[] STRING_KEYS = new TextAttributesKey[]{STRING};")
        appendLine("    private static final TextAttributesKey[] NUMBER_KEYS = new TextAttributesKey[]{NUMBER};")
        appendLine("    private static final TextAttributesKey[] LINE_COMMENT_KEYS = new TextAttributesKey[]{LINE_COMMENT};")
        appendLine("    private static final TextAttributesKey[] BLOCK_COMMENT_KEYS = new TextAttributesKey[]{BLOCK_COMMENT};")
        appendLine("    private static final TextAttributesKey[] DOC_COMMENT_KEYS = new TextAttributesKey[]{DOC_COMMENT};")
        appendLine("    private static final TextAttributesKey[] OPERATION_KEYS = new TextAttributesKey[]{OPERATION_SIGN};")
        appendLine("    private static final TextAttributesKey[] PAREN_KEYS = new TextAttributesKey[]{PARENTHESES};")
        appendLine("    private static final TextAttributesKey[] BRACKET_KEYS = new TextAttributesKey[]{BRACKETS};")
        appendLine("    private static final TextAttributesKey[] BRACE_KEYS = new TextAttributesKey[]{BRACES};")
        appendLine("    private static final TextAttributesKey[] IDENTIFIER_KEYS = new TextAttributesKey[]{IDENTIFIER};")
        appendLine("    private static final TextAttributesKey[] ANNOTATION_KEYS = new TextAttributesKey[]{ANNOTATION};")
        appendLine("    private static final TextAttributesKey[] BAD_CHAR_KEYS = new TextAttributesKey[]{BAD_CHARACTER};")
        appendLine("    private static final TextAttributesKey[] EMPTY_KEYS = new TextAttributesKey[0];")
        appendLine()
        
        // getLexer method
        appendLine("    @Override")
        appendLine("    public @NotNull Lexer getHighlightingLexer() {")
        appendLine("        return new ${languageNameCap}LexerAdapter();")
        appendLine("    }")
        appendLine()
        
        // getTokenHighlights method
        appendLine("    @Override")
        appendLine("    public TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {")
        appendLine("        // Keywords")
        appendLine("        if (${languageNameCap}Types.KEYWORDS.contains(tokenType)) {")
        appendLine("            return KEYWORD_KEYS;")
        appendLine("        }")
        appendLine()
        appendLine("        // Strings")
        appendLine("        if (${languageNameCap}Types.STRINGS.contains(tokenType)) {")
        appendLine("            return STRING_KEYS;")
        appendLine("        }")
        appendLine()
        appendLine("        // Numbers")
        appendLine("        if (${languageNameCap}Types.NUMBERS.contains(tokenType)) {")
        appendLine("            return NUMBER_KEYS;")
        appendLine("        }")
        appendLine()
        appendLine("        // Comments")
        appendLine("        if (tokenType == ${languageNameCap}Types.LINE_COMMENT) {")
        appendLine("            return LINE_COMMENT_KEYS;")
        appendLine("        }")
        appendLine("        if (tokenType == ${languageNameCap}Types.BLOCK_COMMENT_START ||")
        appendLine("            tokenType == ${languageNameCap}Types.BLOCK_COMMENT_END ||")
        appendLine("            tokenType == ${languageNameCap}Types.BLOCK_COMMENT_CONTENT) {")
        appendLine("            return BLOCK_COMMENT_KEYS;")
        appendLine("        }")
        appendLine("        if (tokenType == ${languageNameCap}Types.DOC_COMMENT_START ||")
        appendLine("            tokenType == ${languageNameCap}Types.DOC_COMMENT_END ||")
        appendLine("            tokenType == ${languageNameCap}Types.DOC_COMMENT_CONTENT ||")
        appendLine("            tokenType == ${languageNameCap}Types.DOC_TAG) {")
        appendLine("            return DOC_COMMENT_KEYS;")
        appendLine("        }")
        appendLine()
        appendLine("        // Punctuation")
        appendLine("        if (tokenType == ${languageNameCap}Types.L_PAREN ||")
        appendLine("            tokenType == ${languageNameCap}Types.R_PAREN) {")
        appendLine("            return PAREN_KEYS;")
        appendLine("        }")
        appendLine("        if (tokenType == ${languageNameCap}Types.L_BRACKET ||")
        appendLine("            tokenType == ${languageNameCap}Types.R_BRACKET) {")
        appendLine("            return BRACKET_KEYS;")
        appendLine("        }")
        appendLine("        if (tokenType == ${languageNameCap}Types.L_BRACE ||")
        appendLine("            tokenType == ${languageNameCap}Types.R_BRACE) {")
        appendLine("            return BRACE_KEYS;")
        appendLine("        }")
        appendLine()
        appendLine("        // Annotation")
        appendLine("        if (tokenType == ${languageNameCap}Types.ANNOTATION) {")
        appendLine("            return ANNOTATION_KEYS;")
        appendLine("        }")
        appendLine()
        appendLine("        // Identifier")
        appendLine("        if (tokenType == ${languageNameCap}Types.IDENTIFIER) {")
        appendLine("            return IDENTIFIER_KEYS;")
        appendLine("        }")
        appendLine()
        appendLine("        // Bad character")
        appendLine("        if (tokenType == com.intellij.psi.TokenType.BAD_CHARACTER) {")
        appendLine("            return BAD_CHAR_KEYS;")
        appendLine("        }")
        appendLine()
        appendLine("        return EMPTY_KEYS;")
        appendLine("    }")
        appendLine("}")
    }
    
    /**
     * Generate plugin.xml
     */
    fun generatePluginXml(): String = buildString {
        appendLine("""<idea-plugin>""")
        appendLine("""    <id>org.xtclang.intellij</id>""")
        appendLine("""    <name>${model.name} Language Support</name>""")
        appendLine("""    <vendor>xtclang.org</vendor>""")
        appendLine("""    <description><![CDATA[""")
        appendLine("""        Language support for the ${model.name} programming language.""")
        appendLine("""        <ul>""")
        appendLine("""            <li>Syntax highlighting</li>""")
        appendLine("""            <li>Code completion</li>""")
        appendLine("""            <li>Go to definition</li>""")
        appendLine("""            <li>Find usages</li>""")
        appendLine("""            <li>Code formatting</li>""")
        appendLine("""        </ul>""")
        appendLine("""    ]]></description>""")
        appendLine()
        appendLine("""    <depends>com.intellij.modules.platform</depends>""")
        appendLine()
        appendLine("""    <extensions defaultExtensionNs="com.intellij">""")
        appendLine("""        <fileType""")
        appendLine("""            name="${model.name} File"""")
        appendLine("""            implementationClass="$packageName.${languageNameCap}FileType"""")
        appendLine("""            fieldName="INSTANCE"""")
        appendLine("""            language="${languageName}"""")
        appendLine("""            extensions="${model.fileExtensions.joinToString(";")}"/>""")
        appendLine()
        appendLine("""        <lang.parserDefinition""")
        appendLine("""            language="${languageName}"""")
        appendLine("""            implementationClass="$packageName.parser.${languageNameCap}ParserDefinition"/>""")
        appendLine()
        appendLine("""        <lang.syntaxHighlighterFactory""")
        appendLine("""            language="${languageName}"""")
        appendLine("""            implementationClass="$packageName.highlighting.${languageNameCap}SyntaxHighlighterFactory"/>""")
        appendLine()
        appendLine("""        <colorSettingsPage""")
        appendLine("""            implementationClass="$packageName.highlighting.${languageNameCap}ColorSettingsPage"/>""")
        appendLine()
        appendLine("""        <lang.commenter""")
        appendLine("""            language="${languageName}"""")
        appendLine("""            implementationClass="$packageName.${languageNameCap}Commenter"/>""")
        appendLine()
        appendLine("""        <lang.braceMatcher""")
        appendLine("""            language="${languageName}"""")
        appendLine("""            implementationClass="$packageName.${languageNameCap}BraceMatcher"/>""")
        appendLine()
        appendLine("""        <completion.contributor""")
        appendLine("""            language="${languageName}"""")
        appendLine("""            implementationClass="$packageName.completion.${languageNameCap}CompletionContributor"/>""")
        appendLine("""    </extensions>""")
        appendLine("""</idea-plugin>""")
    }
    
    private fun operatorTokenName(symbol: String): String = when (symbol) {
        "=" -> "ASSIGN"
        "+=" -> "PLUS_ASSIGN"
        "-=" -> "MINUS_ASSIGN"
        "*=" -> "MUL_ASSIGN"
        "/=" -> "DIV_ASSIGN"
        "%=" -> "MOD_ASSIGN"
        "&=" -> "AND_ASSIGN"
        "|=" -> "OR_ASSIGN"
        "^=" -> "XOR_ASSIGN"
        "<<=" -> "SHL_ASSIGN"
        ">>=" -> "SHR_ASSIGN"
        ">>>=" -> "USHR_ASSIGN"
        ":=" -> "COND_ASSIGN"
        "?=" -> "ELVIS_ASSIGN"
        "?:" -> "ELVIS"
        "||" -> "OR"
        "&&" -> "AND"
        "^^" -> "XOR"
        "|" -> "BIT_OR"
        "^" -> "BIT_XOR"
        "&" -> "BIT_AND"
        "==" -> "EQ"
        "!=" -> "NE"
        "<" -> "LT"
        "<=" -> "LE"
        ">" -> "GT"
        ">=" -> "GE"
        "<=>" -> "SPACESHIP"
        ".." -> "RANGE"
        "..<" -> "RANGE_EXCL"
        "<<" -> "SHL"
        ">>" -> "SHR"
        ">>>" -> "USHR"
        "+" -> "PLUS"
        "-" -> "MINUS"
        "*" -> "MUL"
        "/" -> "DIV"
        "%" -> "MOD"
        "/%" -> "DIVMOD"
        "!" -> "NOT"
        "~" -> "BIT_NOT"
        "++" -> "INC"
        "--" -> "DEC"
        "." -> "DOT"
        "?." -> "SAFE_DOT"
        else -> symbol.uppercase().replace(Regex("[^A-Z0-9]"), "_")
    }
    
    private fun escapeRegex(s: String): String =
        s.replace("\\", "\\\\")
         .replace(".", "\\.")
         .replace("+", "\\+")
         .replace("*", "\\*")
         .replace("?", "\\?")
         .replace("|", "\\|")
         .replace("^", "\\^")
         .replace("$", "\\$")
         .replace("[", "\\[")
         .replace("]", "\\]")
         .replace("(", "\\(")
         .replace(")", "\\)")
}
