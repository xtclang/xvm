/**
 * Verification tests for all "Better today" and "works today" claims
 * in ecstasy-expressiveness.md.
 *
 * Every code pattern that the document claims is valid Ecstasy has a
 * corresponding test here. If it compiles and runs, the claim is verified.
 *
 * Patterns that require language changes (scope functions, if-expressions,
 * void-as-type) are listed at the bottom as commented-out code with the
 * expected compiler error.
 *
 * Compile: xcc scope-function-tests.x
 * Run:     xec scope-function-tests.xtc
 */
module ScopeFunctionTests {
    @Inject ecstasy.io.Console console;

    void run() {
        console.print("=== Category 1: Lazy Ecstasy — works today ===");

        test1a_MapLiteral();
        test1a_TypedFluentPut();
        test1a_HashMapFluentPut();
        test1b_FunctionalFilterToString();
        test1b_ExpressionBodyCollectErrors();
        test1c_BlockExpression();
        test1d_SwitchExpression();
        test1e_FluentAppenderChain();
        test1f_MapLiteralInProperty();
        test1g_CollectionMapFilter();
        test1h_ConditionalBinding();
        test1i_StringTemplateWithCall();
        test1j_ExpressionBodyMethod();

        console.print("\n=== All Category 1 tests passed ===");
    }

    // =========================================================================
    // 1a. Map construction: literal syntax and typed fluent put
    //
    // Document claim (line 670-676):
    //   @Lazy Map<String, String> mapL.calc() = new ListMap<String, String>().put("1", "L");
    //   @Lazy Map<String, String> mapL.calc() = ["1"="L"];
    // =========================================================================

    void test1a_MapLiteral() {
        console.print("\n** test1a_MapLiteral()");

        // Map literal syntax -- document claims this works as a simpler alternative
        // to `new ListMap(); map.put("1", "L"); return map;`
        Map<String, String> map = ["1"="L"];
        assert map.size == 1;
        assert map["1"] == "L";
        console.print($"  PASS: map literal = {map}");
    }

    void test1a_TypedFluentPut() {
        console.print("\n** test1a_TypedFluentPut()");

        // Typed ListMap with fluent put -- document claims this works
        // NOTE: Untyped `new ListMap()` FAILS because put() returns
        //       `this:class(ListMap)<Object, Object>`, not `Map<String, String>`.
        //       Must use explicit type params.
        Map<String, String> map = new ListMap<String, String>().put("1", "L");
        assert map.size == 1;
        assert map["1"] == "L";
        console.print($"  PASS: typed ListMap fluent put = {map}");
    }

    void test1a_HashMapFluentPut() {
        console.print("\n** test1a_HashMapFluentPut()");

        // Same pattern with HashMap
        Map<String, String> map = new HashMap<String, String>().put("1", "H");
        assert map.size == 1;
        assert map["1"] == "H";
        console.print($"  PASS: typed HashMap fluent put = {map}");
    }

    // =========================================================================
    // 1b. Functional filter+toString as replacement for imperative
    //     StringBuffer loop (ErrorLog.collectErrors pattern)
    //
    // Document claim (line 1067-1070):
    //   String collectErrors() =
    //       messages.filter(m -> m.startsWith("Error:"))
    //               .toString(sep="\n", pre="", post="\n");
    // =========================================================================

    String[] testMessages = [
        "Error: something broke",
        "Info : all good",
        "Error: another problem",
        "Warn : heads up",
    ];

    void test1b_FunctionalFilterToString() {
        console.print("\n** test1b_FunctionalFilterToString()");

        // Block-body form
        String result = testMessages.filter(m -> m.startsWith("Error:"))
                                    .toString(sep="\n", pre="", post="\n");
        assert result.indexOf("Error: something broke");
        assert result.indexOf("Error: another problem");
        assert !result.indexOf("Info");
        assert !result.indexOf("Warn");
        console.print($"  PASS: filter+toString = [{result}]");
    }

    // Expression-body form: the exact pattern from the document
    String collectErrors() =
        testMessages.filter(m -> m.startsWith("Error:"))
                    .toString(sep="\n", pre="", post="\n");

    void test1b_ExpressionBodyCollectErrors() {
        console.print("\n** test1b_ExpressionBodyCollectErrors()");

        String result = collectErrors();
        assert result.indexOf("Error: something broke");
        assert result.indexOf("Error: another problem");
        console.print($"  PASS: expression-body collectErrors() = [{result}]");
    }

    // =========================================================================
    // 1c. Block expression with explicit return
    //
    // Document claim (line 1320-1321):
    //   String message = { if (error) return "bad"; return "default"; };
    // =========================================================================

    void test1c_BlockExpression() {
        console.print("\n** test1c_BlockExpression()");

        Boolean error = True;
        String message = {
            if (error) {
                return "bad";
            }
            return "default";
        };
        assert message == "bad";

        error = False;
        String message2 = {
            if (error) {
                return "bad";
            }
            return "default";
        };
        assert message2 == "default";
        console.print($"  PASS: block expression = {message}, {message2}");
    }

    // =========================================================================
    // 1d. Switch as expression
    //
    // Document claim (line 1296-1306):
    //   String message = switch (error) {
    //       case True:  "something went wrong";
    //       case False: "default";
    //   };
    // =========================================================================

    void test1d_SwitchExpression() {
        console.print("\n** test1d_SwitchExpression()");

        Boolean error = True;
        String message = switch (error) {
            case True:  "something went wrong";
            case False: "default";
        };
        assert message == "something went wrong";
        console.print($"  PASS: switch expression = {message}");
    }

    // =========================================================================
    // 1e. Fluent Appender/StringBuffer chaining
    //
    // Document mentions this as the existing correct pattern that scope
    // functions would generalize.
    // =========================================================================

    void test1e_FluentAppenderChain() {
        console.print("\n** test1e_FluentAppenderChain()");

        String s = new StringBuffer()
            .addAll("hello")
            .add(' ')
            .addAll("world")
            .toString();
        assert s == "hello world";
        console.print($"  PASS: fluent appender = {s}");
    }

    // =========================================================================
    // 1f. Map literal as property initializer
    //
    // Document claim that map literals can replace imperative map construction.
    // =========================================================================

    Map<String, String> propMap = ["a"="1", "b"="2"];

    void test1f_MapLiteralInProperty() {
        console.print("\n** test1f_MapLiteralInProperty()");

        assert propMap.size == 2;
        assert propMap["a"] == "1";
        console.print($"  PASS: map literal property = {propMap}");
    }

    // =========================================================================
    // 1g. Collection map + filter chain
    //
    // Verifies that functional collection operations chain correctly.
    // =========================================================================

    void test1g_CollectionMapFilter() {
        console.print("\n** test1g_CollectionMapFilter()");

        Int[] nums = [1, -2, 3, -4, 5];
        Int[] positives = nums.filter(x -> x > 0).toArray();
        assert positives.size == 3;
        assert positives[0] == 1 && positives[1] == 3 && positives[2] == 5;
        console.print($"  PASS: filter chain = {positives}");
    }

    // =========================================================================
    // 1h. Conditional binding with ?=
    //
    // The document discusses this as an existing pattern that scope functions
    // would complement (nullable?.let(f) as an expression-position alternative).
    // =========================================================================

    private String? pickNullable()    = "hello";
    private String? pickNull()        = Null;

    void test1h_ConditionalBinding() {
        console.print("\n** test1h_ConditionalBinding()");

        // Non-null case
        if (String s ?= pickNullable()) {
            assert s == "hello";
            console.print($"  PASS: conditional binding non-null = {s}");
        } else {
            assert False as "expected non-null";
        }

        // Null case
        if (String s ?= pickNull()) {
            assert False as "expected null";
        } else {
            console.print($"  PASS: conditional binding null case");
        }
    }

    // =========================================================================
    // 1i. String template with method call
    //
    // Verifies that string templates work in expression position.
    // =========================================================================

    void test1i_StringTemplateWithCall() {
        console.print("\n** test1i_StringTemplateWithCall()");

        String s = $"result={greet("Ecstasy")}";
        assert s == "result=Hello, Ecstasy!";
        console.print($"  PASS: string template = {s}");
    }

    // =========================================================================
    // 1j. Expression-body method with = syntax
    //
    // Document uses this throughout (e.g., `String collectErrors() = ...`).
    // =========================================================================

    String greet(String name) = $"Hello, {name}!";

    void test1j_ExpressionBodyMethod() {
        console.print("\n** test1j_ExpressionBodyMethod()");

        assert greet("world") == "Hello, world!";
        console.print($"  PASS: expression body = {greet("world")}");
    }

    // =========================================================================
    // Category 2: Would need language changes — COMMENTED OUT
    //
    // These are the "With scope functions" and "if as expression" patterns
    // from the document. They CANNOT compile today, which is the point —
    // the document proposes them as future additions.
    // =========================================================================

    // --- 2a. if-as-expression (NOT SUPPORTED) ---
    // Document line 1324: `String message = if (error) "bad" else "default";`
    // Expected error: "Expected expression, found 'if' keyword"
    //
    // void test_IfExpression() {
    //     Boolean error = True;
    //     String message = if (error) "bad" else "default";
    // }

    // --- 2b. Scope function: let (NOT AVAILABLE) ---
    // Document line 362: `Int length = name?.let(n -> n.trim().size) ?: 0;`
    // Expected error: Object has no method 'let'
    //
    // void test_Let() {
    //     String? name = pickNullable();
    //     Int length = name?.let(n -> n.trim().size) ?: 0;
    // }

    // --- 2c. Scope function: also (NOT AVAILABLE) ---
    // Document line 393: `.also(u -> log.info(...))`
    // Expected error: Object has no method 'also'
    //
    // void test_Also() {
    //     String s = "hello".also(v -> console.print(v));
    // }

    // --- 2d. Scope function: apply (NOT AVAILABLE) ---
    // Document line 416: `new Button().apply(b -> { b.text = "Submit"; })`
    // Expected error: Object has no method 'apply'
    //
    // void test_Apply() {
    //     Map<String, String> m = new HashMap<String, String>().apply(m -> {
    //         m.put("a", "1");
    //         m.put("b", "2");
    //     });
    // }

    // --- 2e. void as a type (NOT SUPPORTED) ---
    // Document line 1210: `void v = bar();` is a compile error
    // Expected error: "void is not a type"
    //
    // void test_VoidAsType() {
    //     void v = console.print("hello");
    // }

    // --- 2f. Last-expression-as-return (NOT SUPPORTED) ---
    // Blocks require explicit `return`, not implicit last-expression.
    // Expected error: block does not produce a value without `return`
    //
    // void test_LastExprReturn() {
    //     String s = { "hello" };
    // }
}
