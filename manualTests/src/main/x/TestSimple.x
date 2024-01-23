module TestSimple {
    @Inject Console console;

    package json import json.xtclang.org;

    void run() {
        console.print(json.Schema.DEFAULT); // this used to NPE at runtime
    }
}

