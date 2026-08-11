package org.xvm.lsp.treesitter

/**
 * Tree-sitter query patterns for Ecstasy language constructs.
 *
 * These S-expression queries match specific patterns in the syntax tree,
 * enabling extraction of declarations, references, and other language elements.
 *
 * The XTC grammar defines field names (via field() in grammar.js), enabling
 * field-based query syntax like `(class_declaration name: (type_name) @name)`.
 * This is more robust than positional matching and aligns with tree-sitter best practices.
 */
internal object XtcQueries {
    /**
     * Find all method declarations.
     * Uses field-based matching on name and parameters fields.
     */
    val methodDeclarations =
        """
        (method_declaration
            name: (identifier) @name
            parameters: (parameters) @params
        ) @declaration
        """.trimIndent()

    /**
     * Find all identifiers (for reference finding).
     */
    val identifiers =
        """
        (identifier) @id
        """.trimIndent()

    /**
     * Find import statements.
     */
    val imports =
        """
        (import_statement
            path: (qualified_name) @import
        )
        """.trimIndent()

    /**
     * Find comment and string-literal nodes -- the host nodes for free-text content
     * (URLs, file paths, etc.) that may need document links.
     */
    val commentsAndStrings =
        """
        (line_comment) @text
        (block_comment) @text
        (doc_comment) @text
        (string_literal) @text
        (template_string_literal) @text
        """.trimIndent()

    /**
     * Combined query for all declarations (for document symbols).
     * Uses field-based matching for robust, position-independent queries.
     *
     * Typedefs are declarations too (issue #459: cmd-click on `JsonArray` found
     * nothing because typedefs were absent here and thus from the workspace index).
     *
     * Shorthand constructor parameters of class-like declarations (`const
     * Point(Int x, Int y)`) declare properties in Ecstasy, so each parameter
     * name is captured as a @property. Issue #459: go-to-declaration on
     * `structure.y` jumped to an unrelated `y` in doc/archive because the
     * shorthand-declared `y` was invisible to the same-file lookup.
     */
    val allDeclarations =
        """
        (module_declaration name: (qualified_name) @name) @module
        (package_declaration name: (identifier) @name) @package
        (class_declaration name: (type_name) @name) @class
        (interface_declaration name: (type_name) @name) @interface
        (mixin_declaration name: (type_name) @name) @mixin
        (service_declaration name: (type_name) @name) @service
        (const_declaration name: (type_name) @name) @const
        (enum_declaration name: (type_name) @name) @enum
        (method_declaration name: (identifier) @name) @method
        (constructor_declaration) @constructor
        (property_declaration name: (identifier) @name) @property
        (typedef_declaration name: (identifier) @name) @typedef
        (class_declaration (constructor_parameters (parameters (parameter name: (identifier) @name)))) @property
        (mixin_declaration (constructor_parameters (parameters (parameter name: (identifier) @name)))) @property
        (service_declaration (constructor_parameters (parameters (parameter name: (identifier) @name)))) @property
        (const_declaration (constructor_parameters (parameters (parameter name: (identifier) @name)))) @property
        (enum_declaration (constructor_parameters (parameters (parameter name: (identifier) @name)))) @property
        (annotation_declaration (constructor_parameters (parameters (parameter name: (identifier) @name)))) @property
        """.trimIndent()
}
