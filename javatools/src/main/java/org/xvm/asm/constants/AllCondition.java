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
public final class AllCondition
        extends MultiCondition {
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
            throws IOException {
        super(pool, format, in);
    }

    /**
     * Construct an AllCondition.
     *
     * @param pool        the ConstantPool that will contain this Constant
     * @param aconstCond  an array of underlying conditions to evaluate
     */
    public AllCondition(ConstantPool pool, ConditionalConstant... aconstCond) {
        super(pool, mergeAnds(aconstCond));
    }

    private AllCondition(ConditionalConstant[] acond) {
        super(acond[0].getConstantPool(), acond);
    }

    private AllCondition(AdoptionContext context, ConditionalConstant[] acond) {
        super(context, acond);
    }


    // ----- ConditionalConstant methods -----------------------------------------------------------

    @Override
    protected AllCondition copyForAdoption(AdoptionContext context) {
        // Adoption must copy only the logical condition graph. The base condition class has a
        // transient brute-force scratch slot, so this family cannot use shallow clone safely.
        return new AllCondition(context, m_aconstCond);
    }

    @Override
    public boolean evaluate(LinkerContext ctx) {
        for (ConditionalConstant constCond : m_aconstCond) {
            if (!constCond.evaluate(ctx)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean testEvaluate(long n) {
        for (ConditionalConstant constCond : m_aconstCond) {
            if (!constCond.testEvaluate(n)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Set<Version> versions() {
        Set<Version> setVers = null;

        for (ConditionalConstant cond : m_aconstCond) {
            Set<Version> setNew = cond.versions();
            if (!setNew.isEmpty()) {
                if (setVers == null) {
                    setVers = setNew;
                } else {
                    throw new IllegalStateException("can't have two version conditions in an AllCondition: "
                            + setVers + ", " + setNew);
                }
            }
        }

        return setVers == null ? Collections.emptySet() : setVers;
    }

    @Override
    public ConditionalConstant addVersion(Version ver) {
        if (versions().contains(ver)) {
            return this;
        }

        // by convention, the version is placed at the end of the list
        ConditionalConstant[] acondOld = m_aconstCond;
        int                   cConds   = acondOld.length;
        ConditionalConstant   condLast = acondOld[cConds-1];
        switch (condLast) {
            case AnyCondition condAny when condAny.isOnlyVersions() -> {
                ConditionalConstant[] acondNew = acondOld.clone();
                acondNew[cConds-1] = condAny.addVersion(ver);
                return new AllCondition(acondNew);
            }
            case VersionedCondition condVer -> {
                ConstantPool          pool     = getConstantPool();
                ConditionalConstant[] acondNew = acondOld.clone();
                acondNew[cConds-1] = new AnyCondition(pool, condVer, pool.ensureVersionedCondition(ver));
                return new AllCondition(acondNew);
            }
            default -> { }
        }

        // this is the first version being added
        assert versions().isEmpty();
        ConditionalConstant[] acondNew = new ConditionalConstant[cConds+1];
        System.arraycopy(acondOld, 0, acondNew, 0, cConds);
        acondNew[cConds] = getConstantPool().ensureVersionedCondition(ver);
        return new AllCondition(acondNew);
    }

    @Override
    public ConditionalConstant removeVersion(Version ver) {
        if (!versions().contains(ver)) {
            return this;
        }

        // by convention, the version is placed at the end of the list
        ConditionalConstant[] acondOld = m_aconstCond;
        int                   cConds   = acondOld.length;
        return switch (acondOld[cConds-1]) {
            case AnyCondition condAny -> {
                assert condAny.versions().contains(ver);
                ConditionalConstant[] acondNew = acondOld.clone();
                acondNew[cConds-1] = condAny.removeVersion(ver);
                yield new AllCondition(acondNew);
            }
            case VersionedCondition condVer -> {
                assert ver.equals(condVer.getVersion());
                yield switch (cConds) {
                    case 0, 1 -> throw new IllegalStateException(
                            "unexpectedly small AllCondition: " + cConds);
                    case 2    -> acondOld[0];
                    default   -> {
                        ConditionalConstant[] acondNew = new ConditionalConstant[cConds-1];
                        System.arraycopy(acondOld, 0, acondNew, 0, cConds-1);
                        yield new AllCondition(acondNew);
                    }
                };
            }
            default -> throw new IllegalStateException("version not found at end of conditions");
        };
    }

    @Override
    public boolean isTerminalInfluenceBruteForce() {
        return !isTerminalInfluenceFinessable(false, new HashSet<>(), new HashSet<>());
    }

    @Override
    protected boolean isTerminalInfluenceFinessable(boolean fInNot,
            Set<ConditionalConstant> setSimple, Set<ConditionalConstant> setComplex) {
        // none of the non-version terminals can be related
        Set<ConditionalConstant> terminals  = terminals();
        int                      cTerminals = terminals.size();
        ConditionalConstant[]    aTerminals = terminals.toArray(new ConditionalConstant[cTerminals]);
        for (int iThis = 0; iThis < cTerminals; ++iThis) {
            ConditionalConstant condThis = aTerminals[iThis];
            if (!(condThis instanceof VersionedCondition)) {
                for (int iThat = iThis + 1; iThat < cTerminals; ++iThat) {
                    ConditionalConstant condThat = aTerminals[iThat];
                    if (!(condThat instanceof VersionedCondition)) {
                        if (condThis.calcRelation(condThat) != Relation.INDEP) {
                            return false;
                        }
                    }
                }
            }
        }

        // each of the AND-ed conditions needs to be finessable as well
        for (Iterator<ConditionalConstant> iter = flatIterator(); iter.hasNext(); ) {
            if (!iter.next().isTerminalInfluenceFinessable(fInNot, setSimple, setComplex)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public Map<ConditionalConstant, Influence> terminalInfluences() {
        if (isTerminalInfluenceBruteForce()) {
            return super.terminalInfluences();
        }

        Map<ConditionalConstant, Influence> influences  = new HashMap<>();
        Set<VersionedCondition>             setVerConds = new HashSet<>();
        Set<Version>                        setVers     = null;
        for (Iterator<ConditionalConstant> iter = flatIterator(); iter.hasNext(); ) {
            // exhaustive over the sealed condition tree: versions are tracked, other terminals
            // contribute AND, NOT contributes its pre-inverted influences, and a nested
            // AllCondition cannot come out of the flattening iterator (the old shape only
            // asserted that, so -da silently mis-processed an impossible kind)
            switch (iter.next()) {
                case VersionedCondition condVer -> {
                    setVers = retainVersions(setVers, condVer);
                    setVerConds.add(condVer);
                }
                case AnyCondition condAny -> {
                    setVers = retainVersions(setVers, condAny);
                    // collect the terminal VersionedConditions
                    for (Iterator<ConditionalConstant> iterVerCond = condAny.flatIterator();
                            iterVerCond.hasNext(); ) {
                        switch (iterVerCond.next()) {
                            case VersionedCondition condNested -> setVerConds.add(condNested);
                            case ConditionalConstant condOther -> throw new IllegalStateException(
                                    "non-version condition under a finessable AnyCondition: "
                                            + condOther.getValueString());
                        }
                    }
                }
                case NotCondition condNot -> {
                    // the influences are already inverted; just add them with an "AND" result
                    for (Map.Entry<ConditionalConstant, Influence> entry
                            : condNot.terminalInfluences().entrySet()) {
                        influences.put(entry.getKey(), entry.getValue().and());
                    }
                }
                case AllCondition condAll -> throw new IllegalStateException(
                        "flattening iterator returned a nested AllCondition: "
                                + condAll.getValueString());
                case NamedCondition condNamed          -> influences.put(condNamed, Influence.AND);
                case PresentCondition condPresent      -> influences.put(condPresent, Influence.AND);
                case VersionMatchesCondition condMatch -> influences.put(condMatch, Influence.AND);
            }
        }

        if (setVers != null) {
            // there were version conditions
            if (setVers.isEmpty()) {
                // the version conditions are impossible to meet; this condition is unsatisfiable
                for (Map.Entry<ConditionalConstant, Influence> entry : influences.entrySet()) {
                    entry.setValue(Influence.ALWAYS_F);
                }
                for (VersionedCondition cond : setVerConds) {
                    influences.put(cond, Influence.ALWAYS_F);
                }
            } else {
                for (VersionedCondition cond : setVerConds) {
                    // three cases: this version is impossible, in which case it should be ALWAYS_F;
                    // this version is the only version, in which case it should be AND; or this
                    // version is one of several versions, in which case it should be CONTRIB
                    Version   ver       = cond.getVersion();
                    Influence influence = Influence.ALWAYS_F;
                    if (setVers.contains(ver)) {
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
    protected String getOperatorString() {
        return "&&";
    }

    @Override
    protected AllCondition instantiate(ConditionalConstant[] aconstCond) {
        return new AllCondition(getConstantPool(), aconstCond);
    }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat() {
        return Format.ConditionAll;
    }


    // ----- helper methods ------------------------------------------------------------------------

    /**
     * Track which versions survive the conditionals seen so far: the first version-bearing
     * condition seeds the set, every later one intersects it.
     */
    private static Set<Version> retainVersions(Set<Version> setVers, ConditionalConstant cond) {
        if (setVers == null) {
            return new HashSet<>(cond.versions());
        }
        setVers.retainAll(cond.versions());
        return setVers;
    }

    /**
     * Merge all of the nested "and" conditions into one bigger array of conditions.
     *
     * @param aconstCond  an array of conditional constants, some of which may be AllConditions
     *
     * @return a potentially larger array of conditional constants, logically equivalent to those
     *         passed in, and of which none should be an AllCondition
     */
    protected static ConditionalConstant[] mergeAnds(ConditionalConstant[] aconstCond) {
        assert aconstCond != null;
        assert aconstCond.length > 1;

        // scan the underlying conditions to see if there is anything to merge
        boolean fAnds   = false;
        int     cConds = 0;
        for (ConditionalConstant cond : aconstCond) {
            if (cond instanceof AllCondition) {
                fAnds   = true;
                cConds += mergeAnds(((AllCondition) cond).m_aconstCond).length;
            } else {
                ++cConds;
            }
        }

        if (!fAnds) {
            // nothing to merge
            return aconstCond;
        }

        // merge the "ands"
        ConditionalConstant[] aconstMerged = new ConditionalConstant[cConds];
        int ofNew = 0;
        for (ConditionalConstant cond : aconstCond) {
            if (cond instanceof AllCondition) {
                ConditionalConstant[] aconstCopy = mergeAnds(((AllCondition) cond).m_aconstCond);
                int cCopy = aconstCopy.length;
                System.arraycopy(aconstCopy, 0, aconstMerged, ofNew, cCopy);
                ofNew += cCopy;
            } else {
                aconstMerged[ofNew++] = cond;
            }
        }
        assert ofNew == cConds;

        return aconstMerged;
    }
}
