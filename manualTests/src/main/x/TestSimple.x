module TestSimple {
    @Inject Console console;

    void run() {
        new Test<Byte>(126).test();
    }

    class Test<Element extends Byte>(Element value) {
        void test() {
            Byte b; b = value;
            Int byteCount = 1 + (b & 0x1F); // that used to blow up at runtime
            console.print(byteCount);
        }
    }
}
