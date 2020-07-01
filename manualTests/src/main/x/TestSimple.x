module TestSimple
    {
    @Inject Console console;

    void run( )
        {
        console.println();

        testPerformance();
        }

    void testPerformance()
        {
        import ecstasy.lang.src.Lexer.Id;

        Map<String, Id> map1 = Id.allKeywords;

        console.println($"allKeywords: {map1.size}");

        Map<String, Id> map2 = Id.keywords;

        console.println($"keywords: {map2.size}");

        Map<String, Id> map3 = Id.prefixes;

        console.println($"keywords: {map3.size}");

        TypeSystem ts = this:service.typeSystem;

        // below is still sub-optimal
//        Class clz;
//
//        assert clz := ts.classForName("Appender");
//        console.println($"class for Appender={clz}");
//
//        assert clz := ts.classForName("String");
//        console.println($"class for String={clz}");
        }
    }
