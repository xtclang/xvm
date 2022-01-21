module TestSimple.test.org
    {
    @Inject Console console;

    import ecstasy.collections.*;

    void run()
        {
        Int i1 = 3;
        Int i2 = 7;

        Set<Int> s1 = [i1,i2,11];
        Set<Int> s2 = [2,7,12];

        Set<Int> s3 = s1.addAll(s2);
        console.println(s3);

        s1.symmetricDifference(s2); // this used to throw
        console.println(s1);
        }
    }
