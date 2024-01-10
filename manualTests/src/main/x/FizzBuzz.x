// TODO: Add unit tests for both broken and working code/runtime modules.
// TODO: Add a "test debugger" task to manualTests with the assert:debug enabled, or something.
module TestFizzBuzz {
    void run(String[] args = []) {
        @Inject Console console;

        loop: for (String arg : args) {
            console.print($"TestFizzBuzzArgument: (args[{loop.count}] = {arg})");
        }

        //assert:debug;

        for (Int x : 1..100) {
            console.print(
                switch (x % 3, x % 5) {
                case (0, 0): "FizzBuzz";
                case (0, _): "Fizz";
                case (_, 0): "Buzz";
                case (_, _): x.toString();
                }, True);
            console.print(" ", True);
        }
        console.print();
    }
}
