module TestSimple
    {
    @Inject Console console;

    import crypto.xtclang.org as crypto;

    import crypto.*;

    void run()
        {
        @Inject Algorithms algorithms;
        assert Signer md5 := algorithms.hasherFor("MD5");
        assert Signer sha_256 := algorithms.hasherFor("SHA-256");
        assert Signer sha_512 := algorithms.hasherFor("SHA-512");
        Signer sha_512_256 = new TruncatingSigner(sha_512, 256/8);

        Signer[] signers = [md5, sha_256, sha_512, sha_512_256];
        test(signers, "Hello world!");
        }

    void test(Signer[] signers, String text)
        {
        Byte[] message = text.utf8();
        for (Int i = 0, c = signers.size; i < c; ++i)
            {
            Signer    signer = signers[i];
            Signature digest = signer.sign(message);
            assert signer.verify(digest, message);
            }
        }
    }

// TODO GG:
//Exception in thread "main" java.lang.StackOverflowError
//	at org.xvm.compiler.ast.NameResolver.resolveImport(NameResolver.java:529)
//	at org.xvm.compiler.ast.NameResolver.resolve(NameResolver.java:164)
//	at org.xvm.compiler.ast.NameResolver.resolveImport(NameResolver.java:530)
//	at org.xvm.compiler.ast.NameResolver.resolve(NameResolver.java:164)
//	at org.xvm.compiler.ast.NameResolver.resolveImport(NameResolver.java:530)
//	at org.xvm.compiler.ast.NameResolver.resolve(NameResolver.java:164)
//	at org.xvm.compiler.ast.NameResolver.resolveImport(NameResolver.java:530)
//	at org.xvm.compiler.ast.NameResolver.resolve(NameResolver.java:164)
