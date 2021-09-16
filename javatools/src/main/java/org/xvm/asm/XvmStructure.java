package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.IdentityConstant;

import org.xvm.util.Severity;


/**
 * Represents any of the various XVM structures, which are hierarchical in nature, and include such
 * structures as modules, namespaces, classes, methods, properties, and so on.
 * <p/>
 * The purpose of an XVM structure is to encapsulate a broad range of complexity behind a relatively
 * uniform surface area, which is a fairly significant challenge. Quite a few design decisions are
 * hidden behind that limited surface area, but even with significant effort, the surface area has
 * expanded beyond what one would consider "small". Hereunder is an attempt to capture a few of the
 * rationales for the design:
 * <ul>
 * <li>The design of the binary structure that is emitted by the XTC compiler (and consumed by the
 * XVM) is hierarchical, and thus there is a natural correlation to the hierarchical tree of XVM
 * structures.</li>
 *
 * <li>As the binary structure is both emitted and consumed, it is necessary to be able to persist
 * the runtime structure into a binary structure, and to be able to load that binary structure into
 * the runtime structure. The XVM structure thus can be constructed from a stream, and persisted to
 * a stream.</li>
 *
 * <li>A decision was made to lock each node to a particular XVM structure hierarchy, such that a
 * node from one module (for example) could not simply be "added" to a different module. This is a
 * consequence of the tree structure being maintained from the leaf upwards by way of immutable
 * references within the tree from node to parent. This allows a node to assume that it will only
 * ever exist within the confines of the parent that it was introduced to at construction, and that
 * guarantee is recursively enforced all the way to the root. As a result, a node may be able to
 * optimize a number of its responsibilities, for example by caching information that is immutable
 * as the result of its lineage being fixed.</li>
 *
 * <li>Since the parent of a node cannot be changed after construction, it does mean that the node
 * has to be "born to" the right parent. This lends itself to an API style in which a parent exposes
 * factory-like methods that are responsible for the instantiation of any child nodes.</li>
 *
 * <li>The {@link #getContaining()} method provides access to the parent, while the {@link
 * #getContained()} method provides the children. In general, the only XVM Structure without a
 * parent (i.e. the only root structure) is the {@link FileStructure}, which acts as an envelope for
 * XVM Structures. Many XVM Structures, such as classes and methods, have an identity that is unique
 * (at least within the scope of their parent), which is represented by the value returned from the
 * {@link #getIdentityConstant()} method.</li>
 *
 * <li>The manner in which a hierarchy is created from a binary is called disassembly. Disassembly
 * is triggered in a recursive manner by constructing a {@link FileStructure FileStructure}, which
 * represents an outer-most "envelope" for XVM structures. The result of disassembly should be equal
 * to the XVM structure from which the binary was originally created from. Furthermore, the result
 * should itself be mutable, and subsequently persistable; that means that a binary can be
 * constituted (disassembled) into an XVM structure, modified, and persisted, supporting a wide
 * range of tool chain capabilities.</li>
 *
 * <li>The manner in which a hierarchy is transformed into a binary is called assembly. Assembly is
 * a two-phase process. In the first phase, the hierarchy is traversed, and every XVM Structure in
 * the hierarchy registers the constant values (if any) that it uses. After the first phase, the
 * constants in the constant pool that are unused can be discarded, and the pool can be ordered in a
 * way that places the most-used constants first (which reduces the sum-size of the variably-sized
 * integers used to specify constant identities). Once the constants and their order are finalized,
 * then the XVM Structures recursively write their structures out to create the binary (the
 * serialized form).</li>
 *
 * <li>The XVM Structure also supports conditional structure inclusion, which is a "link-time"
 * capability that corresponds to the type of functionality that one can achieve with a language
 * <i>pre</i>-processor in other languages. Among other things, it allows the resulting (resolved)
 * XVM structure to differ based on the presence and/or the version of various other libraries,
 * based on any number of arbitrary named link-time options, and based on the version of <i>this</i>
 * module. It is a requirement that each and every possible combination of conditions results in a
 * <b>verifiable</b> module, i.e. one that is correct according to the XVM specification. To
 * accomplish conditional structure inclusion, each XVM structure in the hierarchy can have a
 * {@link ConditionalConstant ConditionalConstant} associated with it.</li>
 * </ul>
 */
public abstract class XvmStructure
        implements Constants
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct an XVM structure.
     *
     * @param xsParent  the containing XVM structure
     */
    protected XvmStructure(XvmStructure xsParent)
        {
        m_xsParent = xsParent;
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    /**
     * Obtain the containing XVM structure.
     *
     * @return the XvmStructure that contains this XvmStructure, or null if this XvmStructure is not
     *         contained by another XvmStructure
     */
    public XvmStructure getContaining()
        {
        return m_xsParent;
        }

    /**
     * Modify the containing XVM structure.
     *
     * @param xsParent  the XvmStructure that will contain this XvmStructure
     */
    protected void setContaining(XvmStructure  xsParent)
        {
        m_xsParent = xsParent;
        }

    /**
     * Get a reference to the FileStructure that contains this XvmStructure.
     *
     * @return the FileStructure
     */
    public FileStructure getFileStructure()
        {
        return getContaining().getFileStructure();
        }

    /**
     * Return the name of the source file that this structure origines from.
     *
     * @return the file name
     */
    public String getSourceFileName()
        {
        XvmStructure child = this;
        while (child != null)
            {
            XvmStructure parent = child.getContaining();
            if (parent instanceof Component)
                {
                Component componentParent = (Component) parent;
                Component.Format formatParent = componentParent.getFormat();
                if (formatParent == Component.Format.MODULE)
                    {
                    return componentParent.getSimpleName() + ".x";
                    }
                else if (formatParent == Component.Format.PACKAGE && child instanceof Component)
                    {
                    return ((Component) child).getSimpleName() + ".x";
                    }
                }
            child = parent;
            }

        return "<unknown>";
        }

    /**
     * Get a reference to the ConstantPool that is shared by all of the XvmStructures within the
     * same FileStructure.
     *
     * @return  the ConstantPool
     */
    public ConstantPool getConstantPool()
        {
        return getContaining().getConstantPool();
        }

    /**
     * If this XvmStructure has an identity that is a Constant in the ConstantPool, then obtain that
     * identity.
     *
     * @return the Constant that represents the identity of this XvmStructure; otherwise null
     */
    public IdentityConstant getIdentityConstant()
        {
        return null;
        }

    /**
     * Obtain the nested XVM structures contained within this XVM structure. The caller should treat
     * the return value as if it were immutable.
     *
     * @return an Iterable object representing all nested XVM structures
     */
    public Iterator<? extends XvmStructure> getContained()
        {
        return Collections.emptyIterator();
        }

    /**
     * Determine if the XVM structure (or any nested XVM structure) has been modified.
     *
     * @return true if the XVM structure has been modified since the last call to
     *         {@link #resetModified()}
     */
    public boolean isModified()
        {
        for (Iterator<? extends XvmStructure> iter = getContained(); iter.hasNext(); )
            {
            if (iter.next().isModified())
                {
                return true;
                }
            }

        return false;
        }

    /**
     * Mark the XVM structure as having been modified.
     * <p/>
     * After calling this method, and before a call to {@link #resetModified()} occurs,
     * {@link #isModified()} must return true.
     */
    protected abstract void markModified();

    /**
     * If the XVM structure has been modified, reset that modified status such that subsequent calls
     * to {@link #isModified()} will return {@code false}.
     * <p/>
     * After calling this method, and before any further modifications occur, {@link #isModified()}
     * must return false.
     */
    protected void resetModified()
        {
        for (Iterator<? extends XvmStructure> iter = getContained(); iter.hasNext(); )
            {
            iter.next().resetModified();
            }
        }

    /**
     * Determine whether the presence of this XVM structure is conditional.
     *
     * @return false iff there are no conditions for the presence of this XVM structure with respect
     *         to the presence of its module; true if this XVM structure may or may not be present
     *         at runtime within its module
     */
    public boolean isConditional()
        {
        return getCondition() != null || m_xsParent.isConditional();
        }

    /**
     * Obtain the condition for this XVM Structure. Note that this is not an aggregate condition;
     * this XVM Structure is likely to be nested within another XVM Structure, and so on, and the
     * "true" condition for this XVM Structure is the aggregate of each of those conditions, from
     * the outermost to the innermost.
     *
     * @return the ConditionalConstant that represents the condition (if any) for this XVM Structure
     *         to be available at runtime
     */
    public ConditionalConstant getCondition()
        {
        return null;
        }

    /**
     * Obtain the condition for the entire path from the root XVM Structure down to this XVM
     * Structure.
     *
     * @return
     */
    public ConditionalConstant getAggregateCondition()
        {
        ConditionalConstant[] conds = aggregateConditions(0);
        if (conds == null)
            {
            return null;
            }

        return conds.length == 1
                ? conds[0]
                : getConstantPool().ensureAllCondition(conds);
        }

    /**
     * Aggregate all of the conditions for a specified XVM Structure by walking the tree up to its
     * root.
     *
     * @param cConditions  the number of conditions present further down in the tree
     *
     * @return an array of conditions, or nullif the XVM Structure is not conditional
     */
    protected ConditionalConstant[] aggregateConditions(int cConditions)
        {
        ConditionalConstant[] conds  = null;
        XvmStructure          parent = m_xsParent;
        ConditionalConstant   cond   = getCondition();
        if (cond != null)
            {
            ++cConditions;
            }

        if (parent == null)
            {
            // we're at the top of the tree; create the storage to hold the aggregated conditions
            // (if any)
            if (cConditions > 0)
                {
                conds = new ConditionalConstant[cConditions];
                }
            }
        else
            {
            // go up the tree
            conds = parent.aggregateConditions(cConditions);
            }

        // add our condition to the end
        if (cond != null)
            {
            conds[conds.length-cConditions] = cond;
            }

        return conds;
        }

    /**
     * Specify a condition for this XVM Structure. Note that this condition is <i>in addition to</i>
     * any condition that applies to the parent of this XVM Structure.
     *
     * @param condition  the ConditionalConstant that represents the condition for this XVM
     *                   Structure to be available at runtime, or null to specify that this XVM
     *                   Structure has no additional conditions to its presence at runtime
     */
    protected void setCondition(ConditionalConstant condition)
        {
        throw new UnsupportedOperationException();
        }

    /**
     * Remove any portions of the XVM Structure that are conditionally present only for the passed
     * condition. One example use of this method is to remove code that is tagged as only being used
     * for "test" or "debug".
     *
     * @param condition a NamedCondition, a PresentCondition, or a VersionedCondition, or a
     *                  NotCondition of any of the above
     */
    protected void purgeCondition(ConditionalConstant condition)
        {
        for (Iterator<? extends XvmStructure> iter = getContained(); iter.hasNext(); )
            {
            iter.next().purgeCondition(condition);
            }
        }

    /**
     * Given a specified context, determine if this XVM Structure would be present at runtime.
     *
     * @param ctx  a LinkerContext that specifies what other modules (and versions thereof) are
     *             available, what the contents of those modules are, what the version of this
     *             module is, and what named options are specified
     *
     * @return true if this XVM Structure would be present given the specified context
     */
    public boolean isPresent(LinkerContext ctx)
        {
        if (!m_xsParent.isPresent(ctx))
            {
            return false;
            }

        final ConditionalConstant cond = getCondition();
        return cond == null || cond.isPresent(ctx);
        }

    /**
     * Determine if this XVM Structure is resolved. An XVM Structure is considered resolved if it
     * not subject to variation from conditional inclusion.
     *
     * TODO CP: this method is not currently used and the name is ambiguous and confusing
     *
     * @return true iff the XVM Structure is not subject to conditional inclusion
     *
     * @see ConditionalConstant
     */
    public boolean isResolved()
        {
        if (getCondition() != null)
            {
            return false;
            }

        for (Iterator<? extends XvmStructure> iter = getContained(); iter.hasNext(); )
            {
            if (!iter.next().isResolved())
                {
                return false;
                }
            }

        return true;
        }

    /**
     * Use the specified context to evaluate and thus eliminate conditional inclusion within this
     * XVM Structure.
     *
     * @param ctx  a LinkerContext that specifies what other modules (and versions thereof) are
     *             available, what the contents of those modules are, what the version of this
     *             module is, and what named options are specified
     */
    public void resolve(LinkerContext ctx)
        {
        // just pass down the resolve to any children; note that this must be overridden if any of
        // the children themselves can be discarded as part of the resolve process
        for (Iterator<? extends XvmStructure> iter = getContained(); iter.hasNext(); )
            {
            iter.next().resolve(ctx);
            }
        }

    /**
     * Read an XVM structure from the DataInput stream.
     *
     * @param in  the DataInput containing the XVM structure
     *
     * @throws IOException  if an I/O exception occurs during disassembly from the provided
     *                      DataInput stream, or if there is invalid data in the stream
     */
    protected void disassemble(DataInput in)
            throws IOException
        {
        }

    /**
     * The first assembly step collects the necessary entries for the constant pool.  During this
     * step, all constants used by the XVM structure and any sub-structures are registered with (but
     * not yet bound by position in) the constant pool.
     *
     * @param pool  the ConstantPool with which to register each constant referenced by the XVM
     *              structure
     */
    protected void registerConstants(ConstantPool pool)
        {
        for (Iterator<? extends XvmStructure> iter = getContained(); iter.hasNext(); )
            {
            iter.next().registerConstants(pool);
            }
        }

    /**
     * The second assembly step writes the XVM structure to the DataOutput stream.
     *
     * @param out  the DataOutput to write the XVM structure to
     *
     * @throws IOException  if an I/O exception occurs during assembly to the provided DataOutput
     *                      stream
     */
    protected void assemble(DataOutput out)
            throws IOException
        {
        }

    /**
     * Validate the XvmStructure and its contents, checking for any errors or violations of the XVM
     * specification, and reporting any such errors to the specified {@link ErrorListener}.
     *
     * @param errlist  the ErrorListener to log errors to
     *
     * @return true if the validation process was halted before it completed, for example if the
     *         error list reached its size limit
     */
    public boolean validate(ErrorListener errlist)
        {
        for (Iterator<? extends XvmStructure> iter = getContained(); iter.hasNext(); )
            {
            if (iter.next().validate(errlist))
                {
                return true;
                }
            }

        return false;
        }

    /**
     * Log an error against this structure.
     *
     * @param errs     the error list to log to, or null to use the runtime ErrorListener
     * @param sev      the severity of the error
     * @param sCode    the error code
     * @param aoParam  the parameters of the error
     */
    public boolean log(ErrorListener errs, Severity sev, String sCode, Object ... aoParam)
        {
        // TODO need a way to log to compiler error list if we have compile-time info on the location in the source code
        return ensureErrorListener(errs).log(sev, sCode, aoParam, this);
        }

    /**
     * Make sure that an error listener is returned to use.
     *
     * @param  errs  an error listener, or null
     *
     * @return the error listener passed in, if it was not null, otherwise the previously specified
     *         error listener, otherwise the runtime error listener
     */
    public ErrorListener ensureErrorListener(ErrorListener errs)
        {
        return errs == null ? getErrorListener() : errs;
        }

    /**
     * @return the error listener, if provided, otherwise the runtime error listener
     */
    public ErrorListener getErrorListener()
        {
        return m_xsParent.getErrorListener();
        }

    /**
     * Specify an error listener.
     *
     * @param errs  the error listener
     */
    public void setErrorListener(ErrorListener errs)
        {
        m_xsParent.setErrorListener(errs);
        }


    // ----- debugging support ---------------------------------------------------------------------

    /**
     * Assemble a comma-delimited "key=value" string.
     *
     * @return a String description of the attributes of this XVM Structure
     */
    public abstract String getDescription();

    /**
     * Obtain the output from a full dump of this XVM Structure as a String. This is particularly
     * helpful when in a debugger; place a watch on this method, and the value of the watch will be
     * the dump of the entire XVM Structure.
     *
     * @return a String containing a dump of this XVM Structure
     */
    public String toDebugString()
        {
        StringWriter sw  = new StringWriter(1024);
        PrintWriter  out = new PrintWriter(sw);
        dump(out);
        out.flush();
        return sw.toString();
        }

    /**
     * For debugging purposes, dump the contents of the XVM Structure to the provided PrintWriter.
     *
     * @param out  the PrintWriter to dump to, or null to dump to the console
     */
    public void dump(PrintWriter out)
        {
        if (out == null)
            {
            out = System.console().writer();
            }

        dump(out, "");
        }

    /**
     * This is the method that implements the actual debugging dump of the contents of this XVM
     * Structure, and is responsible for cascading the dump request to any contained XVM Structures.
     *
     * @param out      the PrintWriter to dump to
     * @param sIndent  an indentation string to use at the beginning of each line of output; any
     *                 contained XVM Structures should be indented
     */
    protected abstract void dump(PrintWriter out, String sIndent);

    /**
     * A helper method to dump out the contents of a collection of XVM
     * Structures.
     *
     * @param out          the PrintWriter to dump to
     * @param sIndent      an indentation string to use at the beginning of each line of output; any
     *                     contained XVM Structures should be indented
     * @param sTitle       the title for the section of XVM Structures
     * @param collStructs  the collection of XVM Structure
     */
    protected void dumpStructureCollection(PrintWriter out, String sIndent, String sTitle,
            Collection<? extends XvmStructure> collStructs)
        {
        if (collStructs != null && !collStructs.isEmpty())
            {
            out.print(sIndent);
            out.println(sTitle);

            int i = 0;
            String sIndentMore = nextIndent(sIndent);
            for (XvmStructure xs : collStructs)
                {
                StringBuilder sb = new StringBuilder();
                sb.append(sIndent)
                  .append('[')
                  .append(i++)
                  .append("]");

                if (xs == null || xs instanceof Constant)
                    {
                    sb.append('=')
                      .append(xs);
                    out.println(sb.toString());
                    }
                else
                    {
                    sb.append(':');
                    out.println(sb.toString());
                    xs.dump(out, sIndentMore);
                    }
                }
            }
        }

    /**
     * A helper method to dump out the contents of a map containing XVM Structures.
     *
     * @param out          the PrintWriter to dump to
     * @param sIndent      an indentation string to use at the beginning of each line of output; any
     *                     contained XVM Structures should be indented
     * @param sTitle       the title for the section of XVM Structures
     * @param mapStructs   the map from identity to XVM Structure
     */
    protected void dumpStructureMap(PrintWriter out, String sIndent, String sTitle,
            Map<? extends Object, ? extends XvmStructure> mapStructs)
        {
        if (mapStructs != null && !mapStructs.isEmpty())
            {
            out.print(sIndent);
            out.println(sTitle);

            int i = 0;
            String sIndentMore = nextIndent(sIndent);
            for (Map.Entry<? extends Object, ? extends XvmStructure> entry : mapStructs.entrySet())
                {
                StringBuilder sb = new StringBuilder();
                sb.append(sIndent)
                  .append('[')
                  .append(i++)
                  .append("]=")
                  .append(entry.getKey());
                out.println(sb.toString());

                XvmStructure xs = entry.getValue();
                if (xs == null)
                    {
                    out.print(sIndentMore);
                    out.print(xs);
                    }
                else
                    {
                    xs.dump(out, sIndentMore);
                    }
                }
            }
        }

    /**
     * Obtain a String to use for indentation for the next nested level of output by
     * {@link #dump(PrintWriter, String)}}.
     *
     * @param sIndent the indentation to use for the current nested level
     *
     * @return the indentation to use for the next nested level
     */
    protected String nextIndent(String sIndent)
        {
        return sIndent + "  ";
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        Constant constant = getIdentityConstant();
        if (constant != null)
            {
            return constant.hashCode();
            }

        throw new UnsupportedOperationException();
        }

    @Override
    public abstract boolean equals(Object obj);

    @Override
    public String toString()
        {
        return getClass().getSimpleName() + '{' + getDescription() + '}';
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The containing XVM structure.
     */
    private XvmStructure m_xsParent;
    }
