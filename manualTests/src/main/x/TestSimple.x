module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        Int[] ints = [1, 2];
        assert ints.mutability == Constant;
        ints = ints.reversed(True);
        assert ints.mutability == Constant;
        ints = ints.reify(Mutable);
        assert ints.mutability == Mutable;
        ints = ints.reversed();
        assert ints.mutability == Mutable;
        ints = ints.reify(Fixed);
        assert ints.mutability == Fixed;
        ints = ints.reversed();
        assert ints.mutability == Fixed;
        ints = ints.reify(Constant);
        assert ints.mutability == Constant;


//        Byte[] bytes = ints.asByteArray();
//        console.println(bytes);
        }
    }