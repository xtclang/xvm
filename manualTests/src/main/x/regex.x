module TestRegularExpressions
    {
    import ecstasy.text.Matcher;
    import ecstasy.text.RegEx;

    @Inject Console console;

    void run()
        {
        console.println("*** Regular Expression tests ***\n");
        testConstruct();
        testNotExactMatch();
        testExactMatch();
        testExactMatchWithGroups();
        testNotPrefixMatch();
        testPrefixMatch();
        testPrefixMatchWithNext();
        testFindNoMatch();
        testFind();
        testFindWithNext();
        testReplaceAll();
        }

    void testConstruct()
        {
        RegEx regex = new RegEx("([a-z]+)([0-9]+)");
        assert:test regex.pattern == "([a-z]+)([0-9]+)";
        }

    void testNotExactMatch()
        {
        RegEx regex = new RegEx("[0-9]");
        assert:test !regex.match("a1b2c3");
        }

    void testExactMatch()
        {
        RegEx regex = new RegEx("[0-9]+");
        assert:test Matcher match := regex.match("123");
        assert:test match.groupCount == 0;
        assert:test match[0] == "123";
        assert:test match.next() == False;
        }

    void testExactMatchWithGroups()
        {
        RegEx regex = new RegEx("([0-9]+)([a-z]+)");
        assert:test Matcher match := regex.match("123abc");
        assert:test match.groupCount == 2;
        assert:test match[0] == "123abc";
        assert:test match[1] == "123";
        assert:test match[2] == "abc";
        assert:test match.next() == False;
        }

    void testNotPrefixMatch()
        {
        RegEx regex = new RegEx("[0-9]+");
        assert:test !regex.matchPrefix("xyz123abc");
        }

    void testPrefixMatch()
        {
        RegEx regex = new RegEx("[0-9]+");
        assert:test Matcher match := regex.matchPrefix("123abc");
        assert:test match.groupCount == 0;
        assert:test match[0] == "123";
        assert:test match.next() == False;
        }

    void testPrefixMatchWithNext()
        {
        RegEx regex = new RegEx("[0-9]+");
        assert:test Matcher match := regex.matchPrefix("123abc456def789ghi");
        assert:test match[0] == "123";
        assert:test match.next();
        assert:test match[0] == "456";
        assert:test match.next();
        assert:test match[0] == "789";
        assert:test match.next() == False;
        }

    void testFindNoMatch()
        {
        RegEx regex = new RegEx("[0-9]+");
        assert:test !regex.find("abc");
        }

    void testFind()
        {
        RegEx regex = new RegEx("[0-9]+");
        assert:test Matcher match := regex.find("abc123");
        assert:test match[0] == "123";
        assert:test match.next() == False;
        }

    void testFindWithNext()
        {
        RegEx regex = new RegEx("[0-9]+");
        assert:test Matcher match := regex.find("xyz123abc456def789ghi");
        assert:test match[0] == "123";
        assert:test match.next();
        assert:test match[0] == "456";
        assert:test match.next();
        assert:test match[0] == "789";
        assert:test match.next() == False;
        }

    void testReplaceAll()
        {
        RegEx regex = new RegEx("[0-9]");
        assert:test Matcher match := regex.find("a1b2c3d4e");
        assert:test match.replaceAll("#") == "a#b#c#d#e";
        }
    }