/**
 * Roundtrip tests for the modernized `toString()` methods on the AST nodes in
 * `ecstasy.lang.src.ast`. For every input string the test parses it into an AST,
 * renders the AST back to a string via `toString()`, and asserts equality.
 *
 * The seven AST types touched by the lagergren/tostring-typing-project branch
 * are all covered:
 *
 *   - AnnotationExpression   (via @-annotated type expressions)
 *   - ArrayTypeExpression
 *   - ChildTypeExpression
 *   - FunctionTypeExpression
 *   - ImportStatement
 *   - NamedTypeExpression
 *   - TupleTypeExpression    (via `<...>` element inside a generic param list)
 *
 * A failing roundtrip aborts the test (assert), so this module fails the build
 * if any rewrite drifts from the legacy rendering.
 */
module TestAstToString {
    @Inject Console console;

    void run() {
        console.print("\n*** TestAstToString ***\n");
        testTypeExpressions();
        testImportStatements();
        console.print("\nAll AST toString roundtrips passed.");
    }

    void testTypeExpressions() {
        import ecstasy.lang.src.Parser;
        import ecstasy.lang.src.ast.TypeExpression;

        // Each entry is a canonical type-expression string. The roundtrip
        // invariant is: parseTypeExpression(s).toString() == s.
        String[] cases = [
            // NamedTypeExpression
            "String",
            "String?",
            "Map<String, Int>",
            "ecstasy.collections.List",
            "ecstasy.collections.List<Int>",
            "maps.HashMap<String?, IntNumber | IntLiteral>",

            // ChildTypeExpression
            "Map<String, Int>.Entry",
            "ecstasy.collections.List<String>.Cursor",

            // ArrayTypeExpression
            "Int[]",
            "String?[?,?]",
            "(Int | Float)[?,?,?]",

            // FunctionTypeExpression
            "function void (Int)",
            "function Int (String, IntLiteral)",
            "function (Int?, Boolean) ()",

            // AnnotatedTypeExpression -> exercises AnnotationExpression.toString
            "@None Map",
            "@Zero() Map",
            "@One(1) Map",

            // TupleTypeExpression appears as an element of a generic param list
            "Function<<Int, String>, <Int>>",
        ];

        Loop: for (String input : cases) {
            val parser = new Parser(input, allowModuleNames=True);
            TypeExpression type = parser.parseTypeExpression();
            String rendered = type.toString();
            console.print($"[{Loop.count}] {input.quoted()} -> {rendered.quoted()}");
            assert rendered == input as
                    $"roundtrip mismatch: input={input.quoted()}, rendered={rendered.quoted()}";
        }
    }

    void testImportStatements() {
        import ecstasy.lang.src.Parser;
        import ecstasy.lang.src.ast.ImportStatement;

        // Each entry is the canonical rendering of an import statement.
        String[] cases = [
            "import ecstasy.collections.List;",
            "import ecstasy.collections.HashMap;",
            "import ecstasy.collections.List as MyList;",
            "import foo.bar.baz as Q;",
        ];

        Loop: for (String input : cases) {
            val parser = new Parser(input);
            ImportStatement stmt = parser.parseImportStatement();
            String rendered = stmt.toString();
            console.print($"[{Loop.count}] {input.quoted()} -> {rendered.quoted()}");
            assert rendered == input as
                    $"roundtrip mismatch: input={input.quoted()}, rendered={rendered.quoted()}";
        }
    }
}
