module TestSimple
    {
    @Inject Console console;

    package crypto import crypto.xtclang.org;

    import crypto.*;
    import crypto.KeyStore.Info;

    void run(String[] args = ["password"])
        {
        @Inject Directory curDir;
        File storeHello = curDir.fileFor("data/hello/https.p12");
        reportKeyStore(storeHello, args[0]);

        @Inject Directory homeDir;
        File storePlatform = homeDir.fileFor("xqiz.it/platform/certs.p12");
        reportKeyStore(storePlatform, args[0]);
        }

    void reportKeyStore(File store, String password)
        {
        @Inject(opts=new Info(store.contents, password)) KeyStore keystore;

        for (Certificate cert : keystore.certificates)
            {
            console.println($"certificate={cert}");
            if (CryptoKey key := cert.containsKey())
                {
                console.println($"key={key}\n");
                }
            }
        }
    }