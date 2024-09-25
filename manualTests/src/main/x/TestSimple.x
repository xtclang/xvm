module TestSimple {
    @Inject Console console;

    package agg import aggregate.xtclang.org;

    import agg.*;

    void run() {
        Int[] ints = [1, 3, 2, 5, 8, 9];

        Int? r2 = ints.filter(i -> i%2 == 0).reduce(new Max()); // this used to complain to std err
        Int? r7 = ints.filter(i -> i%7 == 0).reduce(new Max());
        console.print($"{r2=} {r7=}");
    }
}
