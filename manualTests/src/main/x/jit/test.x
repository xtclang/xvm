module test.examples.org {

    @Inject Console console;

    Int run(String[] args = []) {
        for (String arg : args) {
            console.print(arg, True);
            console.print(" ", True);
        }
        console.print("\nType \"echo $?\" to see the args count");
        return args.size;
    }
}
