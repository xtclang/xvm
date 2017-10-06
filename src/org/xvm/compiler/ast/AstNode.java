package org.xvm.compiler.ast;


import java.io.PrintWriter;
import java.io.StringWriter;

import java.lang.reflect.Field;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NoSuchElementException;

import org.xvm.asm.Component;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;

import org.xvm.compiler.Compiler.Stage;
import org.xvm.compiler.ErrorListener;
import org.xvm.compiler.Source;

import org.xvm.util.ListMap;
import org.xvm.util.Severity;

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

    /**
     * Specify a parent for the AstNode.
     *
     * @param parent  the parent node
     */
    protected void setParent(AstNode parent)
        {
        this.parent = parent;
        }

    /**
     * This method recurses through the tree of AstNode objects, allowing each node to introduce
     * itself as the parent of each node under it.
     */
    protected void introduceParentage()
        {
        for (AstNode node : children())
            {
            node.setParent(this);
            node.introduceParentage();
            }
        }

    /**
     * Helper: Given an optional Iterable of child AstNode objects, set the parent of each of the
     * children to be this node.
     *
     * @param children  an Iterable of AstNode, or null
     */
    protected void introduceParentage(Iterable<? extends AstNode> children)
        {
        if (children != null)
            {
            for (AstNode child : children)
                {
                child.setParent(this);
                }
            }
        }

    /**
     * Helper: Given an optional AstNode object, set its parent to this node.
     *
     * @param child  an AstNode, or null
     */
    protected void introduceParentage(AstNode child)
        {
        if (child != null)
            {
            child.setParent(this);
            }
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

    @Override
    public Iterator<AstNode> iterator()
        {
        Field[] fields = getChildFields();
        return fields.length == 0 ? Collections.EMPTY_LIST.iterator() : new ChildIterator(fields);
        }

    /**
     * @return the current compilation stage for this node
     */
    public Stage getStage()
        {
        return stage;
        }

    /**
     * @param stage the updated compilation stage for this node
     */
    void setStage(Stage stage)
        {
        assert stage.ordinal() >= this.stage.ordinal();
        this.stage = stage;
        }

    /**
     * Obtain the Source for this AstNode, if any. By default, a node uses the same source as its
     * parent.
     *
     * @return a Source instance
     */
    public Source getSource()
        {
        AstNode parent = getParent();
        return parent == null
                ? null
                : parent.getSource();
        }

    /**
     * Determine the starting position in the source at which this AstNode occurs.
     *
     * @return the Source position of the AstNode
     */
    public abstract long getStartPosition();

    /**
     * Determine the ending position (exclusive) in the source for this AstNode.
     *
     * @return the Source position of the end of the AstNode
     */
    public abstract long getEndPosition();

    /**
     * Obtain the ComponentStatement for this AstNode, if any.
     *
     * @return the first (walking up the tree) ComponentStatement containing this AstNode
     */
    public ComponentStatement getComponentStatement()
        {
        AstNode parent = getParent();
        return parent == null
                ? null
                : parent.getComponentStatement();
        }

    /**
     * Obtain the Component for this AstNode, if any.
     *
     * @return the Component containing this AstNode
     */
    public Component getComponent()
        {
        return getComponentStatement().getComponent();
        }

    /**
     * @return the constant pool
     */
    protected ConstantPool getConstantPool()
        {
        AstNode nodeParent = getParent();
        return nodeParent == null
                ? null
                : nodeParent.getConstantPool();
        }

    /**
     * For nested nodes, determine the default access if the nodes need to specify an accessibility.
     *
     * @return the accessibility that this node should assume if this node has to specify its own
     *         accessibility and no accessibility is specified
     */
    public Access getDefaultAccess()
        {
        AstNode parent = getParent();
        return parent == null
                ? Access.PUBLIC
                : parent.getDefaultAccess();
        }

    /**
     * Helper to log an error related to this AstNode.
     *
     * @param errs        the ErrorListener to log to
     * @param severity    the severity level of the error; one of
     *                    {@link Severity#INFO}, {@link Severity#WARNING},
     *                    {@link Severity#ERROR}, or {@link Severity#FATAL}
     * @param sCode       the error code that identifies the error message
     * @param aoParam     the parameters for the error message; may be null
     *
     * @return true to attempt to abort the process that reported the error, or
     *         false to attempt continue the process
     */
    public boolean log(ErrorListener errs, Severity severity, String sCode, Object... aoParam)
        {
        Source source = getSource();
        return errs == null
                ? severity.ordinal() >= Severity.ERROR.ordinal()
                : errs.log(severity, sCode, aoParam, source,
                        source == null ? 0L : getStartPosition(),
                        source == null ? 0L : getEndPosition());
        }


    // ----- compile phases ------------------------------------------------------------------------

    /**
     * First logical compiler pass.
     * <p/>
     * <ul>
     * <li>At this point, names are NOT resolvable; we're really just organizing the tree and
     * checking for errors that are obvious from "this point down" (no lateral evaluation of
     * structures, because we can't count on them even existing yet.)</li>
     *
     * <li>The general idea is that this method recurses through the structure, allowing each node
     * to introduce itself as the parent of each node under it, and that the nodes which will be
     * structures in the resulting FileStructure will register themselves.</li>
     *
     * <li>Type parameters for the types must also be registered, because they are also types, and
     * they will be required to already be present when the second pass begins.</li>
     * </ul>
     *
     * @param errs    the error list to log any errors etc. to
     */
    protected void registerStructures(ErrorListener errs)
        {
        stage = Stage.Registered;

        for (AstNode node : children())
            {
            if (node.stage.ordinal() < Stage.Registered.ordinal())
                {
                node.registerStructures(errs);
                }
            }
        }

    /**
     * Second logical compiler pass. This pass has access to imported modules, and is responsible
     * for resolving names.
     * <p/>
     * The rule of thumb is that no questions should be asked of other modules that could not have
     * been answered by this module before this call; in other words, the order of the module
     * compilation is not only unpredictable, but the potential exists for dependencies in either
     * direction (first to last and/or vice versa).
     * <p/>
     * As a result, some questions may come to an AstNode to resolve that it is not yet prepared to
     * resolve, in which case the caller (another AstNode) has to add itself to the list of nodes
     * that require another pass.
     * <p/>
     * <ul>
     * <li>Packages that import modules are able to verify that those modules are available to
     * compile against;</li>
     *
     * <li>Conditionals must be resolvable, e.g. the link-time conditionals defining which types are
     * present and which of their Compositions are in effect.</li>
     * </ul>
     *
     * @param listRevisit  a list to add any nodes to that need to be revisted during this compiler
     *                     pass
     * @param errs         the error list to log any errors etc. to
     */
    public void resolveNames(List<AstNode> listRevisit, ErrorListener errs)
        {
        assert stage.ordinal() <= Stage.Resolved.ordinal();

        // if this node is a NameResolver, then make sure it resolves itself first
        boolean fResolved = true;
        if (this instanceof NameResolver.NameResolving && stage.ordinal() < Stage.Resolved.ordinal())
            {
            NameResolver resolver = ((NameResolver.NameResolving) this).getNameResolver();
            if (resolver != null)
                {
                boolean fFirstTime = resolver.isFirstTime();
                if (resolver.resolve(listRevisit, errs) == NameResolver.Result.DEFERRED)
                    {
                    // the first time through the recursive descent of AST nodes, we need to visit
                    // every node. subsequent visits are only to the nodes that registered
                    // themselves for re-visits due to their inability to resolve themselves fully
                    // in previous visits
                    if (!fFirstTime)
                        {
                        return;
                        }
                    fResolved = false;
                    }
                }
            }

        // before resolving any children, mark this node as resolved, so that it is able to help
        // resolve things on requests from children
        if (fResolved)
            {
            stage = Stage.Resolved;
            }

        for (AstNode node : children())
            {
            // don't visit children that have already successfully resolved
            if (node.stage.ordinal() < Stage.Resolved.ordinal())
                {
                node.resolveNames(listRevisit, errs);
                }
            }
        }

    /**
     * Third logical compiler pass. This pass is responsible for resolving names and structures
     * within methods. To accomplish this, this pass must be able to resolve type names, which is
     * why the first pass was necessarily a separate pass.
     *
     * @param errs  the error list to log any errors etc. to
     */
    protected void generateCode(ErrorListener errs)
        {
        stage = Stage.CodeGen;

        for (AstNode node : children())
            {
            node.generateCode(errs);
            }
        }


    // ----- name resolution -----------------------------------------------------------------------

    /**
     * From the root down, see if one of the parents answers to the specified name.
     *
     * @param sName  a simple name
     *
     * @return the Constant of one of the parents that answers to the specified name, or null
     */
    protected Component resolveParentBySimpleName(String sName)
        {
        AstNode parent = getParent();
        return parent == null ? null : parent.resolveParentBySimpleName(sName);
        }

    /**
     * Determine if this particular node has an import registered on it of the specified name.
     *
     * @param sName  a simple name
     *
     * @return an ImportStatement, or null
     */
    protected ImportStatement resolveImportBySingleName(String sName)
        {
        AstNode parent = getParent();
        return parent == null ? null : parent.resolveImportBySingleName(sName);
        }

    /**
     * @return true iff this AstNode should be able to resolve names
     */
    protected boolean canResolveNames()
        {
        if (this instanceof NameResolver.NameResolving)
            {
            // the problem is this: that a NameResolver that hasn't been invoked as part of the
            // natural pass of the resolveNames() recursion has not had a chance to figure out what
            // the effect its imports may have on name resolution, and thus we can't ask it what a
            // name means
            return !((NameResolver.NameResolving) this).getNameResolver().isFirstTime();
            }

        // for all other components (that don't override this method because they know more about
        // whether or not they can resolve names), we'll assume that if they haven't been resolved,
        // then they don't know how to resolve names
        return stage.ordinal() >= Stage.Resolved.ordinal();
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


    // ----- internal -------------------------------------------------------------------

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

    /**
     * The stage of compilation.
     */
    private Stage stage = Stage.Initial;

    /**
     * The parent of this AstNode.
     */
    private AstNode parent;
    }
