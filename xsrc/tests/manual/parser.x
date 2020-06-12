
module TestParser
    {
    void run()
        {
        @Inject Console console;
        console.println("\n*** TestParser ***\n");

        console.println("\n** Lexer:");
        import ecstasy.lang.src.Lexer;
        import ecstasy.lang.src.Lexer.Token;
        String s = `|Date:2020-12-21 /* date */
                    |Time:12:34 // <- time value
                    |/**
                    | * Doc
                    | */
                    |String s = "this is a test";
                    ;
        Lexer lexer = new Lexer(s);
        Loop: for (Token t : lexer)
            {
            console.println($"[{Loop.count}] token={t.toDebugString()}");
            }

        import ecstasy.lang.src.Parser;
        console.println("\n** Type parser:");
        String[] tests =
            [
            "immutable a.b!<c.d>",
            "String?",
            "collections.HashMap<String?,IntNumber|IntLiteral>",
            "collections.Map<String?,Int>.Entry<SomeType>",
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
            ];

        for (String test : tests)
            {
            Parser parser = new Parser(test);
            val    type   = parser.parseTypeExpression();
            console.println($"serious errs: {parser.errs.seriousCount}, severity={parser.errs.severity}, {test.quoted()}={type}");
            }
        }
    }