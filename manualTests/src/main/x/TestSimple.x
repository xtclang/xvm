module TestSimple.test.org
    {
    import ecstasy.io.CharArrayReader;

    package json import json.xtclang.org;

    import json.Doc;
    import json.Lexer;
    import json.Parser;

    @Inject Console console;

    void run()
        {
        String s = `|[
                    |{"tx":14, "value":{"a":1, "d":[1,2,3]}},
                    |{"tx":17, "value":{"b":2, "d":[1,2,3]}},
                    |{"tx":18, "value":{"c":3, "d":[1,2,3]}}
                    |]
                    ;

        console.println("JSON:");
        console.println(s);
        console.println();

        // straight through lex
        console.println("Lexer:");
        Lexer l = new Lexer(new CharArrayReader(s));
        for (val tok : l)
            {
            console.println(tok);
            }

        // parse into a complete doc
        console.println("Parser:");
        Parser p = new Parser(new CharArrayReader(s));
        for (val doc : p)
            {
            console.println(doc);
            }

        console.println();
        console.println("Finding transaction 17:");
        p = new Parser(new CharArrayReader(s));
        using (val p2 = p.expectArray())
            {
            while (val p3 := p2.matchObject())
                {
                using (p3)
                    {
                    p3.expectKey("tx");
                    if (p3.parseDoc().as(IntLiteral) == 17)
                        {
                        assert p3.findKey("value");
                        Doc doc = p3.parseDoc();
                        console.println(doc);
                        }
                    }
                }
            }


//        using (p.openArray())
//            {
//            using (p.openObject())
//                {
//
//                }
//            }
//        expect/match Boolean/Int/IntLiteral/Dec/Float/FloatLiteral/String/Array/Object
//        p.expect/match Open Array/Object
//        p.expect/match/find Close
//
//        p.expectArrayOpen()
//        while p.matchObjectOpen()
//        p.expectKey("tx")
//        closeArray(Boolean skipRemains=False)
//        closeObject(Boolean skipRemains=False)
        }
    }
