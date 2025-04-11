module TestSimple {
    @Inject static Console console;
    @Inject Clock clock;
    @Inject Timer timer;

    import ecstasy.lang.ErrorList;
    import ecstasy.lang.src.Parser;
    import ecstasy.lang.src.Lexer;
    import ecstasy.lang.src.Lexer.Token;

    package xml import xml.xtclang.org;
    import xml.*;

    static void out(Object o = "") {
        console.print(o);
    }

    void run(String[] args = []) {
        String[] tests = [
            "<test/>",
            "<test></test>",
            "<test>stuff</test>",
        ];
//        assert:debug;
        for (String test : tests) {
            parseXml(test);
        }
    }

    void parseXml(String text) {
        (Document? doc, ErrorList errs) = xml.parse(text);
        out($"{text=}, {doc=}, {errs=}");
    }
}
