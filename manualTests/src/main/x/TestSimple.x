module TestSimple {
    package json import json.xtclang.org;

    import json.*;
    import json.ObjectOutputStream.*;

    @Inject Console console;

    void run() {
        // dump(@CloseCap @PointerAwareElementOutput ArrayOutputStream<ElementOutputStream>);
        dump(ListMap<String, Int>);
    }

    void dump(Type t) {
        console.print(t.dump());
    }
}
