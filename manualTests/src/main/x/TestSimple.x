module TestSimple {
    @Inject Console console;

    void run() {
        console.print(strings1[42]);    // used to throw a CCE
        console.print(strings2[index]); // ditto
    }

    static String[] strings1 = new String[1024] (i -> "" + i);
    static String[] strings2 = ["Hello", "World"];
    static Int      index    = strings1.size.notGreaterThan(strings2.size) - 1;

}
