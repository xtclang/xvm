module test {
    @Inject Console console;

    void run() {
        Number[] numbers = [
                Int:123,
                Int:-123,
               UInt:123,
                Dec:123,
                Dec:-123,
            Float32:123,
            Float32:123.001,
            Float32:-123,
            Float32:-123.001,
        ];

        for (Int i : 0 ..< numbers.size) {
            for (Int j : i ..< numbers.size) {
                Number n1 = numbers[i];
                Number n2 = numbers[j];
                console.print($"{&n1.actualType}:{n1} {(n1<=>n2).symbol} {&n2.actualType}:{n2}");
            }
        }
    }
}