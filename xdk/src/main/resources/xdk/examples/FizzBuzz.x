/**
 * You can read more about the FizzBuzz test here:
 *
 *     https://wiki.c2.com/?FizzBuzzTest
 *
 * To compile this example, execute the following command line from within the directory containing
 * this file:
 *
 *     xtc FizzBuzz
 *
 * Then, to run this example:
 *
 *     xec FizzBuzz
 */
module FizzBuzz {
    void run() {
        @Inject Console console;
        for (Int x : 1..100) {
            console.print(switch (x % 3, x % 5) {
                case (0, 0): "FizzBuzz";
                case (0, _): "Fizz";
                case (_, 0): "Buzz";
                case (_, _): x.toString();
            });
        }
    }
}
