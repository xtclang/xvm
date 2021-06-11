module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        }

    class Test<Element>
            implements Const
        {
        construct()
            {
            }

        Element value;

        @Override
        Appender<Char> appendTo(Appender<Char> buf)
            {
            if (Element.is(Type<Char>))
                {
                value.appendEscaped(buf);
                }
            else if (Element == String) // this used to blow with an assertion
                {
                value.appendEscaped(buf);
                }
            return buf;
            }
        }
    }