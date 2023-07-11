module TestSimple {
    @Inject Console console;

    package json import json.xtclang.org;

    import json.*;

    void run() {
         String str = \|{
                       |"host":"admin.xqiz.it",
                       |"http":8080,
                       |"https":8090
                       |}
                       ;
        Doc doc = new Parser(str.toReader()).parseDoc(); // used to fail to compile (unknown type "Doc")
        assert doc.is(Map<String, Doc>);
        console.print(doc);
    }
}