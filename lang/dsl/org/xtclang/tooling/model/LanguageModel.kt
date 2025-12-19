/**
 * DSL Framework for defining language models.
 *
 * This framework provides a type-safe Kotlin DSL for defining language specifications
 * that can be used to generate IDE support (TextMate grammars, IntelliJ lexers, etc.),
 * LSP semantic tokens, and other language tooling artifacts.
 *
 * Usage:
 * ```kotlin
 * val myLanguage = language(
 *     name = "MyLang",
 *     fileExtensions = listOf("ml"),
 *     scopeName = "source.mylang"
 * ) {
 *     keywords("if", "else", "while")
 *     scope("keyword") {
 *         textMate = "keyword.control.mylang"
 *         intellij = "KEYWORD"
 *     }
 *     concept("IfStatement") {
 *         extends("Statement")
 *         child("condition", "Expression")
 *     }
 * }
 * ```
 */
package org.xtclang.tooling.model

import kotlinx.serialization.Serializable

// =============================================================================
// ENUMS
// =============================================================================

/**
 * Operator associativity - how operators of the same precedence bind.
 */
enum class Associativity {
    /** Left-to-right: a op b op c = (a op b) op c */
    LEFT,
    /** Right-to-left: a op b op c = a op (b op c) */
    RIGHT,
    /** Cannot be chained */
    NONE
}

/**
 * Operator category for semantic grouping and styling.
 */
enum class OperatorCategory {
    /** Assignment operators: =, +=, -=, etc. */
    ASSIGNMENT,
    /** Arithmetic operators: +, -, *, /, % */
    ARITHMETIC,
    /** Comparison operators: ==, !=, <, >, <=, >= */
    COMPARISON,
    /** Logical operators: &&, ||, !, ^^ */
    LOGICAL,
    /** Bitwise operators: &, |, ^, ~, <<, >> */
    BITWISE,
    /** Member access operators: ., ?. */
    MEMBER_ACCESS,
    /** Other operators: ->, ?:, .., etc. */
    OTHER
}

/**
 * Keyword category for semantic grouping and styling.
 */
enum class KeywordCategory {
    /** Control flow: if, else, for, while, switch, case, break, continue, return, etc. */
    CONTROL,
    /** Declarations: class, interface, module, package, import, etc. */
    DECLARATION,
    /** Modifiers: public, private, static, abstract, final, etc. */
    MODIFIER,
    /** Type-related: extends, implements, incorporates, into */
    TYPE_RELATION,
    /** Exception handling: try, catch, finally, throw */
    EXCEPTION,
    /** Other: new, this, super, is, as, etc. */
    OTHER
}

/**
 * Cardinality for AST node children.
 */
enum class Cardinality {
    /** Exactly one child, must be present */
    REQUIRED,
    /** Zero or one child */
    OPTIONAL,
    /** Zero or more children */
    ZERO_OR_MORE
}

// =============================================================================
// DATA CLASSES - The model types
// =============================================================================

/**
 * Keyword definition with category for semantic grouping.
 */
@Serializable
data class KeywordDefinition(
    val word: String,
    val category: KeywordCategory,
    /** True for reserved keywords, false for context-sensitive */
    val reserved: Boolean
)

/**
 * Complete language model containing all definitions.
 */
@Serializable
data class LanguageModel(
    val name: String,
    val fileExtensions: List<String>,
    val scopeName: String,
    val scopes: List<ScopeDefinition>,
    val keywords: List<String>,
    val contextKeywords: List<String>,
    val categorizedKeywords: List<KeywordDefinition>,
    val builtinTypes: List<String>,
    val tokens: List<TokenRule>,
    val operators: List<OperatorDefinition>,
    val punctuation: List<PunctuationDefinition>,
    val concepts: List<ConceptDefinition>
) {
    /**
     * Get all keywords (reserved + context-sensitive).
     */
    val allKeywords: List<String>
        get() = keywords + contextKeywords

    /**
     * Get keywords by category.
     */
    fun keywordsByCategory(category: KeywordCategory): List<String> =
        categorizedKeywords.filter { it.category == category }.map { it.word }

    /**
     * Get a concept by name.
     */
    fun getConcept(name: String): ConceptDefinition? =
        concepts.find { it.name == name }

    /**
     * Get all concrete (non-abstract) concepts.
     */
    val concreteConcepts: List<ConceptDefinition>
        get() = concepts.filter { !it.isAbstract }

    /**
     * Get all abstract concepts.
     */
    val abstractConcepts: List<ConceptDefinition>
        get() = concepts.filter { it.isAbstract }

    /**
     * Get operators sorted by precedence (lowest to highest).
     */
    val operatorsByPrecedence: List<OperatorDefinition>
        get() = operators.sortedBy { it.precedence }
}

/**
 * Scope definition mapping a semantic element to editor-specific styling.
 *
 * Each field maps the semantic concept to the editor's specific naming scheme:
 * - textMate: TextMate scope (e.g., "keyword.control.xtc") - used by VS Code, Sublime, etc.
 * - intellij: TextAttributesKey (e.g., "KEYWORD") - used by IntelliJ IDEA
 * - eclipse: Color key (e.g., "keyword") - used by Eclipse
 * - semanticToken: LSP semantic token type (e.g., "keyword") - used by LSP clients
 * - vim: Highlight group (e.g., "Keyword") - used by Vim/Neovim
 * - emacs: Face name (e.g., "font-lock-keyword-face") - used by Emacs
 * - treeSitter: Capture name (e.g., "@keyword") - used by Tree-sitter (Zed, Helix, GitHub)
 */
@Serializable
data class ScopeDefinition(
    val name: String,
    val textMate: String,
    val intellij: String,
    val eclipse: String,
    val semanticToken: String?,
    val vim: String?,
    val emacs: String?,
    val treeSitter: String?
)

/**
 * Token rule for lexical analysis.
 */
@Serializable
data class TokenRule(
    val name: String,
    /** Regex pattern to match */
    val pattern: String,
    /** TextMate scope for syntax highlighting */
    val textMateScope: String
)

/**
 * Operator definition with precedence and associativity.
 */
@Serializable
data class OperatorDefinition(
    val symbol: String,
    /** Binding strength: 1 = lowest (binds last), 15 = highest (binds first) */
    val precedence: Int,
    val associativity: Associativity,
    val category: OperatorCategory
)

/**
 * Punctuation/delimiter definition.
 */
@Serializable
data class PunctuationDefinition(
    val symbol: String,
    /** Token name for generated code (e.g., "COLON", "L_PAREN") */
    val name: String
)

/**
 * AST concept definition (represents a node type in the abstract syntax tree).
 */
@Serializable
data class ConceptDefinition(
    val name: String,
    val isAbstract: Boolean,
    val parentConcept: String?,
    val properties: List<PropertyDefinition>,
    val children: List<ChildDefinition>,
    val references: List<ReferenceDefinition>,
    /** Optional regex pattern for grammar generation */
    val syntaxPattern: String?
) {
    /**
     * Check if this concept extends another (directly or indirectly).
     */
    fun extendsFrom(conceptName: String, model: LanguageModel): Boolean {
        if (parentConcept == conceptName) return true
        val parent = parentConcept?.let { model.getConcept(it) }
        return parent?.extendsFrom(conceptName, model) == true
    }
}

/**
 * Scalar property of a concept.
 */
@Serializable
data class PropertyDefinition(
    val name: String,
    val type: String,
    val default: String?,
    val optional: Boolean
)

/**
 * Child node of a concept.
 */
@Serializable
data class ChildDefinition(
    val name: String,
    val type: String,
    val cardinality: Cardinality
)

/**
 * Reference to another node.
 */
@Serializable
data class ReferenceDefinition(
    val name: String,
    val type: String,
    val optional: Boolean
)

// =============================================================================
// BUILDER CLASSES - DSL implementation
// =============================================================================

/**
 * Builder for scope definitions.
 */
class ScopeBuilder(private val name: String) {
    /** TextMate scope name (e.g., "keyword.control.xtc") */
    var textMate: String = ""
    /** IntelliJ TextAttributesKey name (e.g., "KEYWORD") */
    var intellij: String = ""
    /** Eclipse color key (e.g., "keyword") */
    var eclipse: String = ""
    /** LSP semantic token type (e.g., "keyword") */
    var semanticToken: String? = null
    /** Vim highlight group (e.g., "Keyword") */
    var vim: String? = null
    /** Emacs face name (e.g., "font-lock-keyword-face") */
    var emacs: String? = null
    /** Tree-sitter capture name (e.g., "@keyword") */
    var treeSitter: String? = null

    internal fun build(): ScopeDefinition = ScopeDefinition(
        name = name,
        textMate = textMate,
        intellij = intellij,
        eclipse = eclipse,
        semanticToken = semanticToken,
        vim = vim,
        emacs = emacs,
        treeSitter = treeSitter
    )
}

/**
 * Builder for concept definitions.
 */
class ConceptBuilder(private val name: String, private val isAbstract: Boolean) {
    private var parentConcept: String? = null
    private val properties = mutableListOf<PropertyDefinition>()
    private val children = mutableListOf<ChildDefinition>()
    private val references = mutableListOf<ReferenceDefinition>()
    private var syntaxPattern: String? = null

    /**
     * Inherit from another concept.
     */
    fun extends(parentConcept: String) {
        this.parentConcept = parentConcept
    }

    /**
     * Add a scalar property.
     */
    fun property(
        name: String,
        type: String,
        default: String? = null,
        optional: Boolean = false
    ) {
        properties.add(PropertyDefinition(name, type, default, optional))
    }

    /**
     * Add a single child node.
     */
    fun child(
        name: String,
        type: String,
        cardinality: Cardinality = Cardinality.REQUIRED
    ) {
        children.add(ChildDefinition(name, type, cardinality))
    }

    /**
     * Add a collection of child nodes (zero or more).
     */
    fun children(name: String, type: String) {
        children.add(ChildDefinition(name, type, Cardinality.ZERO_OR_MORE))
    }

    /**
     * Add a reference to another node.
     */
    fun reference(name: String, type: String, optional: Boolean = false) {
        references.add(ReferenceDefinition(name, type, optional))
    }

    /**
     * Set a regex pattern for grammar generation.
     */
    fun syntax(pattern: String) {
        syntaxPattern = pattern
    }

    internal fun build(): ConceptDefinition = ConceptDefinition(
        name = name,
        isAbstract = isAbstract,
        parentConcept = parentConcept,
        properties = properties.toList(),
        children = children.toList(),
        references = references.toList(),
        syntaxPattern = syntaxPattern
    )
}

/**
 * Main builder for language models.
 */
class LanguageModelBuilder(
    private val name: String,
    private val fileExtensions: List<String>,
    private val scopeName: String
) {
    private val scopes = mutableListOf<ScopeDefinition>()
    private val keywordsList = mutableListOf<String>()
    private val contextKeywordsList = mutableListOf<String>()
    private val categorizedKeywordsList = mutableListOf<KeywordDefinition>()
    private val builtinTypesList = mutableListOf<String>()
    private val tokens = mutableListOf<TokenRule>()
    private val operators = mutableListOf<OperatorDefinition>()
    private val punctuationList = mutableListOf<PunctuationDefinition>()
    private val concepts = mutableListOf<ConceptDefinition>()

    /**
     * Define a scope mapping for editor styling.
     */
    fun scope(name: String, block: ScopeBuilder.() -> Unit) {
        val builder = ScopeBuilder(name)
        builder.block()
        scopes.add(builder.build())
    }

    /**
     * Register reserved keywords (backward compatible, no category).
     */
    fun keywords(vararg words: String) {
        keywordsList.addAll(words)
    }

    /**
     * Register reserved keywords with a category.
     */
    fun keywords(category: KeywordCategory, vararg words: String) {
        keywordsList.addAll(words)
        words.forEach { word ->
            categorizedKeywordsList.add(KeywordDefinition(word, category, reserved = true))
        }
    }

    /**
     * Register context-sensitive keywords (backward compatible, no category).
     */
    fun contextKeywords(vararg words: String) {
        contextKeywordsList.addAll(words)
    }

    /**
     * Register context-sensitive keywords with a category.
     */
    fun contextKeywords(category: KeywordCategory, vararg words: String) {
        contextKeywordsList.addAll(words)
        words.forEach { word ->
            categorizedKeywordsList.add(KeywordDefinition(word, category, reserved = false))
        }
    }

    /**
     * Register built-in types.
     */
    fun builtinTypes(vararg types: String) {
        builtinTypesList.addAll(types)
    }

    /**
     * Define a lexical token rule.
     */
    fun token(name: String, pattern: String, textMateScope: String) {
        tokens.add(TokenRule(name, pattern, textMateScope))
    }

    /**
     * Define an operator with precedence information.
     */
    fun operator(
        symbol: String,
        precedence: Int,
        associativity: Associativity,
        category: OperatorCategory
    ) {
        operators.add(OperatorDefinition(symbol, precedence, associativity, category))
    }

    /**
     * Define a punctuation/delimiter token.
     */
    fun punctuation(symbol: String, name: String) {
        punctuationList.add(PunctuationDefinition(symbol, name))
    }

    /**
     * Define a concrete AST concept.
     */
    fun concept(name: String, block: ConceptBuilder.() -> Unit = {}) {
        val builder = ConceptBuilder(name, isAbstract = false)
        builder.block()
        concepts.add(builder.build())
    }

    /**
     * Define an abstract AST concept (cannot be instantiated).
     */
    fun abstractConcept(name: String, block: ConceptBuilder.() -> Unit = {}) {
        val builder = ConceptBuilder(name, isAbstract = true)
        builder.block()
        concepts.add(builder.build())
    }

    internal fun build(): LanguageModel = LanguageModel(
        name = name,
        fileExtensions = fileExtensions,
        scopeName = scopeName,
        scopes = scopes.toList(),
        keywords = keywordsList.toList(),
        contextKeywords = contextKeywordsList.toList(),
        categorizedKeywords = categorizedKeywordsList.toList(),
        builtinTypes = builtinTypesList.toList(),
        tokens = tokens.toList(),
        operators = operators.toList(),
        punctuation = punctuationList.toList(),
        concepts = concepts.toList()
    )
}

// =============================================================================
// ENTRY POINT FUNCTION
// =============================================================================

/**
 * Create a new language model using the DSL.
 *
 * @param name The display name of the language (e.g., "Ecstasy")
 * @param fileExtensions List of file extensions (e.g., ["x", "xtc"])
 * @param scopeName TextMate scope name (e.g., "source.xtc")
 * @param block DSL configuration block
 * @return The constructed LanguageModel
 */
fun language(
    name: String,
    fileExtensions: List<String>,
    scopeName: String,
    block: LanguageModelBuilder.() -> Unit
): LanguageModel {
    val builder = LanguageModelBuilder(name, fileExtensions, scopeName)
    builder.block()
    return builder.build()
}
