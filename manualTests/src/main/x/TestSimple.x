module TestSimple {
    @Inject Console console;

    void run() {
        String[] strings = new String[];
        String[] other = ["a", "bb"];

        strings += other.filter(s -> s.size == 1); // this used to fail to compile

        console.print(strings);
    }
}