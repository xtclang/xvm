module TestSimple
    {
    @Inject Console console;

    void run()
        {
        Test t = new Test();

        console.print($"{t=}"); // this used to blow at runtime
        }

    service Test
            implements Stringable
        {
        @Override
        Appender<Char> appendTo(Appender<Char> buf)
            {
            return buf.add('T');
            }
        }
    }