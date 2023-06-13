module TestSimple {
    @Inject Console console;

    void run() {
        String[] strings = [];
        String[] other = ["a", "bb"];
        strings = strings.addAll(other.filter(s -> s.size == 1)); // this used to fail to compile
        console.print(strings);
    }
}