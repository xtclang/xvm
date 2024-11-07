module TestSimple {

    import ecstasy.collections.NaturalHasher;

    @Inject Console console;

    void run() {
        Char a = 'a';
        Char b = 'b';
        console.print(a.hashCode());
        console.print(new NaturalHasher<Char>().hashOf(a));

        console.print(a==b);
        console.print(new NaturalHasher<Char>().areEqual(a, b));

        console.print(a<=>b);

        try {
            Hashable h = obfuscate(a);
            console.print(h.hashCode());
            }
        catch (Exception e) {
            console.print(e);
        }

        try {
            Const c = obfuscate(a);
            console.print(c.hashCode());
            }
        catch (Exception e) {
            console.print(e);
        }

        try {
            Orderable c1 = obfuscate(a);
            Orderable c2 = obfuscate(b);
            console.print(c1 <=> c2);
            }
        catch (Exception e) {
            console.print(e);
        }

        try {
            Const c1 = obfuscate(a);
            Const c2 = obfuscate(b);
            console.print(c1 == c2);
            }
        catch (Exception e) {
            console.print(e);
        }

        try {
            Const c1 = obfuscate(a);
            Const c2 = obfuscate(b);
            console.print(c1 <=> c2);
            }
        catch (Exception e) {
            console.print(e);
        }
    }

    <T> T obfuscate(Object o) = o.as(T);
}