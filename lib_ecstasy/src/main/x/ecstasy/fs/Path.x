/**
 * Path represents a path to a resource, such as a file.
 */
const Path
        implements UniformIndexed<Int, Path>
        implements Sliceable<Int>
        implements Iterable<Path>
        implements Destringable
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a Path from a String representation.
     *
     * @param pathString  a legal path string, such as one emitted by `Path.toString()`
     */
    @Override
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
        if (segments[last] == "")
            {
            // a blank trailing segment is ignored ("a/b/" becomes "a/b")
            assert --last >= 0;
            }

        // construct a parent path composed of all of the parsed segments, except for the last one
        for (Int i = 0; i < last; ++i)
            {
            parent = new Path(parent, segments[i]);
            }

        // this Path object is constructed from the path parent, plus the last segment
        construct Path(parent, segments[last]);
        }

    /**
     * Construct a Path based on a parent Path and an element form.
     *
     * @param parent  an optional parent
     * @param form    a path element form (anything but Name)
     */
    construct(Path? parent, ElementForm form)
        {
        assert form != Name;
        construct Path(parent, form, form.text);
        }

    /**
     * Construct a Path based on a parent Path and a path element.
     *
     * @param parent  an optional parent
     * @param name    a path element name
     */
    construct(Path? parent, String name)
        {
        switch (name)
            {
            case "/":
                assert:arg parent == Null as "Name \"/\" does not specify a relative path";
                construct Path(parent, Root);
                break;

            case ".":
                construct Path(parent, Current);
                break;

            case "..":
                construct Path(parent, Parent);
                break;

            default:
                assert:arg !name.startsWith('/') as $"Name \"{name}\" does not specify a relative path";
                String remain = name;
                while (Int slash := remain.indexOf('/'))
                    {
                    String part = remain[0 ..< slash];
                    assert:arg part.size > 0 as "Name \"{name}\" contains empty path element";

                    remain = remain[slash >..< remain.size];
                    if (remain.size == 0)
                        {
                        // the name ended with '/', so we just accidentally took the last path part
                        remain = part;
                        }
                    else
                        {
                        parent = new Path(parent, part);
                        }
                    }

                construct Path(parent, Name, remain);
                break;
            }
        }

    /**
     * Internal constructor.
     *
     * @param parent  the parent path of this path, or Null
     * @param form    the form of the path element
     * @param name    the path element name, or null if the form is not Name
     */
    private construct(Path? parent, ElementForm form, String name)
        {
        assert name != "";
        assert form != Root || parent == Null;
        assert parent?.relative || parent.depth + form.depth >= 0;

        this.parent   = parent;
        this.form     = form;
        this.name     = name;
        this.size     = 1 + (parent?.size : 0);
        this.absolute = parent?.absolute : form == Root;
        }


    // ----- constants -----------------------------------------------------------------------------

    /**
     * A "root directory" Path instance.
     */
    static Path ROOT    = new Path(Null, Root);

    /**
     * A "parent directory" Path instance.
     */
    static Path PARENT  = new Path(Null, Parent);

    /**
     * A "current directory" Path instance.
     */
    static Path CURRENT = new Path(Null, Current);


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The Path element preceding this one.
     */
    Path? parent;

    /**
     * The various potential forms of the elements of a Path.
     */
    enum ElementForm(String text, Int depth)
        {
        Root   ("/"   ,  0),
        Parent (".."  , -1),
        Current("."   ,  0),
        Name   ("name",  1)
        }

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
    @Override Int size;

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


    // ----- Path operations -----------------------------------------------------------------------

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
        if (parent == Null)
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
            return new Path(Null, form, name);
            }

        if (form == Parent && parent.form == Name)
            {
            // remove both this and the parent, since this cancels out the parent; if there's
            // nothing left after removing both, then the net result is to use the current dir;
            // the normalized result of "d/e/.." is "d"
            // the normalized result of "d/.." is "."
            return parent.parent ?: CURRENT;
            }

        return &parent == this.&parent
                ? this
                : new Path(parent, form, name);
        }

    /**
     * Determine if this path begins with the specified path. For example, "/a/b/c" starts with
     * "/a/b".
     *
     * @param that  another path
     *
     * @return True iff this path begins with the same sequence of path elements as contained in the
     *         specified path
     */
    Boolean startsWith(Path that)
        {
        if (&this == &that)
            {
            return True;
            }

        Int tailSize = this.size - that.size;
        if (tailSize < 0 || this.absolute != that.absolute)
            {
            return False;
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
     * @return True iff this path ends with the same sequence of path elements as contained in the
     *         specified path
     */
    Boolean endsWith(Path that)
        {
        switch (this.size <=> that.size)
            {
            case Lesser:
                return False;

            case Equal:
                if (&this == &that)
                    {
                    return True;
                    }
                break;

            case Greater:
                if (that.absolute)
                    {
                    return False;
                    }
                break;
            }

        Path thisElement = this;
        Path thatElement = that;
        while (True)
            {
            ElementForm form = thisElement.form;
            if (form != thatElement.form || form == Name && this.name != that.name)
                {
                return False;
                }

            val thatParent = thatElement.parent;
            if (thatParent == Null)
                {
                return True;
                }

            thisElement = thisElement.parent ?: assert;
            thatElement = thatParent;
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

        Path thisNorm = this.normalize();
        Path thatNorm = that.normalize();

        if (thisNorm == thatNorm)
            {
            return CURRENT;
            }
        else if (thisNorm.startsWith(thatNorm))
            {
            // TODO this algorithm may not work for relative paths
            assert this.absolute && that.absolute;

            Int steps = thisNorm.size - thatNorm.size;
            assert steps > 0;
            Path result = PARENT;
            for (Int i = 1; i < steps; ++i)
                {
                result = new Path(result, Parent);
                }
            return result;
            }
        else if (thatNorm.startsWith(thisNorm))
            {
            // TODO this algorithm may not work for relative paths
            assert this.absolute && that.absolute;

            Int start = thisNorm.size;
            Int stop  = thatNorm.size;
            assert stop > start;
            return that[start ..< stop];
            }
        else
            {
            // TODO this algorithm may not work for relative paths
            assert this.absolute && that.absolute;

            Int thisSize = thisNorm.size;
            Int thatSize = thatNorm.size;

            // find the size of the common path
            Int common = 0;
            while (thisNorm[common] == thatNorm[common])
                {
                ++common;
                }
            assert common > 0;          // both paths are normalized and must be absolute
            assert common < thisSize;   // already tested that.startsWith(this); it was false
            assert common < thatSize;   // already tested this.startsWith(that); it was false

            // for each segment that "this" has beyond the common path, add a "Parent" mode to the
            // result
            Path result = PARENT;
            for (Int i = common + 1; i < thisSize; ++i)
                {
                result = new Path(result, Parent);
                }

            // for each segment that "that" has beyond the common path, copy the segment from "that"
            // onto the end of the result
            return result + that[common ..< thatSize];
            }
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
        assert depth >= 1;
        return switch (form)
            {
            case Root   : assert;
            case Parent : new Path(this, Parent) + name;
            case Current: parent?.sibling(name) : new Path(PARENT, name);
            case Name   : parent? + name : new Path(Null, name);
            };
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
    @Op("+") Path add(String name)
        {
        assert:arg !name.startsWith('/') as $"Name \"{name}\" does not specify a relative path";

        return new Path(this, name);
        }

    /**
     * Add a relative path to this path, creating a new path.
     */
    @Op("+") Path add(Path that)
        {
        assert:arg that.relative as $"Path \"{that}\" is not relative";

        Path result = this;
        for (Int i : 0 ..< that.size)
            {
            val segment = that[i];
            result = new Path(result, segment.form, segment.name);
            }
        return result;
        }


    // ----- UniformIndexed methods ----------------------------------------------------------------

    @Override
    @Op("[]") Path getElement(Int index)
        {
        assert:bounds 0 <= index < size;

        if (index == 0 && absolute)
            {
            return ROOT;
            }

        Path path  = this;
        Int  steps = size - index - 1;
        while (steps-- > 0)
            {
            path = path.parent ?: assert;
            }

        return switch (path.form)
            {
            case Root   : ROOT;
            case Parent : PARENT;
            case Current: CURRENT;
            case Name   : new Path(Null, Name, path.name);
            };
        }


    // ----- Sliceable methods ---------------------------------------------------------------------

    @Override
    @Op("[..]") Path slice(Range<Int> indexes)
        {
        Int lower = indexes.effectiveLowerBound;
        Int upper = indexes.effectiveUpperBound;
        assert:bounds 0 <= lower <= upper < size;

        if (lower == 0)
            {
            assert relative || !indexes.descending;

            if (!indexes.descending)
                {
                Path result = this;
                for (Int steps = size - upper - 1; steps > 0; --steps)
                    {
                    result = result.parent ?: assert;
                    }
                return result;
                }
            }

        Path? slice = Null;
        for (Int index : indexes)
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
        if (parent != Null)
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
    Appender<Char> appendTo(Appender<Char> buf)
        {
        // prepend the parent path and the path separator; if the parent is the root, then no
        // additional separator is added
        if (parent != Null)
            {
            parent.appendTo(buf);
            if (parent.form != Root)
                {
                buf.add('/');
                }
            }

        return name.appendTo(buf);
        }


    // ----- Iterable methods ----------------------------------------------------------------------

    @Override
    Iterator<Path> iterator()
        {
        return new Iterator<Path>()
            {
            Int i = 0;

            @Override conditional Path next()
                {
                return i < size
                        ? (True, this.Path[i++])
                        : False;
                }
            };
        }

    @Override
    Path[] toArray(Array.Mutability? mutability = Null)
        {
        // start with a mutable or fixed size array full of references to "this"
        Path[] parts = mutability == Mutable
                ? new Path[](size).fill(this, 0 ..< size)
                : new Path[size](this);

        // now replace all the parts other than the last one (which should be "this")
        for (Path path = this, Int i = size - 1; path ?= path.parent; )
            {
            parts[--i] = path;
            }

        // finally, return an array of the desired mutability
        return parts.toArray(mutability, inPlace=True);
        }


    // ----- funky interfaces ----------------------------------------------------------------------

    /**
     * Compare two objects of the same Hashable type for equality.
     *
     * @return True iff the objects are equivalent
     */
    @Override
    static <CompileType extends Path> Boolean equals(CompileType value1, CompileType value2)
        {
        if (   value1.size     != value2.size       // quick check: how "long" the path is
            || value1.absolute != value2.absolute)  // quick check: are they both absolute/relative?
            {
            return False;
            }

        Path path1 = value1;
        Path path2 = value2;
        while (True)
            {
            if (path1.form != path2.form
                || path1.form == Name && path1.name != path2.name)
                {
                return False;
                }

            val parent1 = path1.parent;
            if (parent1 == Null)
                {
                return True;
                }
            path1 = parent1;
            path2 = path2.parent ?: assert;
            }
        }

    @Override
    static <CompileType extends Path> Int hashCode(CompileType value)
        {
        return switch (value.form)
            {
            case Root   : 3736988521;
            case Parent : 12344321317;
            case Current: 98764321261;
            case Name   : value.name.hashCode();
            } ^ (value.parent?.hashCode().rotateLeft(7) : 0);
        }

    @Override
    static <CompileType extends Path> Ordered compare(CompileType value1, CompileType value2)
        {
        // all absolute paths come first in sort order
        if (value1.absolute ^ value2.absolute)
            {
            return value1.absolute ? Lesser : Greater;
            }

        switch (value1.size <=> value2.size)
            {
            case Lesser:
                val result = value1 <=> value2.parent? : assert;
                return result == Equal ? Lesser : result;

            case Equal:
                var result = value1.parent? <=> value2.parent? : Equal;
                if (result == Equal)
                    {
                    result = value1.form <=> value2.form;
                    if (result == Equal && value1.form == Name)
                        {
                        result = value1.name <=> value2.name;
                        }
                    }
                return result;

            case Greater:
                val result = value1.parent? <=> value2 : assert;
                return result == Equal ? Greater : result;
            }
        }
    }