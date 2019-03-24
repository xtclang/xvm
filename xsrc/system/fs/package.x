/**
 * The filing system package contains classes and interfaces related to accessing, manipulating,
 * and managing hierarchical file systems.
 */
package fs
    {
    /**
     * A generic path-related exception, used as the basis for many filing system exceptions.
     */
    // REVIEW GG - it couldn't find this if it was marked "protected"
    const PathException(Path? path = null, String? text = null, Exception? cause = null)
            extends Exception(text, cause);

    /**
     * Indicates that a file or directory that was required by an operation does not exist.
     */
    const FileNotFound(Path? path = null, String? text = null, Exception? cause = null)
            extends PathException(path, text, cause);

    /**
     * Indicates that a file or directory that was required by an operation could not be accessed;
     * depending on the operation, this may indicate that an attempt to read from the file failed,
     * or it may indicate that an attempt to write to the file failed, or it may indicate both.
     */
    const AccessDenied(Path? path = null, String? text = null, Exception? cause = null)
            extends PathException(path, text, cause);

    /**
     * Indicates that a file or directory that would have been unconditionally created by an
     * operation already existed at or before the point that the operation would have created it.
     */
    const FileAlreadyExists(Path? path = null, String? text = null, Exception? cause = null)
            extends PathException(path, text, cause);

    /**
     * TODO
     */
    const EndOfFile(Path? path = null, String? text = null, Exception? cause = null)
            extends PathException(path, text, cause);
    }
