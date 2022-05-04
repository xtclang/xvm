module TestCompiler
    {
    @Inject ecstasy.lang.src.Compiler compiler;
    @Inject Console console;

    void run()
        {
        @Inject Directory curDir;

        assert File|Directory sourceFile := curDir.find("src/main/x/errors.x");
        assert sourceFile.is(File);

        assert File|Directory buildDir := curDir.find("build");
        assert buildDir.is(Directory);

        compile(sourceFile, buildDir);

        assert sourceFile := curDir.find("src/main/x/TestSimple.x");
        assert sourceFile.is(File);

        compile(sourceFile, buildDir);
        }

    void compile(File sourceModule, Directory buildDir)
        {
        console.println($|
                         |compile module : {sourceModule.path}
                         |build directory: {buildDir}
                       );

        compiler.setResultLocation(buildDir);

        (Boolean success, String[] errors) = compiler.compile([sourceModule]);

        console.println(success ? "Compiled successfully" : "Compilation failed:");
        for (String error : errors)
            {
            console.println(error);
            }
        }
    }