package org.xvm.lsp.treesitter

/**
 * Tree-sitter query patterns for XTC language constructs.
 *
 * These S-expression queries match specific patterns in the syntax tree,
 * enabling extraction of declarations, references, and other language elements.
 *
 * NOTE: The XTC grammar does NOT define field names (via field() in grammar.js).
 * All queries must use positional/structural matching, not field: syntax.
 * For example, use `(class_declaration (type_name) @name)` not `(class_declaration name: (type_name))`.
 *
 * TODO: Consider migrating the grammar (grammar.js) to use field() definitions.
 *       This would enable field-based query syntax like:
 *         `(class_declaration name: (type_name) @name)`
 *       instead of positional matching. Benefits:
 *       - More robust queries (not dependent on child ordering)
 *       - Cleaner query patterns (field: syntax is self-documenting)
 *       - Better XtcNode API (childByFieldName would work)
 *       - Alignment with tree-sitter best practices
 *       Example grammar change:
 *         BEFORE: class_declaration: $ => seq('class', $.type_name, ...)
 *         AFTER:  class_declaration: $ => seq('class', field('name', $.type_name), ...)
 *       See: lang/doc/plans/PLAN_TREE_SITTER.md for tracking.
 */
object XtcQueries {
    /**
     * Find all type declarations (classes, interfaces, mixins, services, consts, enums).
     * Matches type_name child of each declaration type.
     */
    val TYPE_DECLARATIONS =
        """
        (class_declaration (type_name) @name) @declaration
        (interface_declaration (type_name) @name) @declaration
        (mixin_declaration (type_name) @name) @declaration
        (service_declaration (type_name) @name) @declaration
        (const_declaration (type_name) @name) @declaration
        (enum_declaration (type_name) @name) @declaration
        """.trimIndent()

    /**
     * Find all method declarations.
     * Matches the identifier (method name) and parameters within method_declaration.
     */
    val METHOD_DECLARATIONS =
        """
        (method_declaration
            (type_expression)
            (identifier) @name
            (parameters) @params
        ) @declaration
        """.trimIndent()

    /**
     * Find all constructor declarations.
     */
    val CONSTRUCTOR_DECLARATIONS =
        """
        (constructor_declaration
            (parameters) @params
        ) @declaration
        """.trimIndent()

    /**
     * Find all property declarations.
     * Matches type_expression followed by identifier in property_declaration.
     */
    val PROPERTY_DECLARATIONS =
        """
        (property_declaration
            (type_expression) @type
            (identifier) @name
        ) @declaration
        """.trimIndent()

    /**
     * Find all variable declarations.
     * Note: variable_declaration has multiple forms - this matches the identifier.
     */
    val VARIABLE_DECLARATIONS =
        """
        (variable_declaration
            (identifier) @name
        ) @declaration
        """.trimIndent()

    /**
     * Find all parameter declarations.
     */
    val PARAMETER_DECLARATIONS =
        """
        (parameter
            (type_expression) @type
            (identifier) @name
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
        (module_declaration (qualified_name) @name) @declaration
        """.trimIndent()

    /**
     * Find package declarations.
     */
    val PACKAGE_DECLARATIONS =
        """
        (package_declaration (identifier) @name) @declaration
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
     * Note: call_expression structure is (call_expression function args).
     */
    val CALL_EXPRESSIONS =
        """
        (call_expression
            (identifier) @function) @call
        (call_expression
            (member_expression
                (identifier) @function)) @call
        """.trimIndent()

    /**
     * Combined query for all declarations (for document symbols).
     * Uses positional matching since grammar has no field definitions.
     */
    val ALL_DECLARATIONS =
        """
        (module_declaration (qualified_name) @name) @module
        (package_declaration (identifier) @name) @package
        (class_declaration (type_name) @name) @class
        (interface_declaration (type_name) @name) @interface
        (mixin_declaration (type_name) @name) @mixin
        (service_declaration (type_name) @name) @service
        (const_declaration (type_name) @name) @const
        (enum_declaration (type_name) @name) @enum
        (method_declaration (type_expression) (identifier) @name) @method
        (constructor_declaration) @constructor
        (property_declaration (type_expression) (identifier) @name) @property
        """.trimIndent()
}
