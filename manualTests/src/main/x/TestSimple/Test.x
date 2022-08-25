class Test
    {
    void f1(List list, Annotation anno)    // this declaration compiled, while
        {
        assert list.Element == Annotation; // this line used to fail to compile
        }
    }