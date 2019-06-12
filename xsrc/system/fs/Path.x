/**
 * Path represents a path to a file.
 */
const Path
        implements Sequence<Path>
    {
    static Path ROOT    = new Path(null, Root);
    static Path PARENT  = new Path(null, Parent);
    static Path CURRENT = new Path(null, Current);

    enum ElementForm(String text, Int depth)
        {
        Root   ("/"   ,  0),
        Parent (".."  , -1),
        Current("."   ,  0),
        Name   ("name",  1)
        }

    /**
     * Construct a Path from a String representation.
     *
     * @param pathString  a legal path string, such as one emitted by `Path.toString()`
     */
    construct(String pathString)
        {
        assert pathString.size > 0;
        if (pathString == "/")
            {
            construct Path(Null, Root);
            return;
            }

        Path? parent = Null;
        if (pathString[0] == '/')
            {
            parent     = ROOT;
            pathString = pathString.substring(1);
            }

        String[] segments = pathString.split('/');
        Int      last     = segments.size - 1;
        for (Int cur = 0; cur < last; ++cur)
            {
            parent = switch (String segment = segments[cur])
                {
                case "." : new Path(parent, Current);
                case "..": new Path(parent, Parent );
                default  : new Path(parent, segment);
                };
            }

        switch (String segment = segments[last])
            {
            case "." : construct Path(parent, Current); break;
            case "..": construct Path(parent, Parent ); break;
            default  : construct Path(parent, segment); break;
            }
        }

    construct(Path? parent, String name)
        {
        construct Path(parent, Name, name);
        }

    construct(Path? parent, ElementForm form)
        {
        assert form != Name;
        construct Path(parent, form, form.text);
        }

    private construct(Path? parent, ElementForm form, String name)
        {
        this.parent = parent;
        this.form   = form;
        this.name   = name;

        assert form != Root || parent == null;
        assert parent?.relative || parent?.depth + form.depth >= 0;

        size     = 1 + (parent?.size : 0);
        absolute = parent?.absolute : form == Root;
        }

    /**
     * The Path element preceding this one.
     */
    Path? parent;

    /**
     * The form of this Path element.
     */
    ElementForm form;

    /**
     * The name of this Path element.
     */
    String name;

    /**
     * The number of Path elements that make up this Path.
     */
    @Override
    Int size;

    /**
     * True iff the Path is absolute, not relative.
     */
    Boolean absolute;

    /**
     * True iff the Path is relative, not absolute.
     */
    Boolean relative.get()
        {
        return !absolute;
        }

    /**
     * The depth implied by the Path. For an absolute Path, the depth is measured from the root,
     * where the root depth is 0, a file in the root directory is depth 1, and a file in a
     * subdirectory under the root is depth 2. For a relative Path, the depth is a measure of the
     * impact of absolute depth that would occur by following the relative Path; it may be positive,
     * zero, or negative.
     */
    Int depth.get()
        {
        return (parent?.depth : 0) + form.depth;
        }

    /**
     * Simplify the path, if possible, by removing any redundant information without changing the
     * meaning of the path.
     *
     * Specifically, remove any Current ElementForms (unless the entire Path is just one Current
     * ElementForm), and remove any combination of a Name ElementForm followed by a Parent
     * ElementForm.
     *
     * @return  the resulting normalized path
     */
    Path normalize()
        {
        if (parent == null)
            {
            return this;
            }

        Path parent = this.parent?.normalize() : assert;
        if (form == Current)
            {
            // the normalized result of "d/." is "d"
            return parent;
            }

        if (parent.form == Current)
            {
            // the normalized result of "./d" is "d"
            assert parent.size == 1;
            return new Path(null, form, name);
            }

        if (form == Parent && parent.form == Name)
            {
            // remove both this and the parent, since this cancels out the parent; if there's
            // nothing left after removing both, then the net result is to use the current dir;
            // the normalized result of "d/e/.." is "d"
            // the normalized result of "d/.." is "."
            return parent.parent ?: CURRENT;
            }

        return parent == this.parent
                ? this
                : new Path(parent, form, name);
        }

    /**
     * Determine if this path begins with the specified path. For example, "/a/b/c" starts with
     * "/a/b".
     *
     * @param that  another path
     *
     * @return true iff this path begins with the same sequence of path elements as contained in the
     *         specified path
     */
    Boolean startsWith(Path that)
        {
        Int tailSize = this.size - that.size;
        if (tailSize < 0 || this.absolute != that.absolute)
            {
            return false;
            }

        Path parent = this;
        while (tailSize-- > 0)
            {
            parent = parent.parent ?: assert;
            }

        return parent == that;
        }

    /**
     * Determine if this path ends with the specified path. For example, "/a/b/c" ends with
     * "/b/c".
     *
     * @param that  another path
     *
     * @return true iff this path ends with the same sequence of path elements as contained in the
     *         specified path
     */
    Boolean endsWith(Path that)
        {
        switch (this.size <=> that.size)
            {
            case Lesser:
                return false;

            case Greater:
                if (that.absolute)
                    {
                    return false;
                    }
                continue;

            case Equal:
                return this.form == that.form && this.name == that.name
                        && (this.parent?.endsWith(that.parent?) : true);
            }
        }

    /**
     * Resolve the specified path against this path.
     *
     * If the specified path is absolute, then that absolute path is the result.
     * Otherwise, this path is treated as a directory, and the specified relative path is appended
     * to this path.
     *
     * @param that  the path to resolve against this path
     *
     * @return  the resulting path
     */
    Path resolve(Path that)
        {
        return that.absolute ? that : this + that;
        }

    /**
     * Calculate the relative path from this path that would result in the specified path.
     * For example, if this path is "/a/b/c", and that path is "/a/p/d/q", then the relative path
     * is "../../p/d/q".
     */
    Path relativize(Path that)
        {
        assert this.absolute == that.absolute;
        TODO
        }

    /**
     * Resolve the specified name against this path's parent path.
     *
     * @param that  the name to resolve against this path's parent path
     *
     * @return the resulting path
     */
    Path sibling(String name)
        {
        return parent? + name : new Path(null, Name, name);
        }

    /**
     * Resolve the specified path against this path's parent path.
     *
     * @param that  the relative path to resolve against this path's parent path
     *
     * @return the resulting path
     */
    Path sibling(Path that)
        {
        return parent?.resolve(that) : that;
        }

    /**
     * Add a name to this path, creating a new path.
     */
    @Op("+")
    Path add(String name)
        {
        return new Path(this, name);
        }

    /**
     * Add a relative path to this path, creating a new path.
     */
    @Op("+")
    Path add(Path that)
        {
        assert that.relative;

        Path parent = this.add(that.parent?) : this;
        return new Path(parent, that.form, that.name);
        }


    // ----- Sequence methods --------------------------------------------------------------------

    @Override
    @Op("[]")
    Path getElement(Int index)
        {
        if (index < 0)
            {
            throw new OutOfBounds(index.toString() + " < 0");
            }
        if (index >= size)
            {
            throw new OutOfBounds(index.toString() + " >= " + size);
            }

        Path path  = this;
        Int  steps = size - index - 1;
        while (steps-- > 0)
            {
            path = path.parent ?: assert;
            }
        return path;
        }

    @Override
    @Op("[..]")
    Path slice(Range<Int> range)
        {
        Int lower = range.lowerBound;
        Int upper = range.upperBound;
        if (lower < 0)
            {
            throw new OutOfBounds(lower.toString() + " < 0");
            }
        if (upper >= size)
            {
            throw new OutOfBounds(upper.toString() + " >= " + size);
            }
        assert lower <= upper;

        if (lower == 0)
            {
            if (range.reversed)
                {
                assert relative;
                }
            else
                {
                return this[upper];
                }
            }

        Path? slice = null;
        for (Int index : range)
            {
            Path part = this[index];
            slice = new Path(slice, part.form, part.name);
            }
        return slice ?: assert;
        }


    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        Int length = name.size;
        // prepend the parent path and the path separator; if the parent is the root, then no
        // additional separator is added
        if (parent != null)
            {
            if (parent.form == Root)
                {
                ++length;
                }
            else
                {
                length += parent.estimateStringLength() + 1;
                }
            }
        return length;
        }

    @Override
    void appendTo(Appender<Char> appender)
        {
        // prepend the parent path and the path separator; if the parent is the root, then no
        // additional separator is added
        if (parent != null)
            {
            parent.appendTo(appender);
            if (parent.form != Root)
                {
                appender.add('/');
                }
            }

        appender.add(name);
        }
    }