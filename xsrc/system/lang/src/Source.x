import io.CharArrayReader;

/**
 * Represents a unit of source code.
 */
const Source
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a `Source` instance from a `String` value.
     *
     * @param contents  the source code or other textual contents
     * @param root      the directory that acts as the "root" of the source and resource hierarchy,
     *                  used to obtain file and directory literals
     * @param path      the path corresponding to this source code (which may be a directory), used
     *                  to evaluate file and directory literals
     */
    construct (String contents, Directory? root = Null, Path? path=Null)
        {
        this.contents = contents;
        this.root     = root;
        this.path     = path;
        }

    /**
     * Construct a `Source` instance from a `File` value.
     *
     * @param file  the file containing the source code or other contents
     * @param root  the directory that acts as the "root" of the source and resource hierarchy, used
     *              to obtain file and directory literals
     */
    construct (File file, Directory? root = Null)
        {
        this.file = file;
        construct Source(loadText(file), root, file.path);
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The textual contents of the `Source` (typically, source code).
     */
    String contents;

    /**
     * The `File` that the source is read from, or `Null` if the source is provided as a `String`
     * instead.
     */
    File? file;

    /**
     * The `Directory` that acts as the "root" of the source and resource hierarchy, used to obtain
     * file and directory literals.
     */
    Directory? root;

    /**
     * The `Path` of this `Source`, which is either the path of the file that the source was read
     * from, or a `Path` corresponding to the `String` that was used to create this object. This
     * information is used when resolving file and directory literals.
     */
    Path? path;


    // ----- API -----------------------------------------------------------------------------------

    /**
     * @return a `Reader` for the [contents] of the `Source`
     */
    Reader createReader()
        {
        return new CharArrayReader(contents);
        }

    /**
     * Resolve a path relative to this `Source`, or absolute based on the provided [root] Directory.
     *
     * @return True iff a root directory was provided and the path can be resolved to an existing
     *         file or directory
     * @return (conditional) the `File` or `Directory` that the path resolved to
     */
    conditional File | Directory resolvePath(Path path)
        {
        // TODO
        return False;
        }

    /**
     * Load a file (as text) referenced from inside this `Source`.
     *
     * @param path  a path relative to this `Source`
     *
     * @return True iff a root directory was provided and the path can be resolved to a file and
     *         read
     * @return (conditional) the `Source` for the specified file
     */
    conditional Source includeString(Path path)
        {
        // TODO
        return False;
        }

    /**
     * Load a file (as binary) referenced from inside this `Source`.
     *
     * @param path  a path relative to this `Source`
     *
     * @return True iff a root directory was provided and the path can be resolved to a file and
     *         read
     * @return (conditional) the binary contents of the specified file
     */
    conditional Byte[] includeBinary(Path path)
        {
        // TODO
        return False;
        }

    /**
     * TODO
     */
    static String loadText(File file)
        {
        TODO
        }
    }