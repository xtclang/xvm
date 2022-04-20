module TestRegularExpressions
    {
    import ecstasy.text.Match;
    import ecstasy.text.RegEx;

    @Inject Console console;

    void run()
        {
        testConstruct();
        testNotExactMatch();
        testExactMatch();
        testExactMatchWithGroups();
        testExactMatchWithGroupsNotMatched();
        testNotPrefixMatch();
        testPrefixMatch();
        testPrefixMatchWithNext();
        testFindNoMatch();
        testFind();
        testFindFromOffset();
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
        assert:test Match match := regex.match("123");
        assert:test match.groupCount == 0;
        assert:test match[0] == "123";
        assert:test String g := match.group();
        assert:test g == "123";
        }

    void testExactMatchWithGroups()
        {
        RegEx regex = new RegEx("([0-9]+)([a-z]+)");
        assert:test Match match := regex.match("123abc");
        assert:test match.groupCount == 2;
        assert:test match[0] == "123abc";
        assert:test match[1] == "123";
        assert:test match[2] == "abc";
        }

    void testExactMatchWithGroupsNotMatched()
        {
        RegEx regex = new RegEx("([0-9]+)($)*([a-z]+)");

        assert:test Match match := regex.match("123abc");
        assert:test match.groupCount == 3;

        assert:test String gAll := match.group();
        assert:test gAll == "123abc";

        assert:test String g0 := match.group(0);
        assert:test g0 == "123abc";
        assert:test match[0] == "123abc";

        assert:test String g1 := match.group(1);
        assert:test g1 == "123";
        assert:test match[1] == "123";

        assert:test !match.group(2);
        assert:test match[2] == Null;

        assert:test String g3 := match.group(3);
        assert:test g3 == "abc";
        assert:test match[3] == "abc";
        }

    void testNotPrefixMatch()
        {
        RegEx regex = new RegEx("[0-9]+");
        assert:test !regex.matchPrefix("xyz123abc");
        }

    void testPrefixMatch()
        {
        RegEx regex = new RegEx("[0-9]+");
        assert:test Match match := regex.matchPrefix("123abc");
        assert:test match.groupCount == 0;
        assert:test match[0] == "123";
        }

    void testPrefixMatchWithNext()
        {
        RegEx regex = new RegEx("[0-9]+");
        assert:test Match match := regex.matchPrefix("123abc456def789ghi");
        assert:test match[0] == "123";
        assert:test (String s, Range<Int> r) := match.group(0);
        assert:test s == "123";
        assert:test r.first == 0 && r.last == 3;
        assert:test match := match.next();
        assert:test match[0] == "456";
        assert:test (s, r) := match.group(0);
        assert:test s == "456";
        assert:test r.first == 6 && r.last == 9;
        assert:test match := match.next();
        assert:test match[0] == "789";
        assert:test !match.next();
        }

    void testFindNoMatch()
        {
        RegEx regex = new RegEx("[0-9]+");
        assert:test !regex.find("abc");
        }

    void testFind()
        {
        RegEx regex = new RegEx("[0-9]+");
        assert:test Match match := regex.find("abc123");
        assert:test match[0] == "123";
        }

    void testFindFromOffset()
        {
        RegEx regex = new RegEx("[0-9]+");
        assert:test Match match := regex.find("abc123def567", 6);
        assert:test match[0] == "567";
        }

    void testFindWithNext()
        {
        RegEx regex = new RegEx("[0-9]+");
        assert:test Match match := regex.find("xyz123abc456def789ghi");
        assert:test match[0] == "123";
        assert:test match := match.next();
        assert:test match[0] == "456";
        assert:test match := match.next();
        assert:test match[0] == "789";
        assert:test !match.next();
        }

    void testReplaceAll()
        {
        RegEx regex = new RegEx("[0-9]");
        assert:test regex.replaceAll("a1b2c3d4e", "#") == "a#b#c#d#e";
        }
    }