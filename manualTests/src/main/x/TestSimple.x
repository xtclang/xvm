module TestSimple {
    @Inject Console console;

    void run() {
        console.print(switch("") { // this used to NPE the compiler
          default: "Hello World";
        });
    }
}
