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
    public static final String VE_UNKNOWN                            = "VERIFY-01";
    /**
     * {0} does not have type parameters, but type parameters were provided.
     */
    public static final String VE_TYPE_PARAMS_UNEXPECTED             = "VERIFY-02";
    /**
     * {0} requires {1} type parameters, but {2} type parameters were provided.
     */
    public static final String VE_TYPE_PARAMS_WRONG_NUMBER           = "VERIFY-03";
    /**
     * {0} type parameter {1} must be of type {2}, but has been overridden as {3} by {4}.
     */
    public static final String VE_TYPE_PARAM_INCOMPATIBLE_CONSTRAINT = "VERIFY-04";
    /**
     * {0} type parameter {1} must be of type {2}, but has been specified as {3} by {4}.
     */
    public static final String VE_TYPE_PARAM_INCOMPATIBLE_TYPE       = "VERIFY-05";
    /**
     * {0} type parameter {1} is specified as two different types ({2} and {3}) by {4}.
     */
    public static final String VE_TYPE_PARAM_CONFLICTING_TYPES       = "VERIFY-06";
    /**
     * {0} is annotated by type {1}, but it is not an explicit class identity.
     */
    public static final String VE_ANNOTATION_NOT_CLASS               = "VERIFY-07";
    /**
     * Unexpected "extends" {0} on {1}; an "extends" specifier cannot occur on interfaces (or on the
     * root Object), there must be only one, and it must occur first (after any annotations, and
     * after the "into" for a mixin).
     */
    public static final String VE_EXTENDS_UNEXPECTED                 = "VERIFY-08";
    /**
     * {0} is missing "extends".
     */
    public static final String VE_EXTENDS_EXPECTED                   = "VERIFY-09";
    /**
     * {0} "extends" {1}, but it is not an explicit class identity.
     */
    public static final String VE_EXTENDS_NOT_CLASS                  = "VERIFY-10";
    /**
     * {0} is part of a cyclical "extends" loop.
     */
    public static final String VE_EXTENDS_CYCLICAL                   = "VERIFY-11";
    /**
     * {0} mixes into {1}, but is extended by {2} that mixes into the incompatible type {3}.
     */
    public static final String VE_INTO_INCOMPATIBLE                  = "VERIFY-12";
    /**
     * Unexpected annotation {0} on {1}; annotations can only appear in the beginning of the
     * contribution list.
     */
    public static final String VE_ANNOTATION_UNEXPECTED              = "VERIFY-13";
    /**
     * Unexpected "into" {0} on {1}; an "into" specifier can only occur on a mixin, there must be
     * only one, and it must occur first (after any annotations).
     */
    public static final String VE_INTO_UNEXPECTED                    = "VERIFY-14";
    /**
     * Unexpected "incorporates" {0} on {1}; an "incorporates" specifier cannot occur on an
     * interface.
     */
    public static final String VE_INCORPORATES_UNEXPECTED            = "VERIFY-15";
    /**
     * {0} is incorporated by type {1}, but it is not an explicit class identity.
     */
    public static final String VE_INCORPORATES_NOT_CLASS             = "VERIFY-16";
    /**
     * {0} is incorporated by type {1}, but it is not a mixin.
     */
    public static final String VE_INCORPORATES_NOT_MIXIN             = "VERIFY-17";
    /**
     * {0} incorporates {1}, but {2} is not compatible with the "into" specifier: {3}.
     */
    public static final String VE_INCORPORATES_INCOMPATIBLE          = "VERIFY-18";
    /**
     * {0} is delegated by type {1}, but it is not an interface type.
     */
    public static final String VE_DELEGATES_NOT_INTERFACE            = "VERIFY-19";
    /**
     * {0} is implemented by type {1}, but it is not an interface type.
     */
    public static final String VE_IMPLEMENTS_NOT_INTERFACE           = "VERIFY-20";
    /**
     * Unexpected "delegates" {0} on {1}; a "delegates" specifier cannot occur on an
     * interface.
     */
    public static final String VE_DELEGATES_UNEXPECTED               = "VERIFY-21";
    /**
     * Unexpected formal type name {0} encountered while resolving {1}.
     */
    public static final String VE_FORMAL_NAME_UNKNOWN                = "VERIFY-22";
    /**
     * {0}, which is a {1}, illegally extends {2}, which is a {3}.
     */
    public static final String VE_EXTENDS_INCOMPATIBLE               = "VERIFY-23";
    /**
     * Service type "{0}" cannot be treated as an immutable type.
     */
    public static final String VE_IMMUTABLE_SERVICE_ILLEGAL          = "VERIFY-24";
    /**
     * Warning: Redundant immutable type specification.
     */
    public static final String VE_IMMUTABLE_REDUNDANT                = "VERIFY-25";
    /**
     * Type "{0}" cannot be annotated because it does not specify a class or interface.
     */
    public static final String VE_ANNOTATION_ILLEGAL                 = "VERIFY-26";
    /**
     * {0} is not a mixin, and thus cannot be used in an annotation.
     */
    public static final String VE_ANNOTATION_NOT_MIXIN               = "VERIFY-27";
    /**
     * The annotation @{0} is repeated.
     */
    public static final String VE_ANNOTATION_REDUNDANT               = "VERIFY-28";
    /**
     * Type "{0}" cannot have accessibility defined because it does not specify a class or interface.
     */
    public static final String VE_ACCESS_TYPE_ILLEGAL                = "VERIFY-29";
    /**
     * {0} is extended by mixin type {1}, but it is not a mixin.
     */
    public static final String VE_EXTENDS_NOT_MIXIN                  = "VERIFY-30";
    /**
     * {0} is not a type that can be parameterized.
     */
    public static final String VE_PARAM_TYPE_ILLEGAL                 = "VERIFY-31";
    /**
     * {0} is annotated by {1}, but is not compatible with the required "into": {2}.
     */
    public static final String VE_ANNOTATION_INCOMPATIBLE            = "VERIFY-32";


    // ----- miscellaneous -------------------------------------------------------------------------

    /**
     * Compile-time debug flag.
     */
    public static final boolean DEBUG = true;
    }
