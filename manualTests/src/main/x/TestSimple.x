module TestSimple {
    @Inject Console console;

    package agg import aggregate.xtclang.org;

    import agg.*;

    void run() {
        Sum<Int> sum = new Sum();

        sum.Accumulator acc = sum.init(); // used to fail to compile
        acc.add(1);
        console.print(sum.reduce(acc));
    }
}