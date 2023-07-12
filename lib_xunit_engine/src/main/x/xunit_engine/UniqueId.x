/**
 * A unique identifier for a test model.
 *
 * * A `UniqueId` is made up of an array of segments, where a `Segment` has a type and a value.
 * * A `UniqueId` is part of a model hierarchy, so like a File path, the parent of a
 *   `UniqueId` can be determined by removing the last `Segment` from it's array of segments.
 */
const UniqueId
        implements Orderable {
    /**
     * Create a `UniqueId`
     *
     * @param segments  the `Segment` array that makes up this `UniqueId`
     *
     * @throws IllegalArgument if the `Segment` array is empty
     */
    private construct(Segment[] segments) {
        assert:arg !segments.empty;
        this.segments = segments;
        this.size     = segments.size;
    }

    /**
     * The `Segment` array that makes up this `UniqueId`.
     */
    Segment[] segments;

    /**
     * The number of segments in this `UniqueId`.
     */
    Int size;

    /**
     * Return the type of this `UniqueId`.
     *
     * The type is the type from the last `Segment` in this id.
     *
     * @return the type of this `UniqueId`
     */
    @Lazy SegmentType type.calc() {
        return segments[size -1].type;
    }

    /**
     * Return the value of this `UniqueId`.
     *
     * The value is the value from the last `Segment` in this id.
     *
     * @return the value of this `UniqueId`
     */
    @Lazy String value.calc() {
        return segments[size -1].value;
    }

    /**
     * Return the path for this `UniqueId`.
     *
     * @return the path for this `UniqueId`
     */
    @Lazy String path.calc() {
        if (segments.size == 1) {
            return segments[0].value + ":";
        }
        StringBuffer buf = new StringBuffer();
        segments[0].value.appendTo(buf);
        for (Int i : 1 ..< size) {
            if (i > 1) {
                buf.append('.');
            } else {
                buf.append(':');
            }
            segments[i].value.appendTo(buf);
        }
        return buf.toString();
    }

    /**
     * The string representation of this `UniqueId`.
     */
    @Lazy String stringValue.calc() {
        StringBuffer buf = new StringBuffer();
        for (Int i : 0 ..< size) {
            if (i > 0) {
                buf.append('/');
            }
            buf.append($"[{segments[i].type}:{segments[i].value}]");
        }
        return buf.toString();
    }

    /**
     * Return a `UniqueId` that is this `UniqueId` with a new `Segment` appended.
     *
     * @param type   the `Segment` type
     * @param value  the `Segment` value
     */
    UniqueId! append(SegmentType type, String value) {
        return new UniqueId(segments + new Segment(type, value));
    }

    /**
     * Returns the parent of this `UniqueId`.
     *
     * @return `True` iff this `UniqueId` has a parent, otherwise returns `False`
     * @return the `UniqueId` of the parent
     */
    conditional UniqueId! parent() {
        if (size > 1) {
            return True, new UniqueId(segments[0 ..< size - 1]);
        }
        return False;
    }

    // ----- Comparable methods --------------------------------------------------------------------

    @Override
    static <CompileType extends UniqueId> Ordered compare(CompileType value1, CompileType value2) {
        Ordered ordered = value1.size <=> value1.size;
        if (ordered == Equal) {
            for (Int i : 0 ..< value1.size) {
                ordered = value1.segments[i] <=> value2.segments[i];
                if (ordered != Equal) {
                    break;
                }
            }
        }
        return ordered;
    }

    @Override
    static <CompileType extends UniqueId> Boolean equals(CompileType value1, CompileType value2) {
        return value1.segments == value2.segments;
    }

    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength() {
        return stringValue.size;
    }

    @Override
    Appender<Char> appendTo(Appender<Char> buf) {
        return stringValue.appendTo(buf);
    }

    // ----- factory methods -----------------------------------------------------------------------

    /**
     * Create a `UniqueId` from the supplied `Class`.
     *
     * @param o  the `Class` to create a `UniqueId` for
     *
     * @return  the `UniqueId` for the specified `Class`
     */
    static UniqueId forObject(Object o) {
        if (o.is(Class)) {
            return forClass(o);
        } else if (o.is(Method)) {
            Type         target   = o.Target;
            Type         dataType = target.DataType;
            assert Class clz      := dataType.fromClass();
            UniqueId     parent    = forClass(clz);
            return parent.append(Method, o.name);
        }
        Class clz = &o.actualClass;
        return forClass(clz);
    }

    /**
     * Create a `UniqueId` from the supplied `Class`.
     *
     * @param clz  the `Class` to create a `UniqueId` for
     *
     * @return  the `UniqueId` for the specified `Class`
     */
    static UniqueId forClass(Class<> clz) {
        Type type = clz.toType();
        String segment;
        String value;

        String       path       = clz.path;
        assert Int   idx        := path.indexOf(':');
        String       moduleName = path[0 ..< idx];
        String       remainder  = path[idx >..< path.size];
        String[]     parts      = remainder.size > 0 ? remainder.split('.') : [];
        Segment[]    segments   = new Array(1 + parts.size);

        segments.add(new Segment(Module, moduleName));

        if (parts.size > 0) {
            Int size  = parts.size;
            if (type.isA(Package)) {
                populatePackages(segments, parts, size);
            } else if (type.isA(Method)) {
                populatePackages(segments, parts, size - 2);
                segments.add(new Segment(Class , parts[size - 2]));
                segments.add(new Segment(Method, parts[size - 1]));
            } else {
                populatePackages(segments, parts, size - 1);
                segments.add(new Segment(Class , parts[size - 1]));
            }
        }

        return new UniqueId(segments);
    }

    private static void populatePackages(Segment[] segments, String[] parts, Int end) {
        for (Int i : 0 ..< end) {
            segments.add(new Segment(Package, parts[i]));
        }
    }

    // ----- inner enum: SegmentType ---------------------------------------------------------------

    enum SegmentType {
        /**
         * A module segment type.
         */
        Module,
        /**
         * A package segment type.
         */
        Package,
        /**
         * A class segment type.
         */
        Class,
        /**
         * A method segment type.
         */
        Method;
    }

    // ----- inner const: Segment ------------------------------------------------------------------

    /**
     * A segment in a unique identifier.
     */
    static const Segment(SegmentType type, String value)
            implements Orderable {

        // ----- Comparable methods ----------------------------------------------------------------

        @Override
        static <CompileType extends Segment> Ordered compare(CompileType value1, CompileType value2) {
            Ordered ordered = value1.type.ordinal <=> value2.type.ordinal;
            if (ordered == Equal) {
                ordered = value1.value <=> value2.value;
            }
            return ordered;
        }
    }

    // ----- Well known Segment types --------------------------------------------------------------

    /**
     * A module segment type.
     */
    static String SEGMENT_MODULE = "module";

    /**
     * A package segment type.
     */
    static String SEGMENT_PACKAGE = "package";

    /**
     * A class segment type.
     */
    static String SEGMENT_CLASS = "class";

    /**
     * A method segment type.
     */
    static String SEGMENT_METHOD = "method";
}