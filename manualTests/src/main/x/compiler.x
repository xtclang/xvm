module TestCompiler
    {
    @Inject Console console;

    void run()
        {
        @Inject Directory curDir;

        assert File|Directory sourceFile := curDir.find("src/main/x/errors.x");
        assert sourceFile.is(File);

        assert File|Directory buildDir := curDir.find("build");
        assert buildDir.is(Directory);

        console.println($|compile module : {sourceFile.path}
                         |build directory: {buildDir}
                         |
                       );

        compile(sourceFile, buildDir);
        }

    void compile(File sourceModule, Directory buildDir)
        {
        @Inject ecstasy.lang.src.Compiler compiler;

        compiler.setResultLocation(buildDir);

        (Boolean success, String errors) = compiler.compile([sourceModule]);

        if (success)
            {
            console.println("Done!");
            }
        else
            {
            console.println(errors);
            }
        }
    }