module TestSimple {

    @Inject Console console;

    void run() {
        Principal[] findUser(String name) {
            return findPrincipals(p -> p.name == name)
                        .map(redact(_))
                        .toArray(Constant);
        }
    }
}