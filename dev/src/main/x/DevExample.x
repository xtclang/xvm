module DevExample {
    void run(String[] args = []) {
        @Inject Console console;
        console.print("Hello, World!");

//        assert:debug;
        if (args.empty) {
	          console.print("There are no arguments: Exiting.");
	          return;
	      }

	      console.print($"There are {args.size} args");
        loop: for (String arg : args) {
            console.print($"Hello, World! (args[{loop.count}] = {arg})");
        }
    }
}
