module TestConfix {
    typedef Join<A, B> as Tuple<A, B>;
    typedef Twin<T> as Join<T, T>;
    typedef MetaSeries<I, T> as Join<I, function T(I)>;
    typedef Series<T> as MetaSeries<Int, T>;

    typedef Join<String, IOMemento> as ColumnMeta;
    typedef function ColumnMeta() as ColumnMetaRef;
    typedef Join<Any?, ColumnMetaRef> as Cell;
    typedef Series<Cell> as RowVec;
    typedef Series<RowVec> as Cursor;
    typedef String as CharStr;
    typedef MetaSeries<ConfixFacet, Any?> as ConfixIndex;
    typedef Join<Int, IOMemento> as Pending;
    typedef function Int?(String) as KeyLookup;

    enum IOMemento {
        IoBoolean,
        IoInt,
        IoLong,
        IoFloat,
        IoDouble,
        IoString,
        IoLocalDate,
        IoInstant,
        IoNothing,
        IoObject,
        IoArray,
        IoBytes,
    }

    enum Syntax {
        JSON,
        CBOR,
        YAML,
    }

    enum ConfixFacet {
        Spans,
        Tags,
        Depths,
        DirectChildren,
        TreeCursor,
        KeyToChild,
    }

    void run() {
        testRecognize();
        testDecode();
        testJsonIndex();
        testJsonTree();
        testDispatch();
        testCborIndex();
    }

    static <A, B> Join<A, B> joinOf(A left, B right) {
        return (left, right);
    }

    static <T> Twin<T> twinOf(T left, T right) {
        return joinOf(left, right);
    }

    static <I, T> MetaSeries<I, T> metaSeriesOf(I bound, function T(I) indexer) {
        return joinOf(bound, indexer);
    }

    static <T> Series<T> seriesOf(Int size, function T(Int) indexer) {
        return metaSeriesOf(size, indexer);
    }

    static <T> Series<T> seriesFrom(T[] values) {
        return seriesOf(values.size, i -> values[i]);
    }

    static <I, T> I boundOf(MetaSeries<I, T> series) {
        return series[0];
    }

    static <I, T> T at(MetaSeries<I, T> series, I index) {
        return series[1](index);
    }

    static ColumnMeta metaOf(String name, IOMemento tag) {
        return joinOf(name, tag);
    }

    static Cell cellOf(Any? value, String name, IOMemento tag) {
        ColumnMetaRef meta = () -> metaOf(name, tag);
        return joinOf(value, meta);
    }

    static RowVec rowOf(Cell[] cells) {
        return seriesFrom(cells);
    }

    static Cursor cursorOf(RowVec[] rows) {
        return seriesFrom(rows);
    }

    static CharStr textOf(String text) {
        return text;
    }

    static const RowBuilder(Cell[] cells = []) {
        @Op("+")
        RowBuilder add(Cell cell) {
            return new RowBuilder(cells.add(cell));
        }

        RowVec build() {
            return rowOf(cells);
        }
    }

    static const RowShape {
        construct(RowVec row) {
            this.row = row;
        }

        private RowVec row;

        Int size.get() {
            return boundOf(row);
        }

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

    static const CursorShape {
        construct(Cursor cursor) {
            this.cursor = cursor;
        }

        private Cursor cursor;

        Int size.get() {
            return boundOf(cursor);
        }

        @Op("[]")
        RowShape getElement(Int index) {
            return new RowShape(at(cursor, index));
        }
    }

    static const ConfixShape {
        construct(ConfixIndex index) {
            this.index = index;
        }

        private ConfixIndex index;

        @Op("[]")
        Any? getElement(ConfixFacet facet) {
            return at(index, facet);
        }
    }

    static RowBuilder rowBuilder() {
        return new RowBuilder();
    }

    static Boolean recognize(Syntax syntax, Byte first) {
        Char ch = first.toInt().toChar();

        return switch (syntax) {
            case JSON: ch == '{' || ch == '[' || ch == '"';
            case CBOR: True;
            case YAML: ch != '{' && ch != '[';
        };
    }

    static Cursor dispatch(Byte[] bytes) {
        Byte first = bytes[0];

        // Preserve the Kotlin enum ordering: JSON, then CBOR, then YAML.
        if (recognize(JSON, first)) {
            return scan(JSON, bytes);
        }

        if (recognize(CBOR, first)) {
            return scan(CBOR, bytes);
        }

        return scan(YAML, bytes);
    }

    static Cursor scan(Syntax syntax, Byte[] bytes) {
        return treeCursorOf(syntax == CBOR
                ? scanCbor(bytes)
                : scan0(bytes.unpackUtf8()));
    }

    static CharStr decodeText(CharStr src, Int open, Int close) {
        Char first = src[open];
        Char last  = src[close];

        if (first == '"' && last == '"' && close > open + 1) {
            return textOf(src[open + 1 ..< close]);
        }

        return textOf(src[open .. close]);
    }

    static Any? decodeValue(CharStr src, Int open, Int close, IOMemento tag) {
        return switch (tag) {
            case IoString:  decodeText(src, open, close);
            case IoBoolean: src[open] == 't';
            case IoNothing: Null;
            case IoDouble:  decodeText(src, open, close);
            default:        Null;
        };
    }

    static ConfixIndex scan0(Byte[] bytes) {
        return scan0(bytes.unpackUtf8());
    }

    static ConfixIndex scan0(String src) {
        Int n = src.size;

        Int[]       opens  = [];
        Int[]       closes = [];
        IOMemento[] tags   = [];
        Pending[]   stack  = [];

        void add(Int open, Int close, IOMemento tag) {
            opens  += open;
            closes += close;
            tags   += tag;
        }

        void push(Int open, IOMemento tag) {
            stack += joinOf(open, tag);
        }

        void pop(Int close) {
            if (!stack.empty) {
                Pending pending = stack[stack.size - 1];
                stack.delete(stack.size - 1);
                add(pending[0], close, pending[1]);
            }
        }

        Boolean inQuote = False;
        Boolean escaped = False;
        Int     index   = 0;

        while (index < n) {
            Char ch = src[index];

            if (inQuote) {
                if (escaped) {
                    escaped = False;
                    if (ch == '"') {
                        inQuote = False;
                        pop(index);
                    }
                } else if (ch == '\\') {
                    escaped = True;
                } else if (ch == '"') {
                    inQuote = False;
                    pop(index);
                }
            } else {
                switch (ch) {
                case '{':
                    push(index, IoObject);
                    break;

                case '[':
                    push(index, IoArray);
                    break;

                case '}':
                case ']':
                    pop(index);
                    break;

                case '"':
                    push(index, IoString);
                    inQuote = True;
                    break;

                case 't':
                    if (index + 3 < n &&
                            src[index + 1] == 'r' &&
                            src[index + 2] == 'u' &&
                            src[index + 3] == 'e') {
                        add(index, index + 3, IoBoolean);
                        index += 3;
                    }
                    break;

                case 'f':
                    if (index + 4 < n &&
                            src[index + 1] == 'a' &&
                            src[index + 2] == 'l' &&
                            src[index + 3] == 's' &&
                            src[index + 4] == 'e') {
                        add(index, index + 4, IoBoolean);
                        index += 4;
                    }
                    break;

                case 'n':
                    if (index + 3 < n &&
                            src[index + 1] == 'u' &&
                            src[index + 2] == 'l' &&
                            src[index + 3] == 'l') {
                        add(index, index + 3, IoNothing);
                        index += 3;
                    }
                    break;

                case '-':
                case '+':
                case '0'..'9':
                    Int start = index;
                    while (index < n) {
                        Char digit = src[index];
                        if (!('0' <= digit <= '9') &&
                                digit != '.' &&
                                digit != 'e' &&
                                digit != 'E' &&
                                digit != '+' &&
                                digit != '-') {
                            break;
                        }
                        ++index;
                    }
                    add(start, index - 1, IoDouble);
                    continue;
                }
            }

            ++index;
        }

        while (!stack.empty) {
            Pending pending = stack[stack.size - 1];
            stack.delete(stack.size - 1);
            add(pending[0], n - 1, pending[1]);
        }

        return buildTree(opens, closes, tags, src);
    }

    static ConfixIndex scanCbor(Byte[] src) {
        Int n = src.size;

        Int[]       opens  = [];
        Int[]       closes = [];
        IOMemento[] tags   = [];

        void add(Int open, Int close, IOMemento tag) {
            opens  += open;
            closes += close;
            tags   += tag;
        }

        (Int len, Int nextPos) readLen(Int pos, Int ai) {
            switch (ai) {
            case 0..23:
                return ai, pos;

            case 24:
                return src[pos].toInt() & 0xFF, pos + 1;

            case 25:
                return ((src[pos    ].toInt() & 0xFF) << 8) |
                        (src[pos + 1].toInt() & 0xFF), pos + 2;

            case 26:
                return ((src[pos    ].toInt() & 0xFF) << 24) |
                       ((src[pos + 1].toInt() & 0xFF) << 16) |
                       ((src[pos + 2].toInt() & 0xFF) <<  8) |
                        (src[pos + 3].toInt() & 0xFF), pos + 4;

            case 27:
                Int len = 0;
                for (Int i = 0; i < 8; ++i) {
                    len = (len << 8) | (src[pos + i].toInt() & 0xFF);
                }
                return len, pos + 8;

            case 31:
                return -1, pos;

            default:
                assert as $"unsupported cbor additional-info {ai}";
            }
        }

        Int parseItem(Int pos) {
            Int open = pos;
            Int info = src[pos].toInt() & 0xFF;
            Int mt   = info >> 5;
            Int ai   = info & 0x1F;
            Int next = pos + 1;

            switch (mt) {
            case 0, 1:
                (Int _, Int end) = readLen(next, ai);
                add(open, end - 1, IoDouble);
                return end;

            case 2:
                (Int len, Int end) = readLen(next, ai);
                if (len >= 0) {
                    add(open, end + len - 1, IoBytes);
                    return end + len;
                }
                return end;

            case 3:
                (Int len, Int end) = readLen(next, ai);
                if (len >= 0) {
                    add(open, end + len - 1, IoString);
                    return end + len;
                }
                return end;

            case 4:
                (Int len, Int end) = readLen(next, ai);
                Int cursor = end;
                if (len < 0) {
                    while (cursor < n && (src[cursor].toInt() & 0xFF) != 0xFF) {
                        cursor = parseItem(cursor);
                    }
                    if (cursor < n) {
                        ++cursor;
                    }
                } else {
                    for (Int i = 0; i < len; ++i) {
                        cursor = parseItem(cursor);
                    }
                }
                add(open, cursor - 1, IoArray);
                return cursor;

            case 5:
                (Int len, Int end) = readLen(next, ai);
                Int cursor = end;
                if (len < 0) {
                    while (cursor < n && (src[cursor].toInt() & 0xFF) != 0xFF) {
                        cursor = parseItem(cursor);
                        cursor = parseItem(cursor);
                    }
                    if (cursor < n) {
                        ++cursor;
                    }
                } else {
                    for (Int i = 0; i < len; ++i) {
                        cursor = parseItem(cursor);
                        cursor = parseItem(cursor);
                    }
                }
                add(open, cursor - 1, IoObject);
                return cursor;

            case 6:
                (Int _, Int end) = readLen(next, ai);
                return parseItem(end);

            case 7:
                IOMemento tag = switch (ai) {
                    case 20, 21: IoBoolean;
                    case 22, 23: IoNothing;
                    case 25, 26, 27: IoDouble;
                    default: IoNothing;
                };

                Int size = switch (ai) {
                    case 24: 1;
                    case 25: 2;
                    case 26: 4;
                    case 27: 8;
                    default: 0;
                };

                add(open, open + size, tag);
                return next + size;

            default:
                add(open, open, IoNothing);
                return next;
            }
        }

        Int pos = 0;
        while (pos < n) {
            pos = parseItem(pos);
        }

        return buildTree(opens, closes, tags, src);
    }

    static ConfixIndex buildTree(Int[] opens, Int[] closes, IOMemento[] rawTags, String src) {
        Int total = opens.size;

        Twin<Int>[] spansArray = new Twin<Int>[total](i -> twinOf(opens[total - 1 - i], closes[total - 1 - i]));
        IOMemento[] tagArray   = new IOMemento[total](i -> rawTags[total - 1 - i]);
        Int[]       depthArray = new Int[total];

        for (Int i = 0; i < total; ++i) {
            Int depth = 0;
            Int open  = spansArray[i][0];
            Int close = spansArray[i][1];

            for (Int k = 0; k < total; ++k) {
                if (k != i &&
                        spansArray[k][0] < open &&
                        spansArray[k][1] > close) {
                    ++depth;
                }
            }

            depthArray[i] = depth;
        }

        Series<Twin<Int>> spanSeries  = seriesFrom(spansArray);
        Series<IOMemento> tagSeries   = seriesFrom(tagArray);
        Series<Int>       depthSeries = seriesFrom(depthArray);

        function Series<Int>(Int) childOf = index -> {
            Int parentOpen  = spansArray[index][0];
            Int parentClose = spansArray[index][1];
            Int childDepth  = depthArray[index] + 1;
            Int[] children  = [];

            for (Int k = 0; k < total; ++k) {
                if (k != index &&
                        spansArray[k][0] > parentOpen &&
                        spansArray[k][1] < parentClose &&
                        depthArray[k] == childDepth) {
                    children += k;
                }
            }

            return seriesFrom(children);
        };

        RowVec?[] cache = new RowVec?[total](_ -> Null);

        RowVec row(Int index) {
            if (RowVec built := cache[index]) {
                return built;
            }

            Series<Int> children = childOf(index);
            RowVec[] childRows   = new RowVec[boundOf(children)](i -> row(at(children, i)));
            Cursor   cursor      = cursorOf(childRows);
            Twin<Int> span       = spansArray[index];
            IOMemento tag        = tagArray[index];

            RowVec value = (rowBuilder()
                    + cellOf(span[0], "open",     IoInt)
                    + cellOf(span[1], "close",    IoInt)
                    + cellOf(tag,     "tag",      IoObject)
                    + cellOf(cursor,  "children", IoObject))
                    .build();

            cache[index] = value;
            return value;
        }

        Int[] roots = [];
        for (Int i = 0; i < total; ++i) {
            if (depthArray[i] == 0) {
                roots += i;
            }
        }

        Cursor treeCursor = cursorOf(new RowVec[roots.size](i -> row(roots[i])));

        KeyLookup keyToChild = key -> {
            for (Int i = 0; i < total; ++i) {
                Int open  = spansArray[i][0];
                Int close = spansArray[i][1];

                if (open >= src.size || src[open] != '"') {
                    continue;
                }

                if (close - open - 1 != key.size) {
                    continue;
                }

                Boolean match = True;
                for (Int d = 0; d < key.size; ++d) {
                    if (src[open + 1 + d] != key[d]) {
                        match = False;
                        break;
                    }
                }

                if (match) {
                    return i;
                }
            }

            return Null;
        };

        return metaSeriesOf(ConfixFacet.Spans, facet -> switch (facet) {
            case Spans:          spanSeries;
            case Tags:           tagSeries;
            case Depths:         depthSeries;
            case DirectChildren: childOf;
            case TreeCursor:     treeCursor;
            case KeyToChild:     keyToChild;
        });
    }

    static ConfixIndex buildTree(Int[] opens, Int[] closes, IOMemento[] rawTags, Byte[] src) {
        Int total = opens.size;

        Twin<Int>[] spansArray = new Twin<Int>[total](i -> twinOf(opens[total - 1 - i], closes[total - 1 - i]));
        IOMemento[] tagArray   = new IOMemento[total](i -> rawTags[total - 1 - i]);
        Int[]       depthArray = new Int[total];

        for (Int i = 0; i < total; ++i) {
            Int depth = 0;
            Int open  = spansArray[i][0];
            Int close = spansArray[i][1];

            for (Int k = 0; k < total; ++k) {
                if (k != i &&
                        spansArray[k][0] < open &&
                        spansArray[k][1] > close) {
                    ++depth;
                }
            }

            depthArray[i] = depth;
        }

        Series<Twin<Int>> spanSeries  = seriesFrom(spansArray);
        Series<IOMemento> tagSeries   = seriesFrom(tagArray);
        Series<Int>       depthSeries = seriesFrom(depthArray);

        function Series<Int>(Int) childOf = index -> {
            Int parentOpen  = spansArray[index][0];
            Int parentClose = spansArray[index][1];
            Int childDepth  = depthArray[index] + 1;
            Int[] children  = [];

            for (Int k = 0; k < total; ++k) {
                if (k != index &&
                        spansArray[k][0] > parentOpen &&
                        spansArray[k][1] < parentClose &&
                        depthArray[k] == childDepth) {
                    children += k;
                }
            }

            return seriesFrom(children);
        };

        RowVec?[] cache = new RowVec?[total](_ -> Null);

        RowVec row(Int index) {
            if (RowVec built := cache[index]) {
                return built;
            }

            Series<Int> children = childOf(index);
            RowVec[] childRows   = new RowVec[boundOf(children)](i -> row(at(children, i)));
            Cursor   cursor      = cursorOf(childRows);
            Twin<Int> span       = spansArray[index];
            IOMemento tag        = tagArray[index];

            RowVec value = (rowBuilder()
                    + cellOf(span[0], "open",     IoInt)
                    + cellOf(span[1], "close",    IoInt)
                    + cellOf(tag,     "tag",      IoObject)
                    + cellOf(cursor,  "children", IoObject))
                    .build();

            cache[index] = value;
            return value;
        }

        Int[] roots = [];
        for (Int i = 0; i < total; ++i) {
            if (depthArray[i] == 0) {
                roots += i;
            }
        }

        Cursor treeCursor = cursorOf(new RowVec[roots.size](i -> row(roots[i])));

        KeyLookup keyToChild = key -> {
            for (Int i = 0; i < total; ++i) {
                Int open = spansArray[i][0];

                if (((src[open].toInt() & 0xFF) >> 5) != 3) {
                    continue;
                }

                Int info = src[open].toInt() & 0xFF;
                Int ai   = info & 0x1F;
                (Int len, Int start) = switch (ai) {
                    case 0..23:
                        (ai, open + 1);

                    case 24:
                        (src[open + 1].toInt() & 0xFF, open + 2);

                    default:
                        (-1, open + 1);
                };

                if (len != key.size) {
                    continue;
                }

                Boolean match = True;
                for (Int d = 0; d < len; ++d) {
                    if (src[start + d].toInt().toChar() != key[d]) {
                        match = False;
                        break;
                    }
                }

                if (match) {
                    return i;
                }
            }

            return Null;
        };

        return metaSeriesOf(ConfixFacet.Spans, facet -> switch (facet) {
            case Spans:          spanSeries;
            case Tags:           tagSeries;
            case Depths:         depthSeries;
            case DirectChildren: childOf;
            case TreeCursor:     treeCursor;
            case KeyToChild:     keyToChild;
        });
    }

    static Series<Twin<Int>> spansOf(ConfixIndex index) {
        return at(index, Spans).as(Series<Twin<Int>>);
    }

    static Series<IOMemento> tagsOf(ConfixIndex index) {
        return at(index, Tags).as(Series<IOMemento>);
    }

    static Series<Int> depthsOf(ConfixIndex index) {
        return at(index, Depths).as(Series<Int>);
    }

    static function Series<Int>(Int) directChildrenOf(ConfixIndex index) {
        return at(index, DirectChildren).as(function Series<Int>(Int));
    }

    static Cursor treeCursorOf(ConfixIndex index) {
        return at(index, TreeCursor).as(Cursor);
    }

    static KeyLookup keyLookupOf(ConfixIndex index) {
        return at(index, KeyToChild).as(KeyLookup);
    }

    void testRecognize() {
        assert recognize(JSON, '{'.toByte());
        assert recognize(JSON, '['.toByte());
        assert recognize(JSON, '"'.toByte());
        assert !recognize(JSON, 'a'.toByte());

        assert recognize(YAML, 'a'.toByte());
        assert recognize(YAML, '-'.toByte());
        assert !recognize(YAML, '{'.toByte());

        assert recognize(CBOR, 0x00.toByte());
        assert recognize(CBOR, 0xFF.toByte());
    }

    void testDecode() {
        assert decodeText("\"hello\"", 0, 6) == "hello";
        assert decodeText("hello", 0, 4) == "hello";
        assert decodeValue("\"hello\"", 0, 6, IoString) == "hello";
        assert decodeValue("true", 0, 3, IoBoolean) == True;
        assert decodeValue("null", 0, 3, IoNothing) == Null;
        assert decodeValue("42", 0, 1, IoDouble) == "42";
    }

    void testJsonIndex() {
        ConfixIndex index  = scan0("42");
        Series<Twin<Int>> spans = spansOf(index);
        Series<IOMemento> tags  = tagsOf(index);
        Series<Int>       depth = depthsOf(index);

        assert boundOf(spans) == 1;
        assert at(spans, 0) == twinOf(0, 1);
        assert at(tags , 0) == IoDouble;
        assert at(depth, 0) == 0;

        ConfixIndex objectIndex = scan0("{\"key\":\"val\"}");
        assert boundOf(spansOf(objectIndex)) == 3;
        assert at(tagsOf(objectIndex), 0) == IoObject;

        Int? child = keyLookupOf(objectIndex)("key");
        assert child != Null;
    }

    void testJsonTree() {
        ConfixIndex index = scan0("[1,2]");
        CursorShape tree  = new CursorShape(treeCursorOf(index));

        assert tree.size == 1;

        RowShape root = tree[0];
        assert root.value(0) == 0;
        assert root.value(2) == IoArray;

        CursorShape children = new CursorShape(root.value(3).as(Cursor));
        assert children.size == 2;
        assert children[0].value(2) == IoDouble;
        assert children[1].value(2) == IoDouble;

        ConfixIndex nested = scan0("{\"x\":1,\"y\":2}");
        Series<Int> direct = directChildrenOf(nested)(0);
        assert boundOf(direct) == 4;
    }

    void testDispatch() {
        assert boundOf(dispatch("42".utf8())) >= 0;
        assert boundOf(dispatch("key: value\n".utf8())) >= 0;
    }

    void testCborIndex() {
        Byte[] ints = [0x83.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte()];
        ConfixIndex arrayIndex = scanCbor(ints);
        CursorShape arrayTree  = new CursorShape(treeCursorOf(arrayIndex));

        assert arrayTree.size == 1;
        assert arrayTree[0].value(2) == IoArray;
        assert new CursorShape(arrayTree[0].value(3).as(Cursor)).size == 3;

        Byte[] map = [
            0xA1.toByte(),
            0x65.toByte(),
            0x68.toByte(),
            0x65.toByte(),
            0x6C.toByte(),
            0x6C.toByte(),
            0x6F.toByte(),
            0x01.toByte(),
        ];

        ConfixIndex mapIndex = scanCbor(map);
        Int? child = keyLookupOf(mapIndex)("hello");
        assert child != Null;
        assert keyLookupOf(mapIndex)("world") == Null;
    }
}
