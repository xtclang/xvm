package org.xvm.compiler.ast;


import org.xvm.asm.Version;

import org.xvm.compiler.Token;

import java.lang.reflect.Field;

import java.util.Collections;
import java.util.Map;


/**
 * A version override specifies allowable, avoided, and preferred version information.
 *
 * @author cp 2017.04.03
 */
public class VersionOverride
        extends AstNode
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a default version requirement.
     *
     * @param version  the version required
     */
    public VersionOverride(Version version)
        {
        this(null, version);
        }

    /**
     * Construct a version override.
     *
     * @param verb     the overriding verb ("allow", "avoid", or "prefer")
     * @param version  the version associated with the verb
     */
    public VersionOverride(Token verb, Version version)
        {
        this.verb     = verb;
        this.version = version;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        return verb == null ? version.toString() : (verb.getId().TEXT + ' ' + version);
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }

    @Override
    public Map<String, Object> getDumpChildren()
        {
        return Collections.EMPTY_MAP;
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * A token representing "allow", "avoid", or "prefer".
     *
     * Note: may be null.
     */
    protected Token verb;

    /**
     * The version id.
     */
    protected Version version;

    private static final Field[] CHILD_FIELDS = fieldsForNames(VersionOverride.class, "version");
    }
