package org.xvm.asm;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.VersionConstant;
import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.NamedCondition;
import org.xvm.asm.constants.PresentCondition;
import org.xvm.asm.constants.VersionMatchesCondition;


/**
 * The AssemblerContext is used to notify the assembler of changes in the current set of conditions
 * during the assembly process. For example, when a compiler is emitting XVM structures, it will
 * modify the assembler context to notify the assembler each time it enters or leaves a conditional
 * scope of code.
 * <p/>
 * The challenge is to factor out redundancy
 */
public class AssemblerContext
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct an AssemblerContext.
     *
     * @param pool  the ConstantPool of the FileStructure that this AssemblerContext is being used
     *              for
     */
    public AssemblerContext(ConstantPool pool)
        {
        assert pool != null;
        m_pool = pool;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * Push a condition onto the stack.
     *
     * @param condition  a ConditionalConstant
     */
    public void push(ConditionalConstant condition)
        {
        // store the condition
        m_listCondition.add(condition);

        // associated (and lazily instantiated) SimulatedLinkerContext
        m_listLinkerContexts.add(null);

        // if recursing to a new depth for the first time (i.e. under the specific parent
        // condition), store off the modification count so that a subsequent pop() will restore the
        // current indicator value
        if (m_listCondition.size() > m_listIndicators.size())
            {
            m_listIndicators.add(m_nIndicator);
            }

        // update the modification counter and the modification indicator
        m_nIndicator = ++m_cMods;
        }

    /**
     * Pop a condition off of the stack.
     *
     * @return the ConditionalConstant that was on the top of the stack, or null if the stack is
     *         empty
     */
    public ConditionalConstant pop()
        {
        final int cOldDepth = m_listCondition.size();
        if (cOldDepth == 0)
            {
            return null;
            }

        final int cNewDepth = cOldDepth - 1;
        final ConditionalConstant condition = m_listCondition.remove(cNewDepth);
        m_listLinkerContexts.remove(cNewDepth);

        int cIndicators = m_listIndicators.size();
        if (cOldDepth > cIndicators)
            {
            // more than one pop() has occurred in a row, so the topmost modification indicator
            // corresponds to a branch that no longer exists
            m_listIndicators.remove(--cIndicators);
            }

        // restore the indicator from before the corresponding push() occurred
        m_nIndicator = m_listIndicators.get(cIndicators - 1);

        ++m_cMods;
        return condition;
        }

    /**
     * Obtain the modification count for the data structure. This allows the caller to determine if
     * any conditions have been added or removed.
     *
     * @return the number of modifications that have been made to this data structure
     */
    public long getModificationCount()
        {
        return m_cMods;
        }

    /**
     * Obtain a modification indicator for the data structure. The modification indicator provides a
     * very fast way to determine if a change to the contents of the data structure has occurred; if
     * the modification indicator has not changed, then the contents of the structure are identical
     * to what they were. (Note that the opposite is not necessarily true: If the modification
     * indicator has changed, it is possible that the contents are identical to what they were, in
     * which case a detailed evaluation of the contents would be necessary to prove equality or
     * inequality.)
     *
     * @return an opaque value that can be compared to another return value from this same method
     */
    public long getModificationIndicator()
        {
        return m_nIndicator;
        }

    /**
     * Determine the count of conditions currently tracked by this data structure. Each call to one
     * of the "begin" methods will increment this value, and each corresponding call to the
     * appropriately matched "end" method will decrement this value.
     *
     * @return the current number of conditions
     */
    public int getConditionCount()
        {
        return m_listCondition.size();
        }

    /**
     * Obtain a condition from the condition stack. Note that the element at index 0 is the first
     * element to have been pushed onto the stack, and the element at index {@link
     * #getConditionCount()}-1 is the last element to have been pushed onto the stack, i.e. the
     * "top" of the stack.
     *
     * @param i  an index between 0 and {@link #getConditionCount()}-1
     *
     * @return the specified condition
     */
    public ConditionalConstant getCondition(int i)
        {
        assert i >= 0 && i < getConditionCount();
        return m_listCondition.get(i);
        }

    /**
     * Get a condition that represents all of the conditions that have been introduced since the
     * passed indicator was obtained from {@link #getModificationIndicator()}.
     *
     * @param nIndicator  a value previously return from the getModificationIndicator() method
     *
     * @return a condition, or null if no additional conditions have been introduced
     */
    public ConditionalConstant getConditionSince(long nIndicator)
        {
        if (nIndicator == m_nIndicator)
            {
            return null;
            }

        // find the indicator in the stack of indicators
        final ArrayList<Long> listIndicators = m_listIndicators;
        for (int iLast = listIndicators.size() - 1, i = iLast; i >= 0; --i)
            {
            if (nIndicator == listIndicators.get(i))
                {
                if (i == iLast)
                    {
                    // only the last condition was added
                    return m_listCondition.get(iLast);
                    }
                else
                    {
                    // multiple conditions were added
                    int cConditions = iLast - i + 1;
                    ConditionalConstant[] acondition = new ConditionalConstant[cConditions];
                    for (int iCondition = 0; iCondition < cConditions; ++iCondition)
                        {
                        acondition[iCondition] = m_listCondition.get(i + iCondition);
                        }
                    m_pool.ensureAllCondition(acondition);
                    }
                }
            }

        throw new IllegalStateException("condition indicator " + nIndicator + " no longer exists");
        }

    /**
     * @return a ConditionalConstant iff the AssemblerContext has any conditions registered, or null
     */
    public ConditionalConstant asCondition()
        {
        ArrayList<ConditionalConstant> list = m_listCondition;

        if (list.isEmpty())
            {
            return null;
            }

        if (list.size() == 1)
            {
            return list.get(0);
            }

        return m_pool.ensureAllCondition(list.toArray(new ConditionalConstant[0]));
        }

    /**
     * @return a LinkerContext that corresponds to the current conditions specified in the
     *         AssemblerContext
     */
    public SimulatedLinkerContext getLinkerContext()
        {
        ArrayList<SimulatedLinkerContext> list = m_listLinkerContexts;
        if (list.isEmpty())
            {
            return SimulatedLinkerContext.EMPTY;
            }

        SimulatedLinkerContext ctx = list.get(list.size() - 1);
        if (ctx == null)
            {
            ConditionalConstant cond = asCondition();
            ctx = m_mapContextCache.get(cond);
            if (ctx == null)
                {
                ctx = new SimulatedLinkerContext(cond);
                m_mapContextCache.put(cond, ctx);   // for now, just cache them all
                }
            list.set(list.size() - 1, ctx);
            }
        return ctx;
        }


    // ----- public helpers ------------------------------------------------------------------------

    /**
     * Start a section of the assembly that applies only if the specified name is <i>defined</i>.
     * <p/>
     * This method is NOT idempotent; each call to "begin" must be matched with a call to the
     * corresponding "end".
     *
     * @param sName  the name that must be <i>defined</i>
     */
    public void beginIfSpecified(String sName)
        {
        push(m_pool.ensureNamedCondition(sName));
        }

    /**
     * End a section of the assembly that applies only if the specified name is <i>defined</i>. This
     * call must correspond to a previous call to {@link #beginIfSpecified}.
     * <p/>
     * This method is NOT idempotent; each call to "begin" must be matched with a call to the
     * corresponding "end".
     *
     * @param sName  the name previously passed to {@link #beginIfSpecified}
     */
    public void endIfSpecified(String sName)
        {
        ConditionalConstant condition = pop();
        if (!(condition instanceof NamedCondition && sName.equals(((NamedCondition) condition).getName())))
            {
            throw new IllegalStateException("expected NamedCondition(\"" + sName + "\"); found: " + condition);
            }
        }

    /**
     * Start a section of the assembly that applies only if the specified XVM Constant is visible
     * (available to be used).
     * <p/>
     * This method is NOT idempotent; each call to "begin" must be matched with a call to the
     * corresponding "end".
     *
     * @param constId  the identity of the XVM Structure that must be visible
     */
    public void beginIfVisible(IdentityConstant constId)
        {
        push(m_pool.ensurePresentCondition(constId));
        }

    /**
     * End a section of the assembly that applies only if the specified XVM Constant is visible
     * (available to be used). This call must correspond to a previous call to
     * {@link #beginIfVisible}.
     * <p/>
     * This method is NOT idempotent; each call to "begin" must be matched with a call to the
     * corresponding "end".
     *
     * @param constId  the identity of the XVM Structure previously passed to
     *                 {@link #beginIfVisible}
     */
    public void endIfVisible(Constant constId)
        {
        ConditionalConstant condition = pop();

        if (!(condition instanceof PresentCondition
                && constId.equals(((PresentCondition) condition).getPresentConstant())))
            {
            throw new IllegalStateException("expected PresentCondition(\"" + constId + "\"); found: " + condition);
            }
        }

    /**
     * Start a section of the assembly that applies only if the specified version of the specified
     * module is visible (available to be used).
     * <p/>
     * This method is NOT idempotent; each call to "begin" must be matched with a call to the
     * corresponding "end".
     *
     * @param constModule  the identity of the required module
     * @param constVer     the required version of the module
     */
    public void beginIfVersion(ModuleConstant constModule, VersionConstant constVer)
        {
        push(m_pool.ensureImportVersionCondition(constModule, constVer));
        }

    /**
     * End a section of the assembly that applies only if the specified version of the specified
     * module is visible (available to be used). This call must correspond to a previous call to
     * {@link #beginIfVersion}.
     * <p/>
     * This method is NOT idempotent; each call to "begin" must be matched with a call to the
     * corresponding "end".
     *
     * @param constModule  the identity of the module previously passed to {@link #beginIfVersion}
     * @param constVer     the required version of the module
     */
    public void endIfVersion(ModuleConstant constModule, VersionConstant constVer)
        {
        ConditionalConstant condRaw = pop();

        boolean fMatch = false;
        if (condRaw instanceof VersionMatchesCondition cond)
            {
            if (cond.getModuleConstant().equals(constModule) && cond.getVersionConstant().equals(constVer))
                {
                fMatch = true;
                }
            }
        if (!fMatch)
            {
            throw new IllegalStateException("expected VersionMatchesCondition(\"" + constModule
                    + ", " + constVer + "\"); found: " + condRaw);
            }
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The ConstantPool to use.
     */
    private final ConstantPool m_pool;

    /**
     * A "stack" of ConditionalConstants representing nested conditional scopes.
     */
    private final ArrayList<ConditionalConstant> m_listCondition = new ArrayList<>();

    /**
     * A "stack" of modification indicators that allows a call to one of the "end" methods to return
     * the modification indicator to the value that it had before the corresponding "push"
     * invocation.
     */
    private final ArrayList<Long> m_listIndicators = new ArrayList<>();

    /**
     * Corresponding LinkerContexts for each condition; lazily populated.
     */
    private final ArrayList<SimulatedLinkerContext> m_listLinkerContexts = new ArrayList<>();

    /**
     * A cache of various SimulatedLinkerContext objects created, keyed by ConditionalConstant.
     */
    private final Map<ConditionalConstant, SimulatedLinkerContext> m_mapContextCache = new HashMap<>();

    /**
     * Modification counter.
     */
    private long m_cMods;

    /**
     * Modification indicator.
     */
    private long m_nIndicator;
    }