package org.xvm.compiler.ast;


import org.xvm.asm.StructureContainer;

import org.xvm.compiler.ErrorListener;
import org.xvm.compiler.Source;
import org.xvm.util.ListMap;

import java.io.PrintWriter;
import java.io.StringWriter;

import java.lang.reflect.Field;

import java.util.*;

import static org.xvm.util.Handy.indentLines;


/**
 * Common base class for all statements and expressions.
 *
 * @author cp 2017.04.11
 */
public abstract class AstNode
        implements Iterable<AstNode>
    {
    // ----- accessors -----------------------------------------------------------------------------

    /**
     * Obtain the AstNode that contains this node. A parent is configured by the scan phase.
     *
     * @return  the parent node, or null
     */
    public AstNode getParent()
        {
        return parent;
        }

    protected void setParent(AstNode parent)
        {
        this.parent = parent;
        }

    /**
     * Return an Iterable that represents all of the child nodes of this node.
     *
     * @return an Iterable of child nodes (from whence an Iterator can be obtained)
     */
    public Iterable<AstNode> children()
        {
        return this;
        }

    protected Field[] getChildFields()
        {
        return NO_FIELDS;
        }

    public Iterator<AstNode> iterator()
        {
        Field[] fields = getChildFields();
        return fields.length == 0 ? Collections.EMPTY_LIST.iterator() : new ChildIterator(fields);
        }

    /**
     * Obtain the Source for this AstNode, if any. By default, a node uses the same source as its
     * parent.
     *
     * @return a Source instance
     */
    public Source getSource()
        {
        Source  source = null;
        AstNode parent = getParent();
        if (parent != null)
            {
            source = parent.getSource();
            }
        return source;
        }

    /**
     * Obtain the StructureContainer for this AstNode, if any. By default, a node uses the container
     * for its parent.
     *
     * @return a StructureContainer
     */
    public StructureContainer getStructure()
        {
        StructureContainer struct = null;
        AstNode            parent = getParent();
        if (parent != null)
            {
            struct = parent.getStructure();
            }
        return struct;
        }


    // ----- compile phases ------------------------------------------------------------------------

    /**
     * First logical compiler pass. At this point, names are NOT resolvable; we're really just
     * organizing the tree and checking obvious errors that are obvious from "this point down".
     * The general idea is that this method recurses through the structure, allowing each node to
     * introduce itself as the parent of each node under it, and that the nodes which are modules,
     * packages, and classes (but only those nested under modules/packages/classes) will register
     * themselves as appropriate into the corresponding file structure.
     *
     * @param parent  the parent of this node to tie into (if necessary / appropriate)
     * @param errs    the error list to log any errors etc. to
     *
     * @return the node to replace this node with (or null to discard this node)
     */
    protected void registerGlobalNames(AstNode parent, ErrorListener errs)
        {
        setParent(parent);

        for (AstNode node : children())
            {
            node.registerGlobalNames(this, errs);
            }
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public abstract String toString();

    public String toDumpString()
        {
        StringWriter sw = new StringWriter();
        dump(new PrintWriter(sw), "", "");
        return sw.toString();
        }

    public void dump()
        {
        dump(new PrintWriter(System.out, true), "", "");
        }

    protected void dump(PrintWriter out, String sIndentFirst, String sIndent)
        {
        // find the children to dump, but prune out any empty categories
        Map<String, Object> cats = getDumpChildren();
        for (Iterator<Map.Entry<String, Object>> iter = cats.entrySet().iterator(); iter.hasNext(); )
            {
            Map.Entry entry = iter.next();
            Object    value = entry.getValue();
            if (value == null)
                {
                iter.remove();
                }
            else if (value instanceof Map)
                {
                if (((Map) value).isEmpty())
                    {
                    iter.remove();
                    }
                }
            else if (value instanceof Collection)
                {
                if (((Collection) value).isEmpty())
                    {
                    iter.remove();
                    }
                }
            else if (value instanceof Collection)
                {
                if (((Collection) value).isEmpty())
                    {
                    iter.remove();
                    }
                }
            else if (value instanceof Object[])
                {
                if (((Object[]) value).length == 0)
                    {
                    iter.remove();
                    }
                }
            }

        // print out a line of info about this node (if more than one line is necessary, then indent
        // the whole thing under the top line)
        if (sIndentFirst.length() > 0)
            {
            out.print(sIndentFirst + "- ");
            }
        out.print(getClass().getSimpleName());

        String sThis = getDumpDesc();
        if (sThis == null || sThis.length() == 0)
            {
            out.println();
            }
        else if (sThis.indexOf('\n') < 0)
            {
            out.println(": " + sThis);
            }
        else
            {
            out.println();
            out.println(indentLines(sThis, sIndent + (cats.isEmpty() ? "      " : " |    ")));
            }

        // for each category, print the category name, then print the child nodes under it; if
        // there's only one child node, then just stick it on the same line
        String sIndentCat   = sIndent + "   |- ";
        String sIndentKid   = sIndent + "   |   |";         // all kids except last cat
        String sIndentLastC = sIndent + "       |";         // last cat kids except last kid
        String sIndentLastK = sIndent + "        ";         // last kid on last cat
        int cCats = 0;
        for (Iterator<Map.Entry<String, Object>> iterC = cats.entrySet().iterator(); iterC.hasNext(); )
            {
            boolean                   fLastC = (++cCats == cats.size());
            Map.Entry<String, Object> entry  = iterC.next();
            String                    sCat   = entry.getKey();
            Object                    value  = entry.getValue();

            // category name
            out.print(sIndentCat + sCat);

            // find the kids
            int      cKids = 0;
            Iterator iterK = null;
            if (value instanceof Map)
                {
                Map kids = (Map) value;
                cKids = kids.size();
                iterK = kids.entrySet().iterator();
                }
            else if (value instanceof Collection)
                {
                Collection kids = (Collection) value;
                cKids = kids.size();
                iterK = kids.iterator();
                }
            else if (value instanceof Object[])
                {
                Object[] kids = (Object[]) value;
                cKids = kids.length;
                iterK = Arrays.asList(kids).iterator();
                }
            else
                {
                cKids = 1;
                iterK = Collections.singletonList(value).iterator();
                }

            for (int i = 0; i < cKids; ++i)
                {
                Object  kid      = iterK.next();
                boolean fFirstK  = (i == 0);
                boolean fLastK   = (i == cKids - 1);
                String  sIndent1 = fLastC ? sIndentLastC : sIndentKid;
                String  sIndent2 = fLastC ? (fLastK ? sIndentLastK : sIndentLastC) : sIndentKid;

                if (kid instanceof AstNode)
                    {
                    if (fFirstK)
                        {
                        out.println();
                        }
                    ((AstNode) kid).dump(out, sIndent1, sIndent2);
                    }
                else if (kid instanceof Map.Entry)
                    {
                    if (fFirstK)
                        {
                        out.println();
                        }
                    throw new UnsupportedOperationException("TODO");
                    }
                else // any old object
                    {
                    String sKid = String.valueOf(kid);
                    if (sKid.indexOf('\n') < 0)
                        {
                        if (cKids == 1)
                            {
                            out.print(": ");
                            }
                        else
                            {
                            if (fFirstK)
                                {
                                out.println();
                                }
                            out.print(sIndent1 + "- ");
                            }
                        out.println(sKid);
                        }
                    else
                        {
                        if (fFirstK)
                            {
                            out.println();
                            }
                        sKid = indentLines(sKid, sIndent2 + "  ");
                        sKid = sIndent1 + "- " + sKid.substring(sIndent1.length() + 2);
                        out.println(sKid);
                        }
                    }
                }
            }
        }

    public String getDumpDesc()
        {
        return null;
        }

    /**
     * Build and return a map that allows the caller to navigate the children of this node.
     * <p/>
     * Assume some type T which represents either an AstNode instance, or an object that implements
     * toString(). The keys of the map should be strings that describe the categories of the
     * children, while the values should provide the info about the children of this AstNode,
     * either as an object of type T, a Collection of type T, an array of type T, or a Map whose
     * keys and values are of type T.
     *
     * @return a map containing all the child information to dump
     */
    public Map<String, Object> getDumpChildren()
        {
        Field[] fields = getChildFields();
        if (fields.length == 0)
            {
            return Collections.EMPTY_MAP;
            }

        Map<String, Object> map = new ListMap<>();
        for (Field field : fields)
            {
            try
                {
                map.put(field.getName(), field.get(this));
                }
            catch (IllegalAccessException e)
                {
                throw new IllegalStateException(e);
                }
            }
        return map;
        }


    // ----- inner class: ChildIterator ------------------------------------------------------------

    protected static Field[] fieldsForNames(Class clz, String... names)
        {
        if (names == null || names.length == 0)
            {
            return NO_FIELDS;
            }

        Field[] fields = new Field[names.length];
        NextField: for (int i = 0, c = fields.length; i < c; ++i)
            {
            Class                clzTry = clz;
            NoSuchFieldException eOrig  = null;
            while (clzTry != null)
                {
                try
                    {
                    fields[i] = clzTry.getDeclaredField(names[i]);
                    assert fields[i] != null;
                    continue NextField;
                    }
                catch (NoSuchFieldException e)
                    {
                    if (eOrig == null)
                        {
                        eOrig = e;
                        }

                    clzTry = clzTry.getSuperclass();
                    if (clz == null)
                        {
                        throw new IllegalStateException(eOrig);
                        }
                    }
                catch (SecurityException e)
                    {
                    throw new IllegalStateException(e);
                    }
                }
            }

        return fields;
        }


    // ----- inner class: ChildIterator ------------------------------------------------------------

    protected final class ChildIterator
            implements Iterator<AstNode>
        {
        protected ChildIterator(Field[] fields)
            {
            this.fields = fields;
            }

        public boolean hasNext()
            {
            return state == HAS_NEXT || prepareNextElement();
            }

        public AstNode next()
            {
            if (state == HAS_NEXT || prepareNextElement())
                {
                state = HAS_PREV;
                if (value instanceof AstNode)
                    {
                    return (AstNode) value;
                    }
                else
                    {
                    return (AstNode) ((Iterator) value).next();
                    }
                }

            throw new NoSuchElementException();
            }

        private boolean prepareNextElement()
            {
            if (value instanceof Iterator && ((Iterator) value).hasNext())
                {
                state = HAS_NEXT;
                return true;
                }

            boolean prepped = prepareNextField();
            state = prepped ? HAS_NEXT : NOT_PREP;
            return prepped;
            }

        private boolean prepareNextField()
            {
            while (++iField < fields.length)
                {
                Object next;
                try
                    {
                    next = fields[iField].get(AstNode.this);
                    }
                catch (NullPointerException e)
                    {
                    throw new IllegalStateException("class=" + AstNode.this.getClass().getSimpleName()
                            + ", field=" + iField);
                    }
                catch (IllegalAccessException e)
                    {
                    throw new IllegalStateException(e);
                    }

                if (next != null)
                    {
                    if (next instanceof List)
                        {
                        List list = (List) next;
                        if (!list.isEmpty())
                            {
                            value = list.iterator();
                            return true;
                            }
                        }
                    else if (next instanceof Collection)
                        {
                        Collection coll = (Collection) next;
                        if (!coll.isEmpty())
                            {
                            value = coll.iterator();
                            return true;
                            }
                        }
                    else
                        {
                        value = next;
                        return true;
                        }
                    }
                }

            value = null;
            return false;
            }

        public void remove()
            {
            if (state == HAS_PREV)
                {
                if (value instanceof AstNode)
                    {
                    // null out the field
                    try
                        {
                        fields[iField].set(AstNode.this, null);
                        }
                    catch (IllegalAccessException e)
                        {
                        throw new IllegalStateException(e);
                        }
                    state = NOT_PREP;
                    return;
                    }

                if (value instanceof Iterator)
                    {
                    // tell the underlying iterator to remove the value
                    ((Iterator) value).remove();
                    state = NOT_PREP;
                    return;
                    }
                }

            throw new IllegalStateException();
            }

        public void replace(AstNode newChild)
            {
            if (state == HAS_PREV)
                {
                if (value instanceof AstNode)
                    {
                    // the field holds a single node; store the new value
                    try
                        {
                        fields[iField].set(AstNode.this, newChild);
                        }
                    catch (IllegalAccessException e)
                        {
                        throw new IllegalStateException(e);
                        }
                    return;
                    }

                if (value instanceof ListIterator)
                    {
                    ((ListIterator) value).set(newChild);
                    return;
                    }
                }

            throw new IllegalStateException();
            }

        private static final int NOT_PREP = 0;
        private static final int HAS_NEXT = 1;
        private static final int HAS_PREV = 2;

        private Field[] fields;
        private int     iField = -1;
        private Object  value;
        private int     state  = NOT_PREP;
        }


    // ----- fields --------------------------------------------------------------------------------

    protected static final Field[] NO_FIELDS = new Field[0];

    protected AstNode parent;
    }
