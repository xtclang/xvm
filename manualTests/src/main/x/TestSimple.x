module TestSimple
    {
    @Inject Console console;

    interface FromString
        {
        construct(String s);
        }

    const B(String s)
            implements FromString
        {
        }

    const D(String s) extends B(s + " D")
        {
        }

    void run()
        {
        console.println(foo(B));
        console.println(foo(D));
        console.println(bar(B));
        console.println(bar(D));
        console.println(new Test<B>().foo());
        console.println(new Test<D>().foo());
        }

    <Element extends FromString> Element foo(Type<Element> type)
        {
        return new Element("hello");
        }

    Object bar(Type<FromString> type)
        {
        return new type.DataType("hello");
        }

    class Test<Value extends FromString>
        {
        Value foo()
            {
            return new Value("hello");
            }
        }
    }