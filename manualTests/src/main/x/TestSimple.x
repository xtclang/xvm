module TestSimple {
    @Inject Console console;

    void run() {
        Derived d = new Derived(17);
        d.test();
    }

    @Abstract
    class Base {
        @Abstract protected Int count;

        void test() {
            count = 42; // this used to fail to compile
        }
    }

    class Derived(Int count) extends Base {
        @Override
        protected Int count;
    }
}