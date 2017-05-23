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

    public static final String ECSTASY_MODULE = "ecstasy.xtclang.org";

    public static final String CLASS_OBJECT   = "Object";

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

        public final String KEYWORD = name().toLowerCase();

        public final int FLAGS;
        }
    }
