package org.xvm.compiler;


import java.io.File;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ErrorList;

import org.xvm.compiler.ast.Statement;

/**
 * Test of the Ecstasy parser
 */
public class ParserTest {
    /**
     * Allow for command-line testing
     *
     * @param args  file name
     */
    public static void main(String[] args)
            throws Exception {
        if (args.length < 1 || args[0].isEmpty()) {
            out("file name required");
            return;
        }

        File file = new File(args[0]);
        if (!(file.exists() && file.canRead())) {
            out("cannot read file: " + args[0]);
            return;
        }

        Source source = new Source(file);
        parse(source);
    }

    @Test
    public void testSimpleModule() {
        parse("module Test {}");
    }

    @Test
    public void testSimpleInterface() {
        parse("interface SortedMap extends Map {}");
    }

    @Test
    public void testSimpleDelegates() {
        parse("class DependentFutureRef delegates Ref(value) {}");
    }

    @Test
    public void testGenericTypedef() {
        parse("module Test {class Box<T> {} typedef Alias<T> as Box<T>;}");
    }

    @Test
    public void testMixedTypedefProofModule() {
        parse("""
                module TestTypedefs {
                    typedef Join<A, B> as Tuple<A, B>;
                    typedef Twin<T> as Join<T, T>;
                    typedef MetaSeries<I, T> as Join<I, function T(I)>;
                    typedef Series<T> as MetaSeries<Int, T>;
                    typedef Series2<A, B> as Series<Join<A, B>>;

                    typedef Join<String, String> as ColumnMeta;
                    typedef function ColumnMeta() as ColumnMetaRef;
                    typedef Series2<Any?, ColumnMetaRef> as RowVec;
                    typedef Series<RowVec> as Cursor;
                    typedef Series<Char> as CharStr;
                    typedef Series<CharStr> as Corpus;
                    typedef MetaSeries<ElementState, Set<ElementState>> as LifecycleFSM;

                    enum ElementState {Created, Open, Active, Draining, Closed}

                    void run() {
                        Series<String> names = (3, i -> i == 0 ? "alpha" : i == 1 ? "beta" : "gamma");
                        RowVec row = (2, i -> i == 0 ? ("42", () -> ("id", "Int")) : ("bob", () -> ("name", "String")));
                        Cursor cursor = (1, i -> row);
                        LifecycleFSM lifecycle = (ElementState.Created, state -> {
                            switch (state) {
                            case Created:  return Set:[ElementState.Open];
                            case Open:     return Set:[ElementState.Active, ElementState.Draining];
                            case Active:   return Set:[ElementState.Draining];
                            case Draining: return Set:[ElementState.Closed];
                            case Closed:   return Set:[ElementState.Closed];
                            }
                        });

                        assert names[1](2) == "gamma";
                        assert cursor[1](0)[1](1)[1]()[0] == "name";
                        assert lifecycle[1](ElementState.Draining) == Set:[ElementState.Closed];
                    }
                }
                """);
    }

    static void parse(String value) {
            parse(new Source(value));
    }

    static void parse(Source source) {
        ErrorList errlist = new ErrorList(5);
        Parser parser = new Parser(source, errlist);

        Statement stmt = parser.parseSource();
        out(stmt);

        out("error list (" + errlist.getSeriousErrorCount()
                + " of " + errlist.getSeriousErrorMax() + ", sev="
                + errlist.getSeverity() + "):");

        errlist.getErrors().forEach(ParserTest::out);

        if (errlist.getSeriousErrorCount() > 0) {
            throw new AssertionError("parse produced " + errlist.getSeriousErrorCount() + " serious errors");
        }
    }

    /**
     * Debug output.
     *
     * @param o  something to print
     */
    static void out(Object o) {
        System.out.println(o);
    }
}
