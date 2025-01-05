module TestSimple {

    @Inject Console console;

    void run() {
    }

    interface Iface {
        void test();
    }

    class Test(Iface other)
            implements Iface
            delegates Iface(other) {

        @Override
        void test() {
            return super(); // this used to fail to compile
        }
    }
}