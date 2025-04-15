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
            "<test name='gene' other='cam'>stuff<test>more stuff</test></test>",
            "<test name='gene other='cam'>stuff<test>more stuff</test></test>",
            "<test><![CDATA[hello]]></test>",
            "<test><![CDATA[hello]]></tessst>",
            "<test><![CDATA[hello]]></blah>",
            "<test><test2/>",
            "<xml><xml/>",
            "<deep><deep><deep><deep><deep><deep></deep></deep></deep></deep></deep></deep>",
            "<deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep>",
            "<deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep>",
            "<deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep>",
            "<deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep><deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep></deep>",
        ];
        for (String test : tests) {
//            assert:debug Loop.count != 5;
            parseXml(test);
        }
    }

    void parseXml(String text) {
        (Document? doc, ErrorList errs) = xml.parse(text);
        out($"{text=}, {doc=}, {errs=}");
    }
}
