
module TestParser
    {
    @Inject Console console;

    void run()
        {
        console.print("\n*** TestParser ***\n");
        testLexer();
        testParser();
        testTypeSystem();
        }

    void testLexer()
        {
        console.print("\n** Lexer:");
        import ecstasy.lang.src.Lexer;
        import ecstasy.lang.src.Lexer.Token;
        String s = \|Date:2020-12-21 /* date */
                    |TimeOfDay:12:34 // <- time-of-day value
                    |/**
                    | * Doc
                    | */
                    |String s = "this is a test";
                    ;
        Lexer lexer = new Lexer(s);
        Loop: for (Token t : lexer)
            {
            console.print($"[{Loop.count}] token={t.toDebugString()}");
            }
        }

    void testParser()
        {
        import ecstasy.lang.src.Parser;
        console.print("\n** Type parser:");
        String[] tests =
            [
            "immutable a.b!<c.d>",
            "String?",
            "collections.HashMap<String?,IntNumber|IntLiteral>",
            "collections.Map<String?, Int>.Entry<SomeType>",
            "Int[]",
            "String?[?,?]",
            "(Int|Float)[?,?,?]",
            "function void (Int)",
            "function Int (String, IntLiteral)",
            "function (Int?, Boolean) ()",
            // TODO CP (and test in Java, too): "function (Int | (Boolean + String))? (Char)",
            "function (Int | (Boolean + String) name1, T1-T2 name2) (Char)",
            "@None Map",
            "@None @Zero() Map",
            "@None @Zero() @One(1) Map",
            "@None @Zero() @One(1) @Two(-1, \"hello\") Map",
            "@None @Zero() @One(1) @Two(-1, \"hello\") Map<@Junk('a') String, @Expires(Date:2020-12-25) util.Password>",
            "ecstasy.xtclang.org:collections.HashMap",
            "ecstasy.xtclang.org:collections.List<ecstasy.xtclang.org:numbers.Int64>",
            "@ecstasy.xtclang.org:annotations.AutoFreezable ecstasy.xtclang.org:collections.List<@ecstasy.xtclang.org:annotations.AutoFreezable ecstasy.xtclang.org:numbers.Int64>",
            "Function<Tuple<String>, Tuple<Int>>",
            ];

        for (String test : tests)
            {
            try
                {
                Parser parser = new Parser(test, allowModuleNames=True);
                val    type   = parser.parseTypeExpression();
                String parsed = type.toString();
                if (test != parsed)
                    {
                    console.print($"serious errs: {parser.errs.seriousCount}, severity={parser.errs.severity}, eof={parser.eof}, {test.quoted()}={parsed}");
                    }
                }
            catch (Exception e)
                {
                console.print($"Exception: {e} for {test.quoted()}");
                }
            }
        }

    class Normal<K,V>
        {
        static class Child;
        class VirtualChild;
        }

    void testTypeSystem()
        {
        console.print("\n** TypeSystem:");

        TypeSystem typeSystem = this:service.typeSystem;
        console.print($"TypeSystem={typeSystem}");

        String[] tests =
            [
            "",
            "ecstasy",
            "ecstasy.Boolean",
            "ecstasy.collections.Map",
            "Normal",
            "Normal.Child",
            "Normal.VirtualChild",
            "Normal<Normal,Normal>",
            ];

        for (String test : tests)
            {
            if (Class clz := typeSystem.classForName(test))
                {
                if (test != clz.toString())
                    {
                    console.print($"class for {test.quoted()}={clz}");
                    }
                }
            else
                {
                console.print($"no class for {test.quoted()}");
                }
            }
        }
    }