module TestSimple {

    package json import json.xtclang.org;

    @Inject Console console;

    import json.Schema;
    import ecstasy.io.*;

    void run() {
         String str = \|{"a":"0", "b":"1"}
                       ;
         Map<String, String> config =
            Schema.DEFAULT.createObjectInput(new UTF8Reader(new ByteArrayInputStream(str.utf8()))).read();

         console.print(config);
    }
}