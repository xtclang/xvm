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
        // version = new Version(version, 0, "20130313144700");
        console.println("version=" + version);

        version = new Version(version, Alpha);
        console.println("version=" + version);

        version = new Version(version, 2);
        console.println("version=" + version);

        for (Int i : 0..3)
            {
            console.println("version[" + i + "]=" + version[i]);
            }

        console.println("version[1..2]=" + version[1..2]);
        console.println("version[0..1]=" + version[0..1]);
        console.println("--version=" + --version);
        console.println("++version=" + ++version);

        for (String s : ["1", "alpha", "1.0", "beta2", "5.6.7.8-alpha", "1.2-beta5", "1.2beta5"])
            {
            console.println("version for String " + s + "=" + new Version(s));
            }

        // "1.2-beta3" to "1.2-beta5"
        console.println("steps from 1.2-beta to 1.2-beta5="
                + new Version("1.2-beta").stepsTo(new Version("1.2-beta5")));
        console.println("steps from 1.2-beta3 to 1.2-beta="
                + new Version("1.2-beta3").stepsTo(new Version("1.2-beta")));
        console.println("steps from 1.2-beta3 to 1.2-beta5="
                + new Version("1.2-beta3").stepsTo(new Version("1.2-beta5")));
        }
    }