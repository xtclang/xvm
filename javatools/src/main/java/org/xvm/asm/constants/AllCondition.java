package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.IOException;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.LinkerContext;
import org.xvm.asm.Version;


/**
 * Implements the logical "and" of any number of conditions.
 */
public class AllCondition
        extends MultiCondition
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Constructor used for deserialization.
     *
     * @param pool    the ConstantPool that will contain this Constant
     * @param format  the format of the Constant in the stream
     * @param in      the DataInput stream to read the Constant value from
     *
     * @throws IOException  if an issue occurs reading the Constant value
     */
    public AllCondition(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool, format, in);
        }

    /**
     * Construct an AllCondition.
     *
     * @param pool        the ConstantPool that will contain this Constant
     * @param aconstCond  an array of underlying conditions to evaluate
     */
    public AllCondition(ConstantPool pool, ConditionalConstant... aconstCond)
        {
        super(pool, mergeAnds(aconstCond));
        }

    private AllCondition(ConditionalConstant[] acond)
        {
        super(acond[0].getConstantPool(), acond);
        }


    // ----- ConditionalConstant methods -----------------------------------------------------------

    @Override
    public boolean evaluate(LinkerContext ctx)
        {
        for (ConditionalConstant constCond : m_aconstCond)
            {
            if (!constCond.evaluate(ctx))
                {
                return false;
                }
            }
        return true;
        }

    @Override
    public boolean testEvaluate(long n)
        {
        for (ConditionalConstant constCond : m_aconstCond)
            {
            if (!constCond.testEvaluate(n))
                {
                return false;
                }
            }
        return true;
        }

    @Override
    public Set<Version> versions()
        {
        Set<Version> setVers = null;

        for (ConditionalConstant cond : m_aconstCond)
            {
            Set<Version> setNew = cond.versions();
            if (!setNew.isEmpty())
                {
                if (setVers == null)
                    {
                    setVers = setNew;
                    }
                else
                    {
                    throw new IllegalStateException("can't have two version conditions in an AllCondition: "
                            + setVers + ", " + setNew);
                    }
                }
            }

        return setVers == null ? Collections.EMPTY_SET : setVers;
        }

    @Override
    public ConditionalConstant addVersion(Version ver)
        {
        if (versions().contains(ver))
            {
            return this;
            }

        // by convention, the version is placed at the end of the list
        ConditionalConstant[] acondOld = m_aconstCond;
        int                   cConds   = acondOld.length;
        ConditionalConstant   condLast = acondOld[cConds-1];
        if (condLast instanceof AnyCondition condAny)
            {
            if (condAny.isOnlyVersions())
                {
                ConditionalConstant[] acondNew = acondOld.clone();
                acondNew[cConds-1] = condAny.addVersion(ver);
                return new AllCondition(acondNew);
                }
            }
        else if (condLast instanceof VersionedCondition)
            {
            ConstantPool          pool     = getConstantPool();
            ConditionalConstant[] acondNew = acondOld.clone();
            acondNew[cConds-1] = new AnyCondition(pool, condLast, pool.ensureVersionedCondition(ver));
            return new AllCondition(acondNew);
            }

        // this is the first version being added
        assert versions().isEmpty();
        ConditionalConstant[] acondNew = new ConditionalConstant[cConds+1];
        System.arraycopy(acondOld, 0, acondNew, 0, cConds);
        acondNew[cConds] = getConstantPool().ensureVersionedCondition(ver);
        return new AllCondition(acondNew);
        }

    @Override
    public ConditionalConstant removeVersion(Version ver)
        {
        if (!versions().contains(ver))
            {
            return this;
            }

        // by convention, the version is placed at the end of the list
        ConditionalConstant[] acondOld = m_aconstCond;
        int                   cConds   = acondOld.length;
        ConditionalConstant   condLast = acondOld[cConds-1];
        if (condLast instanceof AnyCondition)
            {
            assert condLast.versions().contains(ver);
            ConditionalConstant[] acondNew = acondOld.clone();
            acondNew[cConds-1] = condLast.removeVersion(ver);
            return new AllCondition(acondNew);
            }
        else if (condLast instanceof VersionedCondition)
            {
            assert ver.equals(((VersionedCondition) condLast).getVersion());
            switch (cConds)
                {
                case 0:
                case 1:
                    throw new IllegalStateException("unexpectedly small AllCondition: " + cConds);

                case 2:
                    return acondOld[0];

                default:
                    ConditionalConstant[] acondNew = new ConditionalConstant[cConds-1];
                    System.arraycopy(acondOld, 0, acondNew, 0, cConds-1);
                    return new AllCondition(acondNew);
                }
            }
        else
            {
            throw new IllegalStateException("version not found at end of conditions");
            }
        }

    @Override
    public boolean isTerminalInfluenceBruteForce()
        {
        return !isTerminalInfluenceFinessable(false, new HashSet<>(), new HashSet<>());
        }

    @Override
    protected boolean isTerminalInfluenceFinessable(boolean fInNot,
            Set<ConditionalConstant> setSimple, Set<ConditionalConstant> setComplex)
        {
        // none of the non-version terminals can be related
        Set<ConditionalConstant> terminals  = terminals();
        int                      cTerminals = terminals.size();
        ConditionalConstant[]    aTerminals = terminals.toArray(new ConditionalConstant[cTerminals]);
        for (int iThis = 0; iThis < cTerminals; ++iThis)
            {
            ConditionalConstant condThis = aTerminals[iThis];
            if (!(condThis instanceof VersionedCondition))
                {
                for (int iThat = iThis + 1; iThat < cTerminals; ++iThat)
                    {
                    ConditionalConstant condThat = aTerminals[iThat];
                    if (!(condThat instanceof VersionedCondition))
                        {
                        if (condThis.calcRelation(condThat) != Relation.INDEP)
                            {
                            return false;
                            }
                        }
                    }
                }
            }

        // each of the AND-ed conditions needs to be finessable as well
        for (Iterator<ConditionalConstant> iter = flatIterator(); iter.hasNext(); )
            {
            if (!iter.next().isTerminalInfluenceFinessable(fInNot, setSimple, setComplex))
                {
                return false;
                }
            }

        return true;
        }

    @Override
    public Map<ConditionalConstant, Influence> terminalInfluences()
        {
        if (isTerminalInfluenceBruteForce())
            {
            return super.terminalInfluences();
            }

        Map<ConditionalConstant, Influence> influences  = new HashMap<>();
        Set<VersionedCondition>             setVerConds = new HashSet<>();
        Set<Version>                        setVers     = null;
        for (Iterator<ConditionalConstant> iter = flatIterator(); iter.hasNext(); )
            {
            ConditionalConstant cond = iter.next();
            if (cond instanceof VersionedCondition || cond instanceof AnyCondition)
                {
                // keep track of what versions survive the conditional(s)
                if (setVers == null)
                    {
                    setVers = new HashSet<>(cond.versions());
                    }
                else
                    {
                    setVers.retainAll(cond.versions());
                    }

                // collect the terminal VersionedConditions
                if (cond instanceof VersionedCondition)
                    {
                    setVerConds.add((VersionedCondition) cond);
                    }
                else
                    {
                    for (Iterator<ConditionalConstant> iterVerCond = ((AnyCondition) cond).flatIterator();
                            iterVerCond.hasNext(); )
                        {
                        setVerConds.add((VersionedCondition) iterVerCond.next());
                        }
                    }
                }
            else if (cond.isTerminal())
                {
                influences.put(cond, Influence.AND);
                }
            else
                {
                // we've already handled the only allowable possibility of "OR" (the versions), and
                // the possibility of "AND" (via flattening the iterator), and the terminals, so the
                // only thing left should be "NOT"
                assert cond instanceof NotCondition;

                // the influences are already inverted; just add them with an "AND" result
                for (Map.Entry<ConditionalConstant, Influence> entry : cond.terminalInfluences().entrySet())
                    {
                    influences.put(entry.getKey(), entry.getValue().and());
                    }
                }
            }

        if (setVers != null)
            {
            // there were version conditions
            if (setVers.isEmpty())
                {
                // the version conditions are impossible to meet; this condition is unsatisfiable
                for (Map.Entry<ConditionalConstant, Influence> entry : influences.entrySet())
                    {
                    entry.setValue(Influence.ALWAYS_F);
                    }
                for (VersionedCondition cond : setVerConds)
                    {
                    influences.put(cond, Influence.ALWAYS_F);
                    }
                }
            else
                {
                for (VersionedCondition cond : setVerConds)
                    {
                    // three cases: this version is impossible, in which case it should be ALWAYS_F;
                    // this version is the only version, in which case it should be AND; or this
                    // version is one of several versions, in which case it should be CONTRIB
                    Version   ver       = cond.getVersion();
                    Influence influence = Influence.ALWAYS_F;
                    if (setVers.contains(ver))
                        {
                        influence = setVers.size() == 1
                                ? Influence.AND
                                : Influence.CONTRIB;
                        }
                    influences.put(cond, influence);
                    }
                }
            }

        return influences;
        }

    @Override
    protected String getOperatorString()
        {
        return "&&";
        }

    @Override
    protected AllCondition instantiate(ConditionalConstant[] aconstCond)
        {
        return new AllCondition(getConstantPool(), aconstCond);
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.ConditionAll;
        }


    // ----- helper methods ------------------------------------------------------------------------

    /**
     * Merge all of the nested "and" conditions into one bigger array of conditions.
     *
     * @param aconstCond  an array of conditional constants, some of which may be AllConditions
     *
     * @return a potentially larger array of conditional constants, logically equivalent to those
     *         passed in, and of which none should be an AllCondition
     */
    protected static ConditionalConstant[] mergeAnds(ConditionalConstant[] aconstCond)
        {
        assert aconstCond != null;
        assert aconstCond.length > 1;

        // scan the underlying conditions to see if there is anything to merge
        boolean fAnds   = false;
        int     cConds = 0;
        for (ConditionalConstant cond : aconstCond)
            {
            if (cond instanceof AllCondition)
                {
                fAnds   = true;
                cConds += mergeAnds(((AllCondition) cond).m_aconstCond).length;
                }
            else
                {
                ++cConds;
                }
            }

        if (!fAnds)
            {
            // nothing to merge
            return aconstCond;
            }

        // merge the "ands"
        ConditionalConstant[] aconstMerged = new ConditionalConstant[cConds];
        int ofNew = 0;
        for (ConditionalConstant cond : aconstCond)
            {
            if (cond instanceof AllCondition)
                {
                ConditionalConstant[] aconstCopy = mergeAnds(((AllCondition) cond).m_aconstCond);
                int cCopy = aconstCopy.length;
                System.arraycopy(aconstCopy, 0, aconstMerged, ofNew, cCopy);
                ofNew += cCopy;
                }
            else
                {
                aconstMerged[ofNew++] = cond;
                }
            }
        assert ofNew == cConds;

        return aconstMerged;
        }
    }