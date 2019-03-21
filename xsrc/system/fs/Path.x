/**
 * Path TODO
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
     * TODO doc
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
     * TODO doc
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
     * TODO doc
     */
    Path resolve(Path that)
        {
        TODO
        }

    /**
     * TODO doc
     */
    Path relativize(Path that)
        {
        TODO
        }

    /**
     * TODO doc
     */
    Path sibling(String name)
        {
        TODO
        }

    /**
     * TODO doc
     */
    Path sibling(Path that)
        {
        TODO
        }

    /**
     * TODO doc
     */
    Path add(String name)
        {
        TODO
        }

    /**
     * TODO doc
     */
    Path add(Path that)
        {
        TODO
        }


    // ----- Sequence methods --------------------------------------------------------------------

    @Override
    @Op("[]")
    Path getElement(Int index)
        {
        if (index < 0)
            {
            throw new OutOfBounds(index.to<String>() + " < 0");
            }
        if (index >= size)
            {
            throw new OutOfBounds(index.to<String>() + " >= " + size);
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
        TODO
        }


    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        Int length = name.size;
        // prepend the parent path and the path separator; if the parent is the root, then no
        // additional separator is added
        if (parent?.form == Root)
            {
            ++length;
            }
        else
            {
            length += parent?.estimateStringLength() + 1;
            }
        return length;
        }

    @Override
    void appendTo(Appender<Char> appender)
        {
        // prepend the parent path and the path separator; if the parent is the root, then no
        // additional separator is added
        parent?.appendTo(appender);
        if (parent?.form != Root)
            {
            appender.add('/');
            }

        name.appendTo(appender);
        }
    }