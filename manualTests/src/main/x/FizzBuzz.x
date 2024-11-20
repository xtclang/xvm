// TODO: Add unit tests for both broken and working code/runtime modules.
// TODO: Add a "test debugger" task to manualTests with the assert:debug enabled, or something.
module TestFizzBuzz {
    // import XUnit so that tests will run
    package xunit import xunit.xtclang.org;

    void run(String[] args = []) {
        @Inject Console console;
        //assert:debug;
        for (Int x : 1..100) {
            console.print(fizzBuzz(x), True);
            console.print(" ", True);
        }
        console.print();
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
        assert fizzBuzz(3) == "Fizz";
        assert fizzBuzz(6) == "Fizz";
        assert fizzBuzz(9) == "Fizz";
        assert fizzBuzz(12) == "Fizz";
        assert fizzBuzz(18) == "Fizz";
        assert fizzBuzz(21) == "Fizz";
    }

    @Test
    void shouldBeBuzz() {
        assert fizzBuzz(5) == "Buzz";
        assert fizzBuzz(10) == "Buzz";
        assert fizzBuzz(20) == "Buzz";
        assert fizzBuzz(25) == "Buzz";
        assert fizzBuzz(35) == "Buzz";
        assert fizzBuzz(40) == "Buzz";
    }

    @Test
    void shouldBeFizzBuzz() {
        assert fizzBuzz(0) == "FizzBuzz";
        assert fizzBuzz(15) == "FizzBuzz";
        assert fizzBuzz(30) == "FizzBuzz";
        assert fizzBuzz(45) == "FizzBuzz";
    }

    @Test
    void shouldBeNumber() {
        assert fizzBuzz(1) == "1";
        assert fizzBuzz(2) == "2";
        assert fizzBuzz(4) == "4";
        assert fizzBuzz(11) == "11";
        assert fizzBuzz(19) == "19";
    }
}
