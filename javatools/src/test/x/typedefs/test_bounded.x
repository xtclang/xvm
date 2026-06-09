module test_bounded {
    typedef <T extends Number> T as NumRef<T>;

    void run() {
        NumRef<Int> n = NumRef<Int>(42);
    }
}