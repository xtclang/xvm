module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        Test<Byte> t = new Test(0);
        }

    class Test<Element extends Number>(Element element)
        {
        construct(Element e)
            {
            assert !Element.fixedLength();
            element = e;
            }
        }
    }