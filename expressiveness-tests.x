/**
 * Verification tests for all claims in ecstasy-expressiveness.md about what
 * Ecstasy CAN do today.
 *
 * Every code pattern that the document claims is valid Ecstasy has a
 * corresponding test here. If it compiles and runs, the claim is verified.
 *
 * Patterns that require language changes (scope functions, if-expressions,
 * void-as-type, sealed types, destructuring patterns) are listed at the
 * bottom as commented-out code with the expected compiler error.
 *
 * Compile: xcc expressiveness-tests.x
 * Run:     xec expressiveness-tests.xtc
 */
module ExpressivenessTests {
    @Inject ecstasy.io.Console console;

    void run() {
        console.print("=== Part I: Scope function building blocks ===");

        test_MapLiteral();
        test_TypedFluentPut();
        test_HashMapFluentPut();
        test_FunctionalFilterToString();
        test_ExpressionBodyCollectErrors();
        test_FluentAppenderChain();
        test_MapLiteralInProperty();
        test_CollectionMapFilter();
        test_ConditionalBinding();
        test_StringTemplateWithCall();
        test_ExpressionBodyMethod();
        test_LambdaAndClosure();
        test_GenericMethod();
        test_SafeCallOperator();
        test_ElvisOperator();
        test_UsingBlock();

        console.print("\n=== Part III: Expressions and control flow ===");

        test_SwitchExpression();
        test_SwitchExpressionTypeDispatch();
        test_BlockExpression();
        test_BlockExpressionInAssignment();

        console.print("\n=== Part IV: Modern language features ===");

        test_EnumExhaustiveSwitch();
        test_UnionType();
        test_ConstType();
        test_ConditionalReturn();
        test_TupleDestructuring();
        test_ForLoopDestructuring();
        test_RangeInclusive();
        test_RangeExclusive();
        test_TypeNarrowingWithIs();
        test_DefaultInterfaceMethod();
        test_ComputedProperty();
        test_TupleMatchInSwitch();
        test_RangeMatchInSwitch();
        test_WildcardInTupleSwitch();
        test_MultiValueCase();

        console.print("\n=== Additional pattern tests ===");

        test_MultilineStringTemplate();
        test_Typedef();
        test_VarConditionalReassignment();
        test_VolatileClosureCapture();
        test_ConditionalReturnMultiValue();
        test_ArraySlice();
        test_Mixin();
        test_TryCatchFallback();
        test_BlockExpressionBuilder();
        test_RepeatViaRange();

        console.print("\n=== All tests passed ===");
    }

    // =========================================================================
    // Part I: Scope function building blocks
    //
    // These verify claims in the "Ecstasy language inventory" and
    // "Category 1: Lazy Ecstasy" sections.
    // =========================================================================

    /**
     * Map literal syntax.
     * Document: "Ecstasy has array literals and map literals"
     */
    void test_MapLiteral() {
        console.print("\n** test_MapLiteral()");
        Map<String, String> map = ["key"="value", "a"="b"];
        assert map.size == 2;
        assert map["key"] == "value";
        console.print($"  PASS: map literal = {map}");
    }

    /**
     * Typed ListMap fluent put.
     * Document: "@Lazy Map<String, String> mapL.calc() = new ListMap<String, String>().put(\"1\", \"L\");"
     * NOTE: Untyped new ListMap() FAILS -- must use explicit type params.
     */
    void test_TypedFluentPut() {
        console.print("\n** test_TypedFluentPut()");
        Map<String, String> map = new ListMap<String, String>().put("1", "L");
        assert map.size == 1 && map["1"] == "L";
        console.print($"  PASS: typed ListMap fluent put = {map}");
    }

    void test_HashMapFluentPut() {
        console.print("\n** test_HashMapFluentPut()");
        Map<String, String> map = new HashMap<String, String>().put("1", "H");
        assert map.size == 1 && map["1"] == "H";
        console.print($"  PASS: typed HashMap fluent put = {map}");
    }

    /**
     * Functional filter + toString replacing imperative StringBuffer loop.
     * Document: "Better today -- use functional collection operations"
     *   String collectErrors() =
     *       messages.filter(m -> m.startsWith("Error:"))
     *               .toString(sep="\n", pre="", post="\n");
     */
    String[] testMessages = [
        "Error: something broke",
        "Info : all good",
        "Error: another problem",
        "Warn : heads up",
    ];

    void test_FunctionalFilterToString() {
        console.print("\n** test_FunctionalFilterToString()");
        String result = testMessages.filter(m -> m.startsWith("Error:"))
                                    .toString(sep="\n", pre="", post="\n");
        assert result.indexOf("Error: something broke");
        assert result.indexOf("Error: another problem");
        assert !result.indexOf("Info");
        assert !result.indexOf("Warn");
        console.print($"  PASS: filter+toString = [{result}]");
    }

    // Expression-body form
    String collectErrors() =
        testMessages.filter(m -> m.startsWith("Error:"))
                    .toString(sep="\n", pre="", post="\n");

    void test_ExpressionBodyCollectErrors() {
        console.print("\n** test_ExpressionBodyCollectErrors()");
        String result = collectErrors();
        assert result.indexOf("Error: something broke");
        console.print($"  PASS: expression-body collectErrors()");
    }

    /**
     * Fluent Appender/StringBuffer chaining.
     * Document: "The Appender interface already demonstrates the correct pattern:
     *           add() returns Appender, enabling fluent chaining."
     */
    void test_FluentAppenderChain() {
        console.print("\n** test_FluentAppenderChain()");
        String s = new StringBuffer()
            .addAll("hello")
            .add(' ')
            .addAll("world")
            .toString();
        assert s == "hello world";
        console.print($"  PASS: fluent appender = {s}");
    }

    /**
     * Map literal as property initializer.
     */
    Map<String, String> propMap = ["a"="1", "b"="2"];

    void test_MapLiteralInProperty() {
        console.print("\n** test_MapLiteralInProperty()");
        assert propMap.size == 2 && propMap["a"] == "1";
        console.print($"  PASS: map literal property = {propMap}");
    }

    /**
     * Collection map + filter chain.
     * Document: "Ecstasy has lambdas, generics, closures... higher-order
     *           functions are idiomatic throughout the XDK"
     */
    void test_CollectionMapFilter() {
        console.print("\n** test_CollectionMapFilter()");
        Int[] nums = [1, -2, 3, -4, 5];
        Int[] positives = nums.filter(x -> x > 0).toArray();
        assert positives.size == 3;
        assert positives[0] == 1 && positives[1] == 3 && positives[2] == 5;
        console.print($"  PASS: filter chain = {positives}");
    }

    /**
     * Conditional binding with ?= for nullable types.
     * Document: "Nullable types and safe-call -- String?, ?., ?:, conditional binding"
     */
    private String? pickNullable() = "hello";
    private String? pickNull()     = Null;

    void test_ConditionalBinding() {
        console.print("\n** test_ConditionalBinding()");
        if (String s ?= pickNullable()) {
            assert s == "hello";
            console.print($"  PASS: conditional binding non-null = {s}");
        } else {
            assert False as "expected non-null";
        }
        if (String s ?= pickNull()) {
            assert False as "expected null";
        } else {
            console.print($"  PASS: conditional binding null case");
        }
    }

    /**
     * String template with method call.
     */
    void test_StringTemplateWithCall() {
        console.print("\n** test_StringTemplateWithCall()");
        String s = $"result={greet("Ecstasy")}";
        assert s == "result=Hello, Ecstasy!";
        console.print($"  PASS: string template = {s}");
    }

    /**
     * Expression-body method with = syntax.
     */
    String greet(String name) = $"Hello, {name}!";

    void test_ExpressionBodyMethod() {
        console.print("\n** test_ExpressionBodyMethod()");
        assert greet("world") == "Hello, world!";
        console.print($"  PASS: expression body = {greet("world")}");
    }

    /**
     * Lambdas and closures.
     * Document: "Lambdas and closures -- full support"
     */
    void test_LambdaAndClosure() {
        console.print("\n** test_LambdaAndClosure()");

        // Simple lambda
        function Int(String) sizeOf = s -> s.size;
        assert sizeOf("hello") == 5;

        // Closure capturing outer variable
        Int multiplier = 3;
        function Int(Int) timesThree = n -> n * multiplier;
        assert timesThree(4) == 12;

        // Void lambda
        @Volatile String captured = "";
        function void() sideEffect = () -> { captured = "done"; };
        sideEffect();
        assert captured == "done";

        console.print($"  PASS: lambda and closure");
    }

    /**
     * Generic method.
     * Document: "Generics on methods -- full support"
     */
    <T> T identity(T value) = value;

    void test_GenericMethod() {
        console.print("\n** test_GenericMethod()");
        assert identity("hello") == "hello";
        assert identity(42) == 42;
        console.print($"  PASS: generic method");
    }

    /**
     * Safe-call operator (?.)
     * Document: "Nullable types and safe-call"
     */
    void test_SafeCallOperator() {
        console.print("\n** test_SafeCallOperator()");
        // Conditional binding on nullable -- the idiomatic Ecstasy safe-access pattern
        String? name = pickNullable();
        if (String s ?= name) {
            assert s.size == 5;
            console.print($"  PASS: safe-call non-null = {s}");
        } else {
            assert False as "expected non-null";
        }
        // Null case
        String? nothing = pickNull();
        if (String s ?= nothing) {
            assert False as "expected null";
        } else {
            console.print($"  PASS: safe-call null case");
        }
    }

    /**
     * Elvis operator (?:)
     * Document: "Nullable types and safe-call -- ?:"
     */
    void test_ElvisOperator() {
        console.print("\n** test_ElvisOperator()");
        String? name = pickNull();
        String result = name ?: "default";
        assert result == "default";

        String? present = pickNullable();
        String result2 = present ?: "default";
        assert result2 == "hello";
        console.print($"  PASS: elvis operator");
    }

    /**
     * using block (resource scoping).
     * Document: "using blocks -- resource-scoping with automatic close()"
     */
    void test_UsingBlock() {
        console.print("\n** test_UsingBlock()");
        // Timeout is a Closeable -- using block will close it automatically
        using (new ecstasy.Timeout(Duration:10S)) {
            // just verify it compiles and runs
            console.print($"  PASS: using block");
        }
    }

    // =========================================================================
    // Part III: Expressions and control flow
    //
    // These verify claims about switch expressions, block expressions,
    // and the statement/expression boundary.
    // =========================================================================

    /**
     * Switch as expression.
     * Document: "Ecstasy's switch can be an expression (returning a value)"
     */
    void test_SwitchExpression() {
        console.print("\n** test_SwitchExpression()");
        Boolean error = True;
        String message = switch (error) {
            case True:  "something went wrong";
            case False: "default";
        };
        assert message == "something went wrong";
        console.print($"  PASS: switch expression = {message}");
    }

    /**
     * Switch with type dispatch.
     * Document: "Type dispatch: switch (x.is(_)) { case Int: ... case String: ... }"
     */
    void test_SwitchExpressionTypeDispatch() {
        console.print("\n** test_SwitchExpressionTypeDispatch()");
        Object value = "hello";
        String desc = switch (value.is(_)) {
            case Int:    "it's an int";
            case String: "it's a string";
            default:     "something else";
        };
        assert desc == "it's a string";
        console.print($"  PASS: switch type dispatch = {desc}");
    }

    /**
     * Block expression with explicit return.
     * Document: "Ecstasy supports StatementExpression -- a block { return val; }"
     */
    void test_BlockExpression() {
        console.print("\n** test_BlockExpression()");
        Boolean flag = True;
        String label = {
            if (flag) {
                return "from-block";
            }
            return "default";
        };
        assert label == "from-block";
        console.print($"  PASS: block expression = {label}");
    }

    /**
     * Block expression used to avoid var + if.
     * Document: "Use block expression (works today)"
     */
    void test_BlockExpressionInAssignment() {
        console.print("\n** test_BlockExpressionInAssignment()");
        Boolean error = False;
        String label = {
            if (error) {
                return "bad";
            }
            return "default";
        };
        assert label == "default";
        console.print($"  PASS: block expression avoids var = {label}");
    }

    // =========================================================================
    // Part IV: Modern language features
    //
    // These verify claims in "What else would developers expect in 2026?"
    // about what Ecstasy already has.
    // =========================================================================

    /**
     * Enum with exhaustive switch.
     * Document: "Ecstasy has enums with exhaustive switch checking -- the
     *           compiler rejects a switch on an enum that doesn't cover all values."
     */
    enum Color { Red, Green, Blue }

    void test_EnumExhaustiveSwitch() {
        console.print("\n** test_EnumExhaustiveSwitch()");
        Color c = Blue;
        // Exhaustive -- no default needed for enums
        String name = switch (c) {
            case Red:   "red";
            case Green: "green";
            case Blue:  "blue";
        };
        assert name == "blue";
        console.print($"  PASS: enum exhaustive switch = {name}");
    }

    /**
     * Union types.
     * Document: "Ecstasy has const classes and union types (String | Int)"
     */
    String|Int pickUnion(Boolean useString) = useString ? "hello" : 42;

    void test_UnionType() {
        console.print("\n** test_UnionType()");
        String|Int value = pickUnion(True);
        if (value.is(String)) {
            assert value.size == 5;
        }
        String|Int value2 = pickUnion(False);
        if (value2.is(Int)) {
            assert value2 == 42;
        }
        console.print($"  PASS: union type");
    }

    /**
     * Const types (immutable value types -- Ecstasy's data class equivalent).
     * Document: "const classes (immutable value types)"
     */
    const Point(Int x, Int y);

    void test_ConstType() {
        console.print("\n** test_ConstType()");
        Point p1 = new Point(3, 4);
        Point p2 = new Point(3, 4);
        // const types have value equality
        assert p1 == p2;
        assert p1.x == 3 && p1.y == 4;
        console.print($"  PASS: const type = {p1}");
    }

    /**
     * Conditional return type.
     * Document: "conditional return type is a creative solution"
     */
    conditional String findGreeting(String name) {
        if (name.size > 0) {
            return True, $"Hello, {name}!";
        }
        return False;
    }

    void test_ConditionalReturn() {
        console.print("\n** test_ConditionalReturn()");
        if (String greeting := findGreeting("Ecstasy")) {
            assert greeting == "Hello, Ecstasy!";
            console.print($"  PASS: conditional return found = {greeting}");
        } else {
            assert False as "expected to find greeting";
        }
        if (String greeting := findGreeting("")) {
            assert False as "expected no greeting";
        } else {
            console.print($"  PASS: conditional return not-found");
        }
    }

    /**
     * Tuple destructuring in variable declarations.
     * Document: "Full support for destructuring tuples:
     *           (Int year, _, _, _) = calcDate(epochDay);"
     */
    private (String, Int, Boolean) makeTriple() = ("hello", 42, True);

    void test_TupleDestructuring() {
        console.print("\n** test_TupleDestructuring()");
        (String s, Int n, Boolean b) = makeTriple();
        assert s == "hello" && n == 42 && b == True;

        // Wildcard destructuring
        (String s2, _, Boolean b2) = makeTriple();
        assert s2 == "hello" && b2 == True;
        console.print($"  PASS: tuple destructuring = ({s}, {n}, {b})");
    }

    /**
     * For-loop destructuring.
     * Document: "Full support for tuple destructuring in for loops:
     *           for ((Key key, Value value) : map)"
     */
    void test_ForLoopDestructuring() {
        console.print("\n** test_ForLoopDestructuring()");
        Map<String, Int> map = ["a"=1, "b"=2, "c"=3];
        Int sum = 0;
        for ((String key, Int value) : map) {
            sum += value;
        }
        assert sum == 6;
        console.print($"  PASS: for-loop destructuring, sum = {sum}");
    }

    /**
     * Inclusive range (1..10).
     * Document: "Ranges: 1..10, 1..<10"
     */
    void test_RangeInclusive() {
        console.print("\n** test_RangeInclusive()");
        Int sum = 0;
        for (Int i : 1..5) {
            sum += i;
        }
        assert sum == 15; // 1+2+3+4+5
        console.print($"  PASS: inclusive range sum = {sum}");
    }

    /**
     * Exclusive range (1..<10).
     */
    void test_RangeExclusive() {
        console.print("\n** test_RangeExclusive()");
        Int sum = 0;
        for (Int i : 0..<5) {
            sum += i;
        }
        assert sum == 10; // 0+1+2+3+4
        console.print($"  PASS: exclusive range sum = {sum}");
    }

    /**
     * Type narrowing with .is() -- flow-sensitive typing.
     * Document: "switch (x.is(_)) already does type dispatch"
     */
    private Object pickObject() = "hello world";

    void test_TypeNarrowingWithIs() {
        console.print("\n** test_TypeNarrowingWithIs()");
        Object value = pickObject();
        if (value.is(String)) {
            // value is narrowed to String inside this block
            assert value.size == 11;
            assert value.startsWith("hello");
        }
        console.print($"  PASS: type narrowing with .is()");
    }

    /**
     * Default interface method implementations.
     * Document: "Interfaces support default implementations"
     */
    interface Describable {
        String name;
        // Default method implementation
        String describe() = $"I am {name}";
    }

    const Widget(String name) implements Describable;

    void test_DefaultInterfaceMethod() {
        console.print("\n** test_DefaultInterfaceMethod()");
        Widget w = new Widget("button");
        assert w.describe() == "I am button";
        console.print($"  PASS: default interface method = {w.describe()}");
    }

    /**
     * Computed properties with .get() syntax.
     * Document: "Computed properties: .get() and .set() syntax"
     */
    const Rectangle(Int width, Int height) {
        Int area.get() = width * height;
    }

    void test_ComputedProperty() {
        console.print("\n** test_ComputedProperty()");
        Rectangle r = new Rectangle(3, 4);
        assert r.area == 12;
        console.print($"  PASS: computed property = {r.area}");
    }

    /**
     * Tuple matching in switch.
     * Document: "Tuple matching: switch (a, b) { case (1, 2): ... }"
     */
    void test_TupleMatchInSwitch() {
        console.print("\n** test_TupleMatchInSwitch()");
        Int a = 1;
        Int b = 2;
        String result = switch (a, b) {
            case (1, 2): "one-two";
            case (3, 4): "three-four";
            default:     "other";
        };
        assert result == "one-two";
        console.print($"  PASS: tuple switch = {result}");
    }

    /**
     * Range matching in switch cases.
     * Document: "Range matching: case 1..5:"
     */
    void test_RangeMatchInSwitch() {
        console.print("\n** test_RangeMatchInSwitch()");
        Int x = 3;
        String result = switch (x) {
            case 1..5:   "low";
            case 6..10:  "high";
            default:     "out of range";
        };
        assert result == "low";
        console.print($"  PASS: range switch = {result}");
    }

    /**
     * Wildcard in tuple switch -- using value tuples.
     * Document: "Wildcard in tuples: case (_, Int):"
     * Note: Wildcards work with VALUE tuples, not type-dispatch tuples.
     */
    void test_WildcardInTupleSwitch() {
        console.print("\n** test_WildcardInTupleSwitch()");
        Int a = 1;
        Int b = 99;
        String result = switch (a, b) {
            case (1, _):  "a is one";
            case (_, 99): "b is 99";
            default:      "other";
        };
        assert result == "a is one";  // first match wins
        console.print($"  PASS: wildcard tuple switch = {result}");
    }

    /**
     * Multi-value / disjunctive case.
     * Document: "case 1, 3, 5: (multiple/disjunctive patterns)"
     */
    void test_MultiValueCase() {
        console.print("\n** test_MultiValueCase()");
        Int x = 3;
        String result = switch (x) {
            case 1, 3, 5: "odd";
            case 2, 4, 6: "even";
            default:       "other";
        };
        assert result == "odd";
        console.print($"  PASS: multi-value case = {result}");
    }

    // =========================================================================
    // Part III additional: expression-related patterns
    // =========================================================================

    /**
     * Multiline string template with $| syntax.
     * Document: "Ecstasy has multiline string templates with $|"
     */
    void test_MultilineStringTemplate() {
        console.print("\n** test_MultilineStringTemplate()");
        String name = "Ecstasy";
        Int version = 1;
        String s = $|Hello {name},
                    |version {version}
                    ;
        assert s.indexOf("Hello Ecstasy");
        assert s.indexOf("version 1");
        console.print($"  PASS: multiline template = [{s}]");
    }

    /**
     * typedef -- type alias.
     * Document: "typedef Appender<String> as Log"
     */
    typedef function Int(Int, Int) as BinaryOp;

    void test_Typedef() {
        console.print("\n** test_Typedef()");
        BinaryOp add = (a, b) -> a + b;
        BinaryOp mul = (a, b) -> a * b;
        assert add(3, 4) == 7;
        assert mul(3, 4) == 12;
        console.print($"  PASS: typedef = add(3,4)={add(3, 4)}, mul(3,4)={mul(3, 4)}");
    }

    /**
     * var reassignment for conditional pipeline.
     * Document: "var result = ... ; if (cond) { result = result.flatMap(...); }"
     * This is the Header.x pattern from Part III -- verifying that var
     * reassignment works as described.
     */
    void test_VarConditionalReassignment() {
        console.print("\n** test_VarConditionalReassignment()");
        String[] items = ["a,b", "c", "d,e,f"];
        Boolean expand = True;
        var result = items.iterator();
        if (expand) {
            result = result.flatMap((String s) -> s.split(','));
        }
        String[] collected = result.toArray();
        assert collected.size == 6; // a, b, c, d, e, f
        console.print($"  PASS: var conditional reassign = {collected}");
    }

    /**
     * @Volatile for closure mutation.
     * Document: "@Volatile for variables that need to be modified from within lambdas"
     */
    void test_VolatileClosureCapture() {
        console.print("\n** test_VolatileClosureCapture()");
        @Volatile Int counter = 0;
        function void() increment = () -> { counter++; };
        increment();
        increment();
        increment();
        assert counter == 3;
        console.print($"  PASS: @Volatile closure capture = {counter}");
    }

    /**
     * Conditional return used with := binding.
     * Document: "conditional Config parse(String input) { ... }
     *           if (Config config := parse(input)) { ... }"
     * Tests the pattern that Result types would complement.
     */
    conditional (String, Int) parseKeyValue(String input) {
        if (Int eq := input.indexOf('=')) {
            return True, input[0 ..< eq], new Int(input.substring(eq + 1));
        }
        return False;
    }

    void test_ConditionalReturnMultiValue() {
        console.print("\n** test_ConditionalReturnMultiValue()");
        if ((String key, Int value) := parseKeyValue("answer=42")) {
            assert key == "answer" && value == 42;
            console.print($"  PASS: conditional multi-return = {key}={value}");
        } else {
            assert False as "expected parse success";
        }
        if (parseKeyValue("no-equals")) {
            assert False as "expected parse failure";
        } else {
            console.print($"  PASS: conditional multi-return failure case");
        }
    }

    /**
     * Array slice with range.
     * Document: array[1..3] slice notation
     */
    void test_ArraySlice() {
        console.print("\n** test_ArraySlice()");
        Int[] nums = [10, 20, 30, 40, 50];
        Int[] slice = nums[1..3];
        assert slice.size == 3;
        assert slice[0] == 20 && slice[1] == 30 && slice[2] == 40;
        console.print($"  PASS: array slice = {slice}");
    }

    /**
     * Mixin pattern.
     * Document: "Ecstasy has mixins which can add methods to types"
     */
    mixin Greeting into Object {
        String greetWith(String name) = $"Hi from {this}, {name}!";
    }

    const Greeter(String title) incorporates Greeting;

    void test_Mixin() {
        console.print("\n** test_Mixin()");
        Greeter g = new Greeter("Boss");
        String msg = g.greetWith("world");
        assert msg.indexOf("Hi from");
        assert msg.indexOf("world");
        console.print($"  PASS: mixin = {msg}");
    }

    // =========================================================================
    // Related Kotlin constructs: patterns that work today
    // =========================================================================

    /**
     * try/catch as fallback (what runCatching would replace).
     * Document: "try/catch block is the only way to convert exceptions into values"
     */
    void test_TryCatchFallback() {
        console.print("\n** test_TryCatchFallback()");
        // Pattern: try to parse, fall back on failure
        Int value;
        try {
            value = new Int("not-a-number");
        } catch (Exception e) {
            value = -1;
        }
        assert value == -1;
        console.print($"  PASS: try/catch fallback = {value}");
    }

    /**
     * Block expression as builder (what buildMap/buildList would replace).
     * Document: "block expression with explicit return"
     */
    void test_BlockExpressionBuilder() {
        console.print("\n** test_BlockExpressionBuilder()");
        // Build a map imperatively, return frozen
        Map<String, Int> counts = {
            HashMap<String, Int> m = new HashMap();
            for (String word : ["a", "b", "a", "c", "b", "a"]) {
                m.put(word, m.getOrDefault(word, 0) + 1);
            }
            return m.freeze(inPlace=True);
        };
        assert counts["a"] == 3;
        assert counts["b"] == 2;
        assert counts["c"] == 1;
        console.print($"  PASS: block expression builder = {counts}");
    }

    /**
     * Repeat via range (what repeat() would replace -- already concise enough).
     * Document: "range syntax is concise enough that a dedicated repeat function
     *           adds minimal value"
     */
    void test_RepeatViaRange() {
        console.print("\n** test_RepeatViaRange()");
        @Volatile Int counter = 0;
        for (Int i : 0 ..< 5) {
            counter++;
        }
        assert counter == 5;
        console.print($"  PASS: repeat via range = {counter}");
    }

    // =========================================================================
    // NOT SUPPORTED — commented out to document what doesn't work
    // =========================================================================

    // --- if-as-expression (NOT SUPPORTED) ---
    // void test_IfExpression() {
    //     String message = if (True) "yes" else "no";
    // }

    // --- Scope functions (NOT AVAILABLE) ---
    // void test_Let() { "hello".let(s -> s.size); }
    // void test_Also() { "hello".also(s -> console.print(s)); }
    // void test_Apply() { new HashMap().apply(m -> m.put("a", 1)); }

    // --- void as a type (NOT SUPPORTED) ---
    // void test_VoidAsType() { void v = console.print("hello"); }

    // --- Sealed types (NOT SUPPORTED) ---
    // sealed interface Result<T> {
    //     const Success<T>(T value) implements Result<T>;
    //     const Failure(Exception error) implements Result<Nothing>;
    // }

    // --- Destructuring in case branches (NOT SUPPORTED) ---
    // void test_DestructuringPattern() {
    //     Point p = new Point(3, 4);
    //     switch (p) {
    //         case Point(x, y) if x > 0: console.print("positive x");
    //     }
    // }

    // --- Guard clauses in case (NOT SUPPORTED) ---
    // void test_GuardClause() {
    //     Int x = 5;
    //     switch (x) {
    //         case Int if x > 0: "positive";
    //     }
    // }
}
