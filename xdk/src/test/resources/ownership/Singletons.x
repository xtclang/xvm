/**
 * Unshared application modules own fresh singleton state in each nested container and request.
 * The current linker implements Lightweight with core sharing only; explicit runtime sharing
 * is covered separately by the Java ownership tests.
 */
module SingletonOwnership {
    import ecstasy.mgmt.Container;
    import ecstasy.mgmt.ModuleRepository;
    import ecstasy.mgmt.PassThroughResourceProvider;

    void run() {
        Container first = child();
        assert first.invoke("increment")[0].as(Int) == 1;
        assert first.invoke("increment")[0].as(Int) == 2;
        assert increment() == 1;

        Container second = child();
        assert second.invoke("increment")[0].as(Int) == 1;
        assert first.invoke("nestedIncrement")[0].as(Int) == 1;
        assert first.invoke("increment")[0].as(Int) == 3;
        assert increment() == 2;

        assert first.invoke("checkSwitch")[0].as(Boolean);
        Int firstToken = first.invoke("tokenValue")[0].as(Int);
        assert first.invoke("tokenValue")[0].as(Int) == firstToken;
        assert checkSwitch();
        Int ownToken = tokenValue();
        assert ownToken != firstToken;
        assert second.invoke("checkSwitch")[0].as(Boolean);
        Int secondToken = second.invoke("tokenValue")[0].as(Int);
        assert secondToken != firstToken;
        assert secondToken != ownToken;
        assert first.invoke("tokenValue")[0].as(Int) == firstToken;

        for (Int i : 0..<2) {
            Boolean failed = False;
            try {
                readFailedSingleton();
            } catch (Exception e) {
                failed = True;
            }
            assert failed;
        }
        assert Attempts.count == 2;
    }

    Container child() {
        @Inject("repository") ModuleRepository repository;
        val template = repository.getResolvedModule("SingletonOwnership");
        return new Container(template, Lightweight, repository, new PassThroughResourceProvider());
    }

    Int increment() = Counter.next();

    Int nestedIncrement() = child().invoke("increment")[0].as(Int);

    Boolean checkSwitch() {
        Int before = Token.value;
        assert matches(Token);
        assert Token.value == before;
        return True;
    }

    Int tokenValue() = Token.value;

    Boolean matches(Object value) {
        switch (value) {
        case Token:
            return True;
        default:
            return False;
        }
    }

    static const Token {
        Int value = Counter.next();
    }

    void readFailedSingleton() {
        Failing.touch();
    }

    static service Failing {
        construct() {
            Attempts.record();
            throw new IllegalState("Expected initialization failure");
        }

        void touch() {}
    }

    static service Attempts {
        Int count = 0;
        void record() { ++count; }
    }

    static service Counter {
        Int value = 0;
        Int next() = ++value;
    }
}
