package org.xvm.asm;


import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.xvm.asm.constants.AllCondition;
import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.ConditionalConstant.Influence;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.MultiCondition;
import org.xvm.asm.constants.NamedCondition;
import org.xvm.asm.constants.NotCondition;
import org.xvm.asm.constants.PresentCondition;
import org.xvm.asm.constants.VersionConstant;
import org.xvm.asm.constants.VersionMatchesCondition;
import org.xvm.asm.constants.VersionedCondition;

import org.xvm.util.Handy;


/**
 * This is a compile-time LinkerContext emulator, that is driven off of the information managed by
 * the AssemblerContext -- and only that information! Specifically, it takes the conditions that
 * have (for a given code path) already been tested (specified), and thus are known to be true.
 * <p/>
 * For example, if a Module is marked as version 1 (using a VersionedCondition) and the package
 * under it is conditional on "debug" being defined, then the AssemblerContext at that point in the
 * hierarchy implies a LinkerContext that returns true for {@link #isSpecified(String)
 * isSpecified("debug")} and true for {@link #isVersion(VersionConstant) isVersion(1)} -- and
 * returns true <b><i>only</i></b> for those two conditions!
 */
public class SimulatedLinkerContext
        implements LinkerContext {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a SimulatedLinkerContext using the specified condition.
     *
     * @param cond  a conditional constant, or null (meaning unconditional)
     */
    public SimulatedLinkerContext(ConditionalConstant cond) {
        this.cond = cond;
        if (cond != null) {
            this.influences = cond.terminalInfluences();
            extractRequiredConditions();
        }
    }

    /**
     * Construct a SimulatedLinkerContext using the specified conditions.
     *
     * @param pool   the ConstantPool to place a new constant into
     * @param conds  any number of conditions, which will be treated as if they all need to be met
     */
    public SimulatedLinkerContext(ConstantPool pool, ConditionalConstant... conds) {
        this(toCondition(pool, conds));
    }

    /**
     * Turn an optional array of conditions into a condition.
     *
     * @param pool   the ConstantPool to place a potentially created new constant into
     * @param conds  an array of conditions, or null
     *
     * @return null iff the array is null or zero-length, otherwise a condition representing the
     *         contents of the array
     */
    private static ConditionalConstant toCondition(ConstantPool pool, ConditionalConstant[] conds) {
        if (conds == null || conds.length == 0) {
            return null;
        } else if (conds.length == 1) {
            return conds[0];
        } else {
            return new AllCondition(pool, conds);
        }
    }

    private void extractRequiredConditions() {
        for (Map.Entry<ConditionalConstant, Influence> entry : influences.entrySet()) {
            if (!entry.getValue().isRequired()) {
                continue;
            }

            // exhaustive over the sealed ConditionalConstant tree: adding a condition kind is
            // a compile error at this switch instead of a silently ignored requirement (the
            // pre-sealed instanceof cascade dropped every unlisted kind without a trace)
            switch (entry.getKey()) {
            case NamedCondition cond -> {
                if (names.isEmpty()) {
                    names = new HashSet<>();
                }
                names.add(cond.getName());
            }
            case PresentCondition cond -> {
                if (present.isEmpty()) {
                    present = new HashMap<>();
                }
                present.put(cond.getPresentConstant(), true);
            }
            case VersionMatchesCondition cond -> {
                if (modules.isEmpty()) {
                    modules = new HashMap<>();
                }
                modules.put(cond.getModuleConstant(), cond.getVersionConstant().getVersion());
            }
            case VersionedCondition cond -> {
                assert version == null;
                version = cond.getVersion();
            }
            case NotCondition ignored -> {
                // a negated requirement carries no positive name/presence/version fact to
                // extract; the pre-sealed cascade dropped it silently, this arm documents it
            }
            case MultiCondition ignored -> {
                // composite requirements are decomposed by the influence calculation before
                // they reach this map; likewise a documented no-op instead of a silent one
            }
            }
        }
    }


    // ----- LinkerContext methods -----------------------------------------------------------------

    @Override
    public boolean isSpecified(String sName) {
        return names.contains(sName);
    }

    @Override
    public boolean isPresent(IdentityConstant constId) {
        if (present.isEmpty()) {
            return false;
        }

        Boolean fPresent = present.get(constId);
        if (fPresent != null) {
            return fPresent;
        }

        // evaluate each of the items in the present set that are from the same module, just in case
        // one of them implies the presence of the IdentityConstant being evaluated
        ModuleConstant module = constId.getModuleConstant();
        for (Map.Entry<IdentityConstant, Boolean> entry : present.entrySet()) {
            if (entry.getValue().booleanValue()) {
                IdentityConstant constIdPresent = entry.getKey();
                if (constIdPresent.getModuleConstant().equals(module)) {
                    // walk up the namespace hierarchy, to see if the IdentityConstant being
                    // evaluated is part of this constant's namespace hierarchy
                    IdentityConstant constIdParent = constIdPresent.getNamespace();
                    while (constIdParent != null) {
                        if (constId.equals(constIdParent)) { // TODO fix eventually - what if the parent is a composite?
                            present.put(constId, true);
                            return true;
                        }
                        constIdParent = constIdParent.getNamespace();
                    }
                }
            }
        }

        present.put(constId, false);
        return false;
    }

    @Override
    public boolean isVersionMatch(ModuleConstant constModule, VersionConstant constVer) {
        if (modules.isEmpty()) {
            return false;
        }

        Version ver = modules.get(constModule);
        return ver != null && ver.equals(constVer.getVersion());
    }

    @Override
    public boolean isVersion(VersionConstant constVer) {
        return version != null && version.equals(constVer.getVersion());
    }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode() {
        return 11 * cond.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof SimulatedLinkerContext that
                && (this == that || Handy.equals(this.cond, that.cond));
    }

    @Override
    public String toString() {
        return "SimulatedLinkerContext{" + cond + "}";
    }


    // ----- fields --------------------------------------------------------------------------------

    public static final SimulatedLinkerContext EMPTY = new SimulatedLinkerContext(null);

    private final ConditionalConstant           cond;
    private Map<ConditionalConstant, Influence> influences  = Collections.emptyMap();
    private Set<String>                         names       = Collections.emptySet();
    private Map<IdentityConstant, Boolean>      present     = Collections.emptyMap();
    private Map<ModuleConstant, Version>        modules     = Collections.emptyMap();
    private Version                             version;
}
