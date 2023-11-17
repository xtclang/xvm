module TestLiterals {
    @Inject Console console;

    void run() {
        console.print("*** literal tests ***\n");

        testFactors();
        testVersions();
        testPaths();
        testIncludes();
        testMultiline();
        testMultilineTemplate();
        testHex();
        testDirs();
        testDates();
        testTimeOfDays();
        testTimes();
        testDurations();
//        testLexer();
        testInterval();
    }

    void testFactors() {
        console.print("\n** testFactors()");

        @Volatile String s = "";
        @Volatile Int    n = 0;
        val show = () -> {
            console.print($"{s} == {n}");
        };

        s = "1KB";  n = 1KB;  show();
        s = "1KI";  n = 1KI;  show();
        s = "1kB";  n = 1kB;  show();
        s = "1kI";  n = 1kI;  show();
        s = "1Kb";  n = 1Kb;  show();
        s = "1Ki";  n = 1Ki;  show();
        s = "1Kib"; n = 1Kib; show();
        s = "1M";   n = 1M;   show();
        s = "1Mi";  n = 1Mi;  show();
        s = "1G";   n = 1G;   show();
        s = "1Gi";  n = 1Gi;  show();
        s = "1T";   n = 1T;   show();
        s = "1Ti";  n = 1Ti;  show();
        s = "1P";   n = 1P;   show();
        s = "1Pi";  n = 1Pi;  show();
        s = "1E";   n = 1E;   show();
        s = "1Ei";  n = 1Ei;  show();
//      s = "1Z";   n = 1Z;   show();
//      s = "1Zi";  n = 1Zi;  show();
//      s = "1Y";   n = 1Y;   show();
//      s = "1Yi";  n = 1Yi;  show();
    }

    void testVersions() {
        console.print("\n** testVersions()");

        Version version = new Version(Null, 1);
        console.print($"new Version(Null, 1)={version}");

        version = new Version(version, 0);
        // version = new Version(version, 0, "20130313144700");
        console.print($"new Version(version, 0)={version}");

        version = new Version(version, Alpha);
        console.print($"new Version(version, Alpha)={version}");

        version = new Version(version, 2);
        console.print($"new Version(version, 2)={version}");

        for (Int i : 0..3) {
            console.print("version[" + i + "]=" + version[i]);
        }

        console.print("version[1..2]=" + version[1..2]);
        console.print("version[0..1]=" + version[0..1]);
        console.print("--version=" + --version);
        console.print("++version=" + ++version);

        for (String s : ["1", "alpha", "1.0", "beta2", "5.6.7.8-alpha", "1.2-beta5", "1.2beta5"]) {
            console.print("version for String " + s + "=" + new Version(s));
        }

        // "1.2-beta3" to "1.2-beta5"
        console.print("steps from 1.2-beta to 1.2-beta5="
                + new Version("1.2-beta").stepsTo(new Version("1.2-beta5")));
        console.print("steps from 1.2-beta3 to 1.2-beta="
                + new Version("1.2-beta3").stepsTo(new Version("1.2-beta")));
        console.print("steps from 1.2-beta3 to 1.2-beta5="
                + new Version("1.2-beta3").stepsTo(new Version("1.2-beta5")));

        version = v:alpha;
        console.print($"literal v:alpha={version}");

        version = v:1;
        console.print($"literal v:1={version}");

        version = v:1.0;
        console.print($"literal v:1.0={version}");

        version = v:beta2;
        console.print($"literal v:beta2={version}");

        version = v:5.6.7.8-alpha;
        console.print($"literal v:5.6.7.8-alpha={version}");

        version = v:1.2-beta5;
        console.print($"literal v:1.2-beta5={version}");

        version = v:1.2beta5;
        console.print($"literal v:1.2beta5={version}");

        version = v:1.2beta5+123-456.abc;
        console.print($"literal v:1.2beta5+123-456.abc={version}");
    }

    void testPaths() {
        console.print("\n** testPaths()");

        Path path1 = Path:./;
        console.print($"Path ./={path1}");

        Path path2 = Path:./more/;
        console.print($"Path ./more/={path2}");

        Path path3 = Path:./more/msgs_EN.txt;
        console.print($"Path ./more/msgs_EN.txt={path3}");

        assert path3.startsWith(path3);
        assert path3.startsWith(path2);
        assert path3.startsWith(path1);
        assert path2.startsWith(path1);
        assert path2.startsWith(path2);
        assert path1.startsWith(path1);
        assert !path1.startsWith(path2);
        assert !path1.startsWith(path3);

        Path path4 = new Path("msgs_EN.txt");
        console.print($"Path for \"msgs_EN.txt\"={path4}");

        assert path4.endsWith(path4);
        assert path3.endsWith(path4);
        assert path3.endsWith(path3);
        assert path2.endsWith(path2);
        assert !path2.endsWith(path4);
        assert path1.endsWith(path1);
        assert !path1.endsWith(path4);

        Path path5 = ./allow.as.assert.avoid.public.private.if.for.while.var.val.void.txt;
        Path path6 = /allow.as.assert.avoid.public.private.if.for.while.var.val.void.txt;

        File file = ./more/msgs_EN.txt;
        console.print($"File ./more/msgs_EN.txt={file}");

        Directory dir = ./;
        console.print($"Dir ./={dir}");

        dir = Directory:./more/;
        console.print($"Dir ./more/={dir}");
    }

    void testIncludes() {
        console.print("\n** testIncludes()");

        String s = $./more/msgs_EN.txt;
        console.print($"./more/msgs_EN.txt={s}");

        assert $./allow.as.assert.avoid.public.private.if.for.while.var.val.void.txt == "hello world!";
        assert $/allow.as.assert.avoid.public.private.if.for.while.var.val.void.txt == "hello world!";
    }

    void testMultiline() {
        console.print("\n** testMultiline()");

        String s = \|<%@ taglib uri="/WEB-INF/jspwiki.tld" prefix="wiki" %>
                    |<%@ taglib uri="http://stripes.sourceforge.net/stripes.tld" prefix="stripes" %>
                    |<stripes:useActionBean binding="/View.action" />
                    |<wiki:Include page="ViewTemplate.jsp" />
                    |String myString = "This is my string\n" +
                    |        " which I want to be \n" +
                    |        "on multiple lines.";
                    ||s = """ this is a very
                    |        long string if I had the
                    |        energy to type more and more ..."""
                    |`string text`
                    |
                    |`string text line 1
                    | string text line 2`
                    |
                    |`string text ${expression} string text`
                    |
                    |tag `string text ${expression} string text`
                    |//const char* p = "\xfff"; // error: hex escape sequence out of range
                    |const char* p = "\xff""f"; // OK: the literal is const char[3] holding {'\xff','f','\0'}
                  ; // semi-colon is the end of the declaration statement
        console.print($"s={s}");

        s = \|This is my string\
             | which I want to be\
             | on one line
             ;

        console.print($"s one-line={s}");
    }

    void testMultilineTemplate() {
        console.print("\n** testMultiline()");

        const Person(String firstname, String lastname);
        Person person = new Person("Bob", "Smith");

        String s = $|# TOML doc
                    |[name]
                    |first = "{person.firstname}"
                    |last = "{person.lastname}"
                    ;

        console.print($"\nTOML=\n{s}");

        s = $|\{
             |"person": \{
             |  "first": "{person.firstname}"
             |  "last": "{person.lastname}"
             |  }
             |}
             ;

        console.print($"\nJSON=\n{s}");

        s = $|\{\
             |"person": \{\
             |  "first": "{person.firstname}"\
             |  "last": "{person.lastname}"\
             |  }\
             |}
             ;

        console.print($"\nJSON one-line={s}");
    }

    void testHex() {
        console.print("\n** testHex()");

        Byte[] bytes = #123_4567_89aB_cDeF;
        console.print($"bytes={bytes}");

        bytes = #|0123456789aBcDeF 0123456789aBcDeF 0123456789aBcDeF 0123456789aBcDeF
                 |0123456789aBcDeF_0123456789aBcDeF_0123456789aBcDeF_0123456789aBcDeF
                 ;
        console.print($"bytes={bytes[0..10]}...{bytes[bytes.size-10 ..< bytes.size]}");

        bytes = #/literals.x;
        console.print($"bytes={bytes[0..10]}...{bytes[bytes.size-10 ..< bytes.size]}");
    }

    void testDirs() {
        console.print("\n** testDirs()");

        FileStore fs = FileStore:/archive/moduleTest;
        console.print("fs=" + fs);

        console.print($"\n(recursive)\n{{fs.emitListing($, recursive=True);}}");
        console.print($"\n(non-recursive)\n{{fs.emitListing($, False);}}");

        File file = File:./more/msgs_EN.txt;
        console.print($"File:./more/msgs_EN.txt={file}");

        Directory dir = Directory:./archive/moduleTest;
        console.print($"Directory:./archive/moduleTest=(recursive)\n{{dir.emitListing($, recursive=True);}}");
    }

    void testDates() {
        console.print("\n** testDates()");

        Date date = new Date("1999-12-25");
        console.print($"date={date} or {Date:1999-12-25}");

        date = new Date("19991225");
        console.print($"date={date} or {Date:19991225}");

        date = new Date("99999-01-23");
        console.print($"date={date} or {Date:9999-01-23} (one less 9)");
    }

    void testTimeOfDays() {
        console.print("\n** testTimeOfDays()");

        TimeOfDay timeOfDay = new TimeOfDay("12:01:23");
        console.print($"timeOfDay={timeOfDay} or {TimeOfDay:12:01:23}");

        timeOfDay = new TimeOfDay("120123");
        console.print($"timeOfDay={timeOfDay} or {TimeOfDay:120123}");

        timeOfDay = new TimeOfDay("12:01:23.456");
        console.print($"timeOfDay={timeOfDay} or {TimeOfDay:12:01:23.456}");

        timeOfDay = new TimeOfDay("120123.456");
        console.print($"timeOfDay={timeOfDay} or {TimeOfDay:120123.456}");
    }

    void testTimes() {
        console.print("\n** testTimes()");

        Time dt = new Time("1999-12-25T12:01:23");
        console.print($"dt={dt} or {Time:1999-12-25T12:01:23}");

        dt = new Time("19991225T120123");
        console.print($"dt={dt} or {Time:19991225T120123}");

        dt = new Time("99999-01-23T12:01:23.456");
        console.print($"dt={dt} or {Time:9999-01-23T12:01:23.456} (one less 9)");

        dt = new Time("2019-05-22T120123.456Z");
        console.print($"dt={dt} or {Time:2019-05-22T120123.456Z}");

        dt = new Time("2019-05-22T120123.456+01:30");
        console.print($"dt={dt} or {Time:2019-05-22T120123.456+01:30}");

        dt = new Time("2019-05-22T120123.456-5:00");
        console.print($"dt={dt} or {Time:2019-05-22T120123.456-05:00}");
    }

    void testDurations() {
        console.print("\n** testDurations()");

        Duration duration = new Duration("P3DT4H5M6S");
        console.print($"duration={duration} or {Duration:P3DT4H5M6S}");

        duration = new Duration("1DT1H1M1.23456S");
        console.print($"duration={duration} or {Duration:P1DT1H1M1.23456S}");

        duration = new Duration("PT10S");
        console.print($"PT10S duration={duration} or {Duration:PT10S}");

        duration = new Duration("10S");
        console.print($"10S duration={duration} or {Duration:10S}");

        duration = new Duration("PT10.5S");
        console.print($"PT10.5S duration={duration} or {Duration:PT10.5S}");

        duration = new Duration("P10.5S");
        console.print($"P10.5S duration={duration} or {Duration:P10.5S}");

        duration = new Duration("T10.5S");
        console.print($"T10.5S duration={duration} or {Duration:T10.5S}");

        duration = new Duration("10.5S");
        console.print($"10.5S duration={duration} or {Duration:10.5S}");
    }

    void testLexer() {
        console.print("\n** testLexer()");

        import ecstasy.lang.ErrorList;
        import ecstasy.lang.src.Lexer;
        import ecstasy.lang.src.Lexer.Token;
        import ecstasy.lang.src.Source;

//        String     text   = \|Version v = v:1.0;
//                             ;
//        Source     source = new Source(text);
//        ErrorList  errs   = new ErrorList(100);
//        Lexer      lexer  = new Lexer(source, errs);
//        console.print($"lexer={lexer}");
//        Loop: for (Token token : lexer)
//            {
//            console.print($"[{Loop.count}] {token}");
//            }
//        }

        File       file   = ./literals.x;
        Source     source = new Source(file);
        ErrorList  errs   = new ErrorList(100);
        Lexer      lexer  = new Lexer(source, errs);
        console.print($"lexer={lexer}");
        Loop: for (Token token : lexer) {
            console.print($"[{Loop.count}] {token}");
        }
    }

    void testInterval() {
        console.print("\n** testInterval()");
        assert (1>..1).empty;
        assert (1>..1).size == 0;
        assert (1..<1).empty;
        assert (1..<1).size == 0;
        assert (1>..<1).empty;
        assert (1>..<1).size == 0;
        assert !(1..1).empty;
        assert (1..1).size == 1;
        assert !(1..2).empty;
        assert (1..2).size == 2;
    }
}