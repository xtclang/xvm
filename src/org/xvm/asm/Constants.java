package org.xvm.asm;


/**
 * Constant values used by the XVM for its various VM structures.
 */
public interface Constants
    {
    // ----- file header ---------------------------------------------------------------------------

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


    // ----- names ---------------------------------------------------------------------------------

    /**
     * The qualified name of the Ecstasy core module. This is the only module that has no external
     * dependencies.
     */
    public static final String ECSTASY_MODULE = "Ecstasy.xtclang.org";

    /**
     * The name of the package within every module that imports the Ecstasy core module.
     */
    public static final String X_PKG_IMPORT = "ecstasy";


    // ----- accessibility levels ------------------------------------------------------------------

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


    // ----- error codes ---------------------------------------------------------------------------

    /**
     * Unknown error.
     */
    public static final String VE_UNKNOWN                           = "VERIFY-01";
    /**
     * Type parameters were specified for {0}, but it does not declare any.
     */
    public static final String VE_UNEXPECTED_TYPE_PARAMS            = "VERIFY-02";


    // ----- miscellaneous -------------------------------------------------------------------------

    /**
     * Compile-time debug flag.
     */
    public static final boolean DEBUG = true;
    }
