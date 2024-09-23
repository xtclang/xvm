module TestSimple {
    @Inject Console console;

    void run() {
        console.print(test());
    }

    Int test() {
        (Int year, _, Int day, _) = calcDate();
        return year;
    }

    static (Int32 year, Int32 month, Int32 day, Int32 dayOfYear) calcDate() {
        return 2024, 9, 16, 276;
    }
}
