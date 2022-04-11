module TestSimple
    {
    @Inject Console console;

    package oodb import oodb.xtclang.org;

    void run()
        {
        }

    class TestC
            implements Stringable | Random  // this used to cause an assertion
        {
        String name;
        }

    interface TestI
            extends Stringable | Random   // this used to cause an assertion
        {
        String name;
        }
    }