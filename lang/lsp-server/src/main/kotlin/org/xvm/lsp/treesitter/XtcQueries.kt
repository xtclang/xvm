package org.xvm.lsp.treesitter

/**
 * Tree-sitter query patterns for XTC language constructs.
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
     * Combined query for all declarations (for document symbols).
     * Uses field-based matching for robust, position-independent queries.
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
        """.trimIndent()
}
