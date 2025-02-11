module TestSimple {

    @Inject static Console console;

    void run() {
        Map<Int, String> map = new HashMap([Int:1="A"]); // this used to fail to compile

        console.print(map);
    }
}