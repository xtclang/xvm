package org.xvm.lsp.treesitter

/**
 * Tree-sitter query patterns for XTC language constructs.
 *
 * These S-expression queries match specific patterns in the syntax tree,
 * enabling extraction of declarations, references, and other language elements.
 */
object XtcQueries {
    /**
     * Find all type declarations (classes, interfaces, mixins, services, consts, enums).
     */
    val TYPE_DECLARATIONS =
        """
        (class_declaration name: (type_name) @name) @declaration
        (interface_declaration name: (type_name) @name) @declaration
        (mixin_declaration name: (type_name) @name) @declaration
        (service_declaration name: (type_name) @name) @declaration
        (const_declaration name: (type_name) @name) @declaration
        (enum_declaration name: (type_name) @name) @declaration
        """.trimIndent()

    /**
     * Find all method declarations.
     */
    val METHOD_DECLARATIONS =
        """
        (method_declaration
            name: (identifier) @name
            parameters: (parameters) @params
        ) @declaration
        """.trimIndent()

    /**
     * Find all constructor declarations.
     */
    val CONSTRUCTOR_DECLARATIONS =
        """
        (constructor_declaration
            parameters: (parameters) @params
        ) @declaration
        """.trimIndent()

    /**
     * Find all property declarations.
     */
    val PROPERTY_DECLARATIONS =
        """
        (property_declaration
            type: (type_expression) @type
            name: (identifier) @name
        ) @declaration
        """.trimIndent()

    /**
     * Find all variable declarations.
     */
    val VARIABLE_DECLARATIONS =
        """
        (variable_declaration
            name: (identifier) @name
        ) @declaration
        """.trimIndent()

    /**
     * Find all parameter declarations.
     */
    val PARAMETER_DECLARATIONS =
        """
        (parameter
            type: (type_expression) @type
            name: (identifier) @name
        ) @declaration
        """.trimIndent()

    /**
     * Find all identifiers (for reference finding).
     */
    val IDENTIFIERS =
        """
        (identifier) @id
        """.trimIndent()

    /**
     * Find all type names (for type reference finding).
     */
    val TYPE_NAMES =
        """
        (type_name) @type
        """.trimIndent()

    /**
     * Find module declarations.
     */
    val MODULE_DECLARATIONS =
        """
        (module_declaration name: (qualified_name) @name) @declaration
        """.trimIndent()

    /**
     * Find package declarations.
     */
    val PACKAGE_DECLARATIONS =
        """
        (package_declaration name: (identifier) @name) @declaration
        """.trimIndent()

    /**
     * Find import statements.
     */
    val IMPORTS =
        """
        (import_statement
            (qualified_name) @import
        )
        """.trimIndent()

    /**
     * Find all call expressions (for call hierarchy).
     */
    val CALL_EXPRESSIONS =
        """
        (call_expression
            function: (identifier) @function) @call
        (call_expression
            function: (member_expression
                property: (identifier) @function)) @call
        """.trimIndent()

    /**
     * Combined query for all declarations (for document symbols).
     */
    val ALL_DECLARATIONS =
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
