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
    public static final int FILE_MAGIC = 0xCAFE1CED;

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
     * Used to specify that an item (such as a parameter) is not specified as
     * being either mutable or immutable. For a parameter, this means that a
     * caller can pass either a mutable or an immutable object as an argument.
     */
    public static final int CONST_NOSPEC   = 0x0;
    /**
     * Used to specify that an item (such as a parameter) is mutable. For a
     * parameter, this means that a caller can only pass a mutable object as an
     * argument.
     */
    public static final int CONST_FALSE    = 0x1;
    /**
     * Used to specify that an item (such as a parameter) is immutable. For a
     * parameter, this means that a caller can only pass an immutable object as
     * an argument.
     */
    public static final int CONST_TRUE     = 0x2;
    /**
     * Used to specify that an item (such as a parameter) will be treated as if
     * it is immutable, regardless of whether it is mutable or immutable. For a
     * parameter, this means that a call can pass either a mutable or an
     * immutable object as an argument, but only explicitly immutable operations
     * on the object will be performed.
     */
    public static final int PARAM_READONLY = 0x3;
    }
