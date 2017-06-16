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
    public VersionOverride(Version version, long lStartPos, long lEndPos)
        {
        this(null, version, lStartPos, lEndPos);
        }

    /**
     * Construct a version override.
     *
     * @param verb     the overriding verb ("allow", "avoid", or "prefer")
     * @param version  the version associated with the verb
     */
    public VersionOverride(Token verb, Version version, long lStartPos, long lEndPos)
        {
        this.verb      = verb;
        this.version   = version;
        this.lStartPos = lStartPos;
        this.lEndPos   = lEndPos;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the version number
     */
    public Version getVersion()
        {
        return version;
        }

    /**
     * @return true iff the version is allowed or preferred, or false if the version is avoided
     */
    public boolean isAllowed()
        {
        return verb.getId() != Token.Id.AVOID;
        }

    /**
     * @return true iff the version is a preferred version
     */
    public boolean isPreferred()
        {
        return verb.getId() == Token.Id.PREFER;
        }

    @Override
    public long getStartPosition()
        {
        return lStartPos;
        }

    @Override
    public long getEndPosition()
        {
        return lEndPos;
        }

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

    protected long lStartPos;
    protected long lEndPos;

    private static final Field[] CHILD_FIELDS = fieldsForNames(VersionOverride.class, "version");
    }
