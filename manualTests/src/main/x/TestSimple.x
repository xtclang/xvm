module TestSimple
    {
    @Inject Console console;
    @Inject Timer timer;

    void run()
        {
        Test<Int> t = new Test();
        console.println(t.toByte());

        Bit[] bits = [1, 0, 0, 1];
        console.println(bits.toByte());
        }

    class Test<Element>
        {
        Method<Test, <>, <UInt8>> toByte = toUInt8;

        UInt8 toUInt8()
            {
            console.println("in " + this);
            return 42;
            }
        }
    }
