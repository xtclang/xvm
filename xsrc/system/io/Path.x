/**
 * Path represents a file or directory pathName name.
 *
 * A Path can be absolute:
 *  * /
 *  * /a
 *  * /a/b
 * or relative:
 *  * a
 *  * ./a
 *  * ../a/b
 *
 * There are natural operation on the Path, such as "+", "-" and "[]".
 *
 * For any Path _path_, it is always the case that:
 *
 *     new Path(path.to<String>()) == path
 */
const Path
        implements collections.UniformIndexed<Int, String>
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a {code}Path{code} based on the specified name.
     */
    construct Path(String pathName)
        {
        this.pathName = normalize(pathName);
        }

    /**
     * Construct a {code}Path{code} based on the specified directory {code}Path{code} and
     * the child name.
     */
    construct Path(Path dir, String pathName)
        {
        this.pathName = dir + normalize(pathName);
        }

    // ----- properties ----------------------------------------------------------------------------

    static Char separatorChar = '/';
    static String separator = "/";

    /**
     * A normalized (canonical) pathName.
     */
    String pathName;

    @Lazy String[] pathSegments.calc()
        {
        return pathName.split(separator);
        }

    /**
     * Specifies whether this path is a relative or absolute one.
     */
    Boolean isAbsolute.get()
        {
        return pathName != "" && pathName[0] == separatorChar;
        }

    /**
     * Specifies whether this path is a root.
     */
    Boolean isRoot.get()
        {
        return pathName == separator;
        }

    /**
     * The depth (number of segments) in this pathName.
     */
    Int depth.get()
        {
        return pathName == "" ? 0 : pathName.count(separatorChar) + 1;
        }

    /**
     * The last part of the pathName name sequence.
     */
    String name.get()
        {
        if (Int of : pathName.lastIndexOf(separatorChar))
            {
            return pathName.substring(of);
            }
        return pathName;
        }

    /**
     * The parent Path.
     */
    @Lazy Path? parent.calc()
        {
        if (Int of : pathName.lastIndexOf(separatorChar))
            {
            return new Path(pathName.substring(0..of), fs);
            }
        return pathName;
        }

    /**
     * Obtain the value of the specified path segment - path[ix] operation.
     */
    @Override
    @Op String getElement(Int ix)
        {
        return pathSegments[ix];
        }

    /**
     * Concatenate this Path with the specified one.
     *
     * * if the specified `path` is absolute, the result would be equal to `path`; otherwise
     * * if this path is absolute, the result is an absolute path
     * * if this path is relative, the result is a relative path
     */
    @Op("+") Path resolve(Path path)
        {
        if (path.absolute)
            {
            return path;
            }

        return root
            ? new Path(separator + path.pathName)
            : new Path(separator + separatorChar + path.pathName);
        }

    /**
     * Calculate a relative Path between this Path and the specified one.
     *
     * Both this and the specified Path must be absolute or both relative.
     *
     * For any two absolute paths `p1` and `p2` it always holds that:
     *
     *      p2 + (p1 - p2) == p1
     */
    @Op("-") Path diff(Path path)
        {
        if (!(absolute ^ path.absolute))
            {
            throw new IllegalArgumentException("not an absolute path");
            }

        String[] thisSegments = this.pathSegments;
        String[] thatSegments = path.pathSegments;

        TODO - calculate the diff
        }

    // ----- internal ------------------------------------------------------------------------------

    /**
     * Normalize the specified string. After the normalization it's guaranteed that:
     *   - an absolute path doesn't have any "relative" segments such as ".." or "."
     *   - an absolute path doesn't end with the separatorChar unless it's the root ("/")
     *   - a relative path never ends with the separatorChar
     *
     * @throws InvalidPathException if the pathName cannot be used as a path
     */
    static String normalize(String pathName)
        {
        TODO -- native
        }

    @Override
    Int hash.get()
        {
        return pathName.hashCode;
        }

    @Override
    String to<String>()
        {
        return pathName;
        }
    }
