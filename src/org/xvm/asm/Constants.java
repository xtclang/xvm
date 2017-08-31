package org.xvm.asm;


/**
 * Constant values used by the XVM for its various VM structures.
 *
 * @author cp  2015.12.04
 */
public interface Constants
    {
    // ----- file header -----------------------------------------------------

    /**
     * The special sequence of bytes that identifies an XVM FileStructure.
     */
    public static final int FILE_MAGIC = 0xEC57A5EE;

    /**
     * The current major version of the XVM FileStructure. This is the newest
     * version that can be read and/or written by this implementation.
     */
    public static final int VERSION_MAJOR_CUR = 0;

    /**
     * The current minor version of the XVM File structure. This is the newest
     * version that can be written by this implementation. (Newer minor versions
     * can be safely read.)
     */
    public static final int VERSION_MINOR_CUR = 0;

    /**
     * The qualified name of the Ecstasy core module. This is the only module that has no external
     * dependencies.
     */
    public static final String ECSTASY_MODULE = "Ecstasy.xtclang.org";

    /**
     * The name of the package within every module that imports the Ecstasy core module.
     */
    public static final String X_PKG_IMPORT = "ecstasy";

    /**
     * The name of the root Ecstasy class.
     */
    public static final String X_CLASS_OBJECT = "Object";

    /**
     * The name of the root Ecstasy class.
     */
    public static final String X_CLASS_TYPE = "Type";

    /**
     * The name of the Nullable enumeration.
     */
    public static final String X_CLASS_NULLABLE = "Nullable";

    /**
     * The name of the Function class.
     */
    public static final String X_CLASS_FUNCTION = "Function";

    /**
     * The name of the Array class.
     */
    public static final String X_CLASS_ARRAY = "collections.Array";

    /**
     * The name of the Tuple class.
     */
    public static final String X_CLASS_TUPLE = "collections.Tuple";

    /**
     * The name of the Set class.
     */
    public static final String X_CLASS_SET = "collections.Set";

    /**
     * The name of the Sequence interface.
     */
    public static final String X_CLASS_SEQUENCE = "collections.Sequence";

    /**
     * The name of the null value (a singleton), which is a child of {@link #X_CLASS_NULLABLE}.
     */
    public static final String X_CLASS_NULL = "Nullable.Null";

    /**
     * The Access enumeration refers to the level of accessibility to a class that a reference will
     * have:
     * <ul>
     * <li>{@link #STRUCT STRUCT} - direct access to the underlying data structure (but only to the
     *     data structure);</li>
     * <li>{@link #PUBLIC PUBLIC} - access to the public members of the object's class;</li>
     * <li>{@link #PROTECTED PROTECTED} - access to the protected members of the object's class;</li>
     * <li>{@link #PRIVATE PRIVATE} - access to the private members of the object's class;</li>
     * </ul>
     */
    public enum Access
        {
        STRUCT(0),
        PUBLIC(Component.ACCESS_PUBLIC),
        PROTECTED(Component.ACCESS_PROTECTED),
        PRIVATE(Component.ACCESS_PRIVATE);

        private Access(int flags)
            {
            this.FLAGS = flags;
            }

        /**
         * Look up a Access enum by its ordinal.
         *
         * @param i  the ordinal
         *
         * @return the Access enum for the specified ordinal
         */
        public static Access valueOf(int i)
            {
            return VALUES[i];
            }

        /**
         * All of the Access enums.
         */
        private static final Access[] VALUES = Access.values();

        /**
         * The Ecstasy keyword associated with the Access enum.
         */
        public final String KEYWORD = name().toLowerCase();

        /**
         * The integer flags used to encode the access enum.
         *
         * @see Component#ACCESS_MASK
         * @see Component#ACCESS_SHIFT
         * @see Component#ACCESS_PUBLIC
         * @see Component#ACCESS_PROTECTED
         * @see Component#ACCESS_PRIVATE
         */
        public final int FLAGS;
        }
    }
