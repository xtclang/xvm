module TestSimple {

    @Inject static Console console;

    void run() {
        test(Null);
        test(Version:2.0);
    }

    void test(Version? xmlVersion) {
        StringBuffer buf = new StringBuffer();
        (xmlVersion ?: "1.0").appendTo(buf); // this used to fail to compile

        console.print(buf.toString());
    }
}