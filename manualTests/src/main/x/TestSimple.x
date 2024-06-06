module TestSimple {
    void run() {
    }

    void test1(Int? i) {
        assert 2 < i;      // this used to compile, but it should not
    }
    void test2(Int? i) {
        assert 2 < i < 7;  // this used to compile, but it should not
    }
}
