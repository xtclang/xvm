module TestSimple
    {
    @Inject Console console;

    void run( )
        {
        String s1 = $./TestSimple.x;
//        String s2 = $./TestSimple.y;      // parser error (the file does not exist)

        Byte[] b1 = #./TestSimple.x;
//        Byte[] b2 = #./TestSimple.y;      // parser error (the file does not exist)

//        Object o1 = ./TestSimple.x;       // compiler error (ambiguous type)
        Object o2 = ./TestSimple.y;         // assumed to be path (the file does not exist)
        Object o3 = Path:./TestSimple.x;
        Object o4 = Path:./TestSimple.y;

//        Exception o5 = ./TestSimple.y;    // type mismatch
//        Exception o6 = ./TestSimple.x;    // type mismatch

//        File|Path u1 = ./TestSimple.x;    // ambiguous
        File|Path u1 = ./TestSimple.y;      // ok (can only be a path)

        Path   p1 = ./TestSimple.x;
        Path   p2 = ./TestSimple.y;
        Path   p3 = Path:./TestSimple.x;
        Path   p4 = Path:./TestSimple.y;

        File   f1 = ./TestSimple.x;
//        File   f2 = ./TestSimple.y;       // compiler error (file does not exist, so type is Path)
        File   f3 = File:./TestSimple.x;
//        File   f4 = File:./TestSimple.y;  // parser error (the file does not exist)

        Directory d1  = ./;
//        Directory d2  = ./doesnotexist;   // type mismatch; right side is inferred as Path
//        Directory d3  = ./doesnotexist/;  // type mismatch; right side is inferred as Path
        Directory d4  = Directory:./;
//        Directory d5  = Directory:./doesnotexist;     // parser error (the dir does not exist)
//        Directory d6  = Directory:./doesnotexist/;    // parser error (the dir does not exist)

        FileStore fs1 = ./;
//        FileStore fs2 = ./doesnotexist;   // type mismatch; right side is inferred as Path
//        FileStore fs3 = ./doesnotexist/;  // type mismatch; right side is inferred as Path
        FileStore fs4 = FileStore:./;
//        FileStore fs5 = FileStore:./doesnotexist;     // parser error (the dir does not exist)
//        FileStore fs6 = FileStore:./doesnotexist/;    // parser error (the dir does not exist)
        }

    service Test(Int n)
        {
        Int foo()
            {
            for (Int i : 1..10)
                {
                console.println($"in Test({n}) foo #{i}");
                }
            return n;
            }
        }
    }