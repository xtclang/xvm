module DevExample {
    void run(String[] args = []) {
        @Inject Console console;
    	Int len = args.size - 1;
    	if (len < 0) {
	        console.print("There are no arguments: Exiting.");
	        return;
	    }
	    console.print("There are args: " + len);
        for (Int i : 0..len) {
            console.print("Hello, World! (args[" + i + "] = " + args[i] + ")");
        }
    }
}
