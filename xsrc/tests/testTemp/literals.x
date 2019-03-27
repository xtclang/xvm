module TestLiterals.xqiz.it
    {
    import X.rt.Version;

    @Inject Console console;

    void run()
        {
        console.println("*** literal tests ***\n");

        testVersions();
        }

    void testVersions()
        {
        Version version = new Version(null, 1);
        console.println("version=" + version);

        version = new Version(version, 0);
        console.println("version=" + version);

        version = new Version(version, Alpha);
        console.println("version=" + version);

        version = new Version(version, 2);
        console.println("version=" + version);

        for (Int i : 0..3)
            {
            //console.println("version[" + i + "]=" + version[i]);
            console.println("version[" + i + "]=" + version.getElement(i));
            }

//        console.println("version[1..2]=" + version[1..2]);
//        console.println("version[0..1]=" + version[0..1]);
DEBUG;
        console.println("--version=" + --version);
        console.println("++version=" + ++version);
        }
    }