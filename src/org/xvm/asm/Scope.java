package org.xvm.asm;


/**
 * Represents a variable scope in the op-code stream.
 */
public class Scope
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct an empty scope.
     */
    public Scope()
        {
        }

    /**
     * Internal: Construct a child scope.
     *
     * @param scopeParent  the parent that contains this child scope
     */
    private Scope(Scope scopeParent)
        {
        assert scopeParent != null;
        m_scopeParent = scopeParent;
        }


    // ----- API for managing scopes and variables -------------------------------------------------

    /**
     * Indicate that a new scope is being entered.
     */
    public void enter()
        {
        if (m_scopeChild == null)
            {
            // create a new scope under this scope
            validate();
            m_scopeChild = new Scope(this);
            }
        else
            {
            m_scopeChild.enter();
            }
        }

    /**
     * Indicate that the current scope is being exited.
     */
    public void exit()
        {
        if (m_scopeChild == null)
            {
            // shut down this scope, but first transfer all of its statistics up to its parent
            validate();

            Scope scopeParent = m_scopeParent;
            if (scopeParent != null)
                {
                // max depth is how many scopes were nested under this
                scopeParent.tallyDepth(m_cMaxDepth);
                scopeParent.tallyVars(m_cMaxVars);

                // now the parent will be the "end of the chain"
                scopeParent.m_scopeChild = null;
                }

            // invalidate this scope
            m_scopeParent = this;
            }
        else
            {
            m_scopeChild.exit();
            }
        }

    /**
     * Check if the specified index points to the next available register.
     *
     * @param nVar  the register index
     *
     * @return true iff the specified index points to the next available register
     *
     * @deprecated remove when the deprecated Op constructors are removed
     */
    public boolean isNextRegister(int nVar)
        {
        int nNext = getCurVars();
        if (nVar < nNext) // this also covers all negative values ("local property" mode)
            {
            return false;
            }
        if (nVar == nNext)
            {
            return true;
            }
        throw new IllegalStateException("Invalid register index");
        }

    /**
     * Allocate a variable (a sequential register number).
     *
     * @return the variable identity
     */
    public int allocVar()
        {
        if (m_scopeChild == null)
            {
            // var is allocated in this scope
            validate();
            int iVar = m_cVars++;
            if (m_cVars > m_cMaxVars)
                {
                m_cMaxVars = m_cVars;
                }
            return iVar;
            }
        else
            {
            return m_cVars + m_scopeChild.allocVar();
            }
        }


    // ----- API for querying statistics -----------------------------------------------------------

    /**
     * Determine the number of scopes, starting with this and including any scopes contained
     * within this scope.
     *
     * @return  the current count of scopes, including this scope
     */
    public int getCurDepth()
        {
        return m_scopeChild == null
                ? 1
                : m_scopeChild.getCurDepth() + 1;
        }

    /**
     * Determine the maximum witnessed number of scopes.
     *
     * @return  the highest number of scopes known to be active at any given point
     */
    public int getMaxDepth()
        {
        return m_scopeChild == null
                ? m_cMaxDepth + 1
                : m_scopeChild.getMaxDepth() + 1;
        }

    /**
     * Determine the number of variables within this scope (including in any scopes within it).
     *
     * @return  the current count of registered variables
     */
    public int getCurVars()
        {
        return m_scopeChild == null
                ? m_cVars
                : m_cVars + m_scopeChild.getCurVars();
        }

    /**
     * Determine the maximum witnessed number of variables within this scope (including in any
     * scopes within it).
     *
     * @return  the highest number of variables known to be active at any given point
     */
    public int getMaxVars()
        {
        return m_scopeChild == null
                ? m_cMaxVars
                : Math.max(m_cMaxVars, this.m_cVars + m_scopeChild.getMaxVars());
        }


    // ----- internal ------------------------------------------------------------------------------

    private void validate()
        {
        if (m_scopeParent == this)
            {
            throw new IllegalStateException("Scope cannot be re-entered after exit");
            }
        }

    private void tallyDepth(int cNested)
        {
        // cMaxDepth is the max from the point of view of this scope
        // cNested is the max from the point of view of the child scope (i.e. one less)
        if (cNested >= m_cMaxDepth)
            {
            m_cMaxDepth = cNested + 1;
            }
        }

    private void tallyVars(int cNested)
        {
        int cTotal = m_cVars + cNested;
        if (cTotal > m_cMaxVars)
            {
            m_cMaxVars = cTotal;
            }
        }


    // ----- data members --------------------------------------------------------------------------

    /**
     * The parent that created this scope. On destruction, set to itself (parent==this) to
     * invalidate.
     */
    private Scope m_scopeParent;

    /**
     * The current child scope.
     */
    private Scope m_scopeChild;

    /**
     * The number of variables allocated within <b>this</b> scope (and <b>not</b> within any
     * child scopes).
     */
    private int   m_cVars;

    /**
     * Tracks the max number of variables that this scope is aware of existing at any one time,
     * including variables that exist within child scopes.
     */
    private int   m_cMaxVars;

    /**
     * Tracks the max depth of scopes nested within this scope. (Does not count this scope.)
     */
    private int   m_cMaxDepth;
    }


