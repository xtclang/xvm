module TestSimple {

    package json import json.xtclang.org;

    @Inject Console console;

    import json.*;

    void run() {

        Test t = new Test();
        console.print(t.getObject());
        console.print(t.getDoc());

    }

    service Test {

        JsonObject getObject(Int i = 1) {
            JsonObject o = json.newObject();
            o["a"] = i /*this will not be needed*/.toIntLiteral();
            return o; // no need to freeze
        }

        Doc getDoc(Dec d = 1) {
            JsonObject o = json.newObject();
            o["a"] = d /*this will not be needed*/.toFPLiteral();
            return o;
        }
    }
}