module TestTypedefs {
    @Inject ecstasy.io.Console console;

    typedef Join<A, B> as Tuple<A, B>;
    typedef Twin<T> as Join<T, T>;
    typedef MetaSeries<I, T> as Join<I, function T(I)>;
    typedef Series<T> as MetaSeries<Int, T>;
    typedef Series2<A, B> as Series<Join<A, B>>;

    typedef Join<String, String> as ColumnMeta;
    typedef function ColumnMeta() as ColumnMetaRef;
    typedef Join<Any?, ColumnMetaRef> as Cell;
    typedef function Cell(Any?) as CellFactory;
    typedef Series<Cell> as RowVec;
    typedef function RowVec(Any?, Any?) as RowFactory;
    typedef Series<RowVec> as Cursor;

    typedef Series<Char> as CharStr;
    typedef Series<CharStr> as Corpus;
    typedef MetaSeries<ElementState, Set<ElementState>> as LifecycleFSM;

    enum ElementState {Created, Open, Active, Draining, Closed}

    static <A, B> Join<A, B> joinOf(A left, B right) {
        return (left, right);
    }

    static <T> Twin<T> twinOf(T left, T right) {
        return joinOf(left, right);
    }

    static ColumnMeta metaOf(String name, String type) {
        return joinOf(name, type);
    }

    static ColumnMetaRef metaRefOf(String name, String type) {
        return () -> metaOf(name, type);
    }

    static <I, T> MetaSeries<I, T> metaSeriesOf(I bound, function T(I) indexer) {
        return joinOf(bound, indexer);
    }

    static <T> Series<T> seriesOf(Int size, function T(Int) indexer) {
        return metaSeriesOf(size, indexer);
    }

    static Cell cellOf(Any? value, ColumnMetaRef meta) {
        return joinOf(value, meta);
    }

    static CellFactory cellFactoryOf(String name, String type) {
        ColumnMetaRef meta = metaRefOf(name, type);
        return value -> cellOf(value, meta);
    }

    static RowFactory rowFactoryOf(CellFactory first, CellFactory second) {
        return (left, right) -> (rowBuilder() + first(left) + second(right)).build();
    }

    static RowVec rowOf(Int width, function Cell(Int) cells) {
        return seriesOf(width, cells);
    }

    static Cursor cursorOf(Int height, function RowVec(Int) rows) {
        return seriesOf(height, rows);
    }

    static RowBuilder rowBuilder() {
        return new RowBuilder();
    }

    static CursorBuilder cursorBuilder() {
        return new CursorBuilder();
    }

    static CharStr textOf(String text) {
        return seriesOf(text.size, i -> text[i]);
    }

    static Corpus corpusOf(Int lines, function CharStr(Int) rows) {
        return seriesOf(lines, rows);
    }

    static LifecycleFSM lifecycleOf(ElementState start, function Set<ElementState>(ElementState) next) {
        return metaSeriesOf(start, next);
    }

    static <I, T> I boundOf(MetaSeries<I, T> series) {
        return series[0];
    }

    static <I, T> T at(MetaSeries<I, T> series, I index) {
        return series[1](index);
    }

    static <I, T, U> MetaSeries<I, U> mapSeries(MetaSeries<I, T> series, function U(T) mapper) {
        return metaSeriesOf(boundOf(series), i -> mapper(at(series, i)));
    }

    static RowShape rowViewOf(RowVec row) {
        return new RowShape(row);
    }

    static CursorShape cursorViewOf(Cursor cursor) {
        return new CursorShape(cursor);
    }

    static TextShape textViewOf(CharStr text) {
        return new TextShape(text);
    }

    static const RowBuilder(Cell[] cells = []) {
        @Op("+")
        RowBuilder add(Cell cell) {
            return new RowBuilder(cells.add(cell));
        }

        RowBuilder addVia(CellFactory factory, Any? value) {
            return add(factory(value));
        }

        RowBuilder add(String name, String type, Any? value) {
            return addVia(cellFactoryOf(name, type), value);
        }

        RowVec build() {
            return rowOf(cells.size, i -> cells[i]);
        }
    }

    static const CursorBuilder(RowVec[] rows = []) {
        @Op("+")
        CursorBuilder add(RowVec row) {
            return new CursorBuilder(rows.add(row));
        }

        Cursor build() {
            return cursorOf(rows.size, i -> rows[i]);
        }
    }

    static const RowShape(RowVec row) {
        Int size.get() = boundOf(row);

        @Op("[]")
        Cell getElement(Int index) {
            return at(row, index);
        }

        Any? value(Int index) {
            return getElement(index)[0];
        }

        ColumnMeta meta(Int index) {
            return getElement(index)[1]();
        }
    }

    static const CursorShape(Cursor cursor) {
        Int size.get() = boundOf(cursor);

        @Op("[]")
        RowShape getElement(Int index) {
            return rowViewOf(at(cursor, index));
        }
    }

    static const TextShape(CharStr text) {
        Int size.get() = boundOf(text);

        @Op("[]")
        Char getElement(Int index) {
            return at(text, index);
        }
    }

    static const Shapes() {
        static <A, B> Join<A, B> join(A left, B right) {
            return joinOf(left, right);
        }

        static <T> Series<T> series(Int size, function T(Int) indexer) {
            return seriesOf(size, indexer);
        }

        static Cell cell(Any? value, ColumnMetaRef meta) {
            return cellOf(value, meta);
        }

        static CellFactory cellFactory(String name, String type) {
            return cellFactoryOf(name, type);
        }

        static RowFactory rowFactory(CellFactory first, CellFactory second) {
            return rowFactoryOf(first, second);
        }

        static RowVec row(Int width, function Cell(Int) cells) {
            return rowOf(width, cells);
        }

        static Cursor cursor(Int height, function RowVec(Int) rows) {
            return cursorOf(height, rows);
        }

        static RowBuilder rowBuilder() {
            return new RowBuilder();
        }

        static CursorBuilder cursorBuilder() {
            return new CursorBuilder();
        }

        static CharStr text(String value) {
            return textOf(value);
        }

        static RowShape rowView(RowVec row) {
            return rowViewOf(row);
        }

        static CursorShape cursorView(Cursor cursor) {
            return cursorViewOf(cursor);
        }

        static TextShape textView(CharStr text) {
            return textViewOf(text);
        }

        static <I, T, U> MetaSeries<I, U> map(MetaSeries<I, T> source, function U(T) mapper) {
            return mapSeries(source, mapper);
        }
    }

    void run() {
        console.print("RFC-1 typedef proof");

        testJoin();
        testFactories();
        testInference();
        testBuilders();
        testComposition();
        testShapesWrapper();
        testLifecycle();
    }

    void testJoin() {
        Join<String, Int> pair = ("age", 42);
        assert pair[0] == "age";
        assert pair[1] == 42;

        Twin<Int> twin = twinOf(3, 5);
        assert twin[0] == 3;
        assert twin[1] == 5;
    }

    void testFactories() {
        val names = seriesOf(3, i -> i == 0
                ? "alpha"
                : i == 1
                        ? "beta"
                        : "gamma");
        assert boundOf(names) == 3;
        assert at(names, 0) == "alpha";
        assert at(names, 2) == "gamma";

        val pairs = seriesOf(2, i -> i == 0
                ? joinOf("x", 1)
                : joinOf("y", 2));
        assert boundOf(pairs) == 2;

        Join<String, Int> second = at(pairs, 1);
        assert second[0] == "y";
        assert second[1] == 2;

        ColumnMetaRef idMeta   = metaRefOf("id", "Int");
        ColumnMetaRef nameMeta = metaRefOf("name", "String");
        val           row      = rowOf(2, i -> i == 0
                ? cellOf("42", idMeta)
                : cellOf("bob", nameMeta));
        val cursor = cursorOf(1, _ -> row);
        val hello  = textOf("hello");

        assert boundOf(row) == 2;
        assert boundOf(cursor) == 1;
        assert boundOf(hello) == 5;
        assert at(hello, 1) == 'e';

        Cell       cell0 = at(row, 0);
        ColumnMeta meta0 = cell0[1]();
        assert cell0[0] == "42";
        assert meta0[0] == "id";
        assert meta0[1] == "Int";

        RowVec     firstRow = at(cursor, 0);
        Cell       cell1    = at(firstRow, 1);
        ColumnMeta meta1    = cell1[1]();
        assert cell1[0] == "bob";
        assert meta1[0] == "name";
        assert meta1[1] == "String";
    }

    void testInference() {
        val id      = cellFactoryOf("id", "Int");
        val name    = cellFactoryOf("name", "String");
        val makeRow = rowFactoryOf(id, name);
        val row     = makeRow("42", "bob");
        val cursor  = (cursorBuilder() + row).build();

        assert boundOf(row) == 2;
        assert boundOf(cursor) == 1;
        assert at(at(cursor, 0), 0)[0] == "42";
        assert at(at(cursor, 0), 1)[1]()[0] == "name";
    }

    void testBuilders() {
        val id   = cellFactoryOf("id", "Int");
        val name = cellFactoryOf("name", "String");

        val first  = (rowBuilder() + id("7") + name("eve")).build();
        val second = rowBuilder()
                .addVia(id, "8")
                .addVia(name, "ada")
                .build();
        val cursor = (cursorBuilder() + first + second).build();

        assert boundOf(cursor) == 2;
        assert at(at(cursor, 0), 0)[0] == "7";
        assert at(at(cursor, 1), 1)[0] == "ada";
    }

    void testComposition() {
        val lines = corpusOf(2, i -> i == 0
                ? textOf("alpha")
                : textOf("pi"));
        val lengths = mapSeries(lines, line -> boundOf(line));

        assert boundOf(lengths) == 2;
        assert at(lengths, 0) == 5;
        assert at(lengths, 1) == 2;

        ColumnMetaRef labelMeta = metaRefOf("label", "String");
        val cursor = cursorOf(2, i -> i == 0
                ? rowOf(1, _ -> cellOf("solo", labelMeta))
                : rowOf(2, j -> j == 0
                        ? cellOf("7", metaRefOf("id", "Int"))
                        : cellOf("eve", metaRefOf("name", "String"))));
        val widths = mapSeries(cursor, row -> boundOf(row));

        assert boundOf(widths) == 2;
        assert at(widths, 0) == 1;
        assert at(widths, 1) == 2;
    }

    void testShapesWrapper() {
        val id      = Shapes.cellFactory("id", "Int");
        val name    = Shapes.cellFactory("name", "String");
        val makeRow = Shapes.rowFactory(id, name);
        val rows    = Shapes.cursorView((Shapes.cursorBuilder()
                + makeRow("42", "bob")
                + makeRow("7", "eve"))
                .build());

        assert rows.size == 2;
        assert rows[0].value(0) == "42";
        ColumnMeta meta = rows[1].meta(1);
        assert meta[0] == "name";
        assert meta[1] == "String";

        val words = Shapes.series(2, i -> i == 0
                ? Shapes.text("oak")
                : Shapes.text("elm"));
        val wordLengths = Shapes.map(words, word -> boundOf(word));
        val text        = Shapes.textView(Shapes.text("elm"));

        assert boundOf(wordLengths) == 2;
        assert at(wordLengths, 0) == 3;
        assert at(wordLengths, 1) == 3;
        assert text[1] == 'l';
    }

    void testLifecycle() {
        LifecycleFSM lifecycle = lifecycleOf(ElementState.Created, state -> {
            switch (state) {
            case Created:
                return Set:[ElementState.Open];

            case Open:
                return Set:[ElementState.Active, ElementState.Draining];

            case Active:
                return Set:[ElementState.Draining];

            case Draining:
                return Set:[ElementState.Closed];

            case Closed:
                return Set:[ElementState.Closed];
            }
        });

        assert boundOf(lifecycle) == ElementState.Created;
        assert at(lifecycle, ElementState.Open) == Set:[ElementState.Active, ElementState.Draining];
        assert at(lifecycle, ElementState.Draining) == Set:[ElementState.Closed];
    }
}
