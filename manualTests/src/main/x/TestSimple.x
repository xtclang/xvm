module TestSimple.test.org
    {
    @Inject Console console;

    void run( )
        {
        Test<Byte> t = new Test(0);
        test(t);
        }

    class Test<Element extends Number>(Element element)
        {
        construct(Element e)
            {
            assert Element.fixedLength();
            element = e;
            }
        }

    <CompileType extends Test> void test(CompileType test)
        {
        assert CompileType.Element.fixedLength();

        Type<Number> type1 = test.&element.actualType.as(Type<Number>);

        assert type1.DataType.fixedLength();
        }
    }