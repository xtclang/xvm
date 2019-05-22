module TestLiterals.xqiz.it
    {
    import X.rt.Version;

    @Inject Console console;

    void run()
        {
        console.println("*** literal tests ***\n");

        testVersions();
        testIncludes();
        testMultiline();
        testMultilineTemplate();
        testHex();
        testDirs();
        }

    void testVersions()
        {
        console.println("\n** test Versions()");

        Version version = new Version(null, 1);
        console.println("version=" + version);

        version = new Version(version, 0);
        // version = new Version(version, 0, "20130313144700");
        console.println("version=" + version);

        version = new Version(version, Alpha);
        console.println("version=" + version);

        version = new Version(version, 2);
        console.println("version=" + version);

        for (Int i : 0..3)
            {
            console.println("version[" + i + "]=" + version[i]);
            }

        console.println("version[1..2]=" + version[1..2]);
        console.println("version[0..1]=" + version[0..1]);
        console.println("--version=" + --version);
        console.println("++version=" + ++version);

        for (String s : ["1", "alpha", "1.0", "beta2", "5.6.7.8-alpha", "1.2-beta5", "1.2beta5"])
            {
            console.println("version for String " + s + "=" + new Version(s));
            }

        // "1.2-beta3" to "1.2-beta5"
        console.println("steps from 1.2-beta to 1.2-beta5="
                + new Version("1.2-beta").stepsTo(new Version("1.2-beta5")));
        console.println("steps from 1.2-beta3 to 1.2-beta="
                + new Version("1.2-beta3").stepsTo(new Version("1.2-beta")));
        console.println("steps from 1.2-beta3 to 1.2-beta5="
                + new Version("1.2-beta3").stepsTo(new Version("1.2-beta5")));
        }

    void testIncludes()
        {
        console.println("\n** testIncludes()");

        String s = ./literals.x;
        console.println($"./literals.x={s}");
        }

    void testMultiline()
        {
        console.println("\n** testMultiline()");

        String s = `|<%@ taglib uri="/WEB-INF/jspwiki.tld" prefix="wiki" %>
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
        console.println($"s={s}");
        }

    void testMultilineTemplate()
        {
        console.println("\n** testMultiline()");

        const Person(String firstname, String lastname);
        Person person = new Person("Bob", "Smith");

        String s = $|# TOML doc
                    |[name]
                    |first = "{person.firstname}"
                    |last = "{person.lastname}"
                    ;

        console.println($"\nTOML=\n{s}");

        s = $|\{
             |"person": \{
             |  "first": "{person.firstname}"
             |  "last": "{person.lastname}"
             |  }
             |}
             ;

        console.println($"\nJSON=\n{s}");
        }

    void testHex()
        {
        console.println("\n** testHex()");

        Byte[] bytes = #123_4567_89aB_cDeF;
        console.println($"bytes={bytes}");

        bytes = #|0123456789aBcDeF 0123456789aBcDeF 0123456789aBcDeF 0123456789aBcDeF
                 |0123456789aBcDeF_0123456789aBcDeF_0123456789aBcDeF_0123456789aBcDeF
                 ;
        console.println($"bytes={bytes[0..10]}...{bytes[bytes.size-10..bytes.size-1]}");

        bytes = #/testLiterals.xtc;
        // console.println($"bytes={bytes[0..10]}..{bytes[bytes.size-10..bytes.size-1]"); TODO CP - infinite loop in lexer?
        console.println($"bytes={bytes[0..10]}...{bytes[bytes.size-10..bytes.size-1]}");
        }

    void testDirs()
        {
        console.println("\n** testDirs()");

        FileStore fs = /resources/;
        console.println("fs=" + fs);

        for (String s : fs.root.names())
            {
            console.println("name=" + s);
            }
        }
    }