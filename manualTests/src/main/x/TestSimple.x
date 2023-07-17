module TestSimple.examples.org {
    @Inject Console console;

    void run() {
        String[] strings = ["abc", "def"];

        Test<String> t = new Test(strings);
        String[] filtered = t.filter(s -> s.indexOf("e")).toArray(Constant); // that used to fail at RT
        console.print(filtered);
    }

    service Test<Element>(Collection<Element> underlying)
        implements Collection<Element>
        delegates Collection(underlying) {
    }
}