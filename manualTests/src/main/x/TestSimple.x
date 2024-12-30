module TestSimple {

    package cli     import cli.xtclang.org;
    package webauth import webauth.xtclang.org;

    @Inject Console console;

    void run() {
        Class c = webauth;
        assert c.PublicType.isA(cli.TerminalApp); // this used to throw
    }
}