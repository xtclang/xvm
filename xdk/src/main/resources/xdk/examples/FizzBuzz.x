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
            console.print(fizzBuzz(x));
        }
    }

    String fizzBuzz(Int x) {
        return switch (x % 3, x % 5) {
            case (0, 0): "FizzBuzz";
            case (0, _): "Fizz";
            case (_, 0): "Buzz";
            case (_, _): x.toString();
        };
    }

    @Test
    void shouldBeFizz() {
        assert:test fizzBuzz(3) == "Fizz";
        assert:test fizzBuzz(6) == "Fizz";
        assert:test fizzBuzz(9) == "Fizz";
    }

    @Test
    void shouldBeBuzz() {
        assert:test fizzBuzz(5) == "Buzz";
        assert:test fizzBuzz(10) == "Buzz";
        assert:test fizzBuzz(20) == "Buzz";
    }

    @Test
    void shouldBeFizzBuzz() {
        for (Int x : 1..10) {
            assert:test fizzBuzz(x * 3 * 5) == "FizzBuzz";
        }
    }

    @Test
    void shouldBeNumber() {
        assert:test fizzBuzz(1) == "1";
        assert:test fizzBuzz(2) == "2";
        assert:test fizzBuzz(4) == "4";
    }
}
