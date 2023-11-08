package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.Collections;
import java.util.Map;

import org.xvm.asm.Version;

import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;


/**
 * A version override specifies allowable, avoided, and preferred version information.
 */
public class VersionOverride
        extends AstNode
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a default version requirement.
     *
     * @param exprVer  denotes the version required
     */
    public VersionOverride(LiteralExpression exprVer)
        {
        this(null, exprVer);
        }

    /**
     * Construct a version override.
     *
     * @param verb     the overriding verb ("allow", "avoid", or "prefer")
     * @param exprVer  denotes the version associated with the verb
     */
    public VersionOverride(Token verb, LiteralExpression exprVer)
        {
        assert exprVer != null && exprVer.literal.getId() == Id.LIT_VERSION;
        this.verb    = verb;
        this.exprVer = exprVer;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the version number
     */
    public Version getVersion()
        {
        return exprVer.getVersion();
        }

    /**
     * @return true iff the version is allowed or preferred, or false if the version is avoided
     */
    public boolean isAllowed()
        {
        return verb == null || verb.getId() != Token.Id.AVOID;
        }

    /**
     * @return true iff the version is a preferred version
     */
    public boolean isPreferred()
        {
        return verb != null && verb.getId() == Token.Id.PREFER;
        }

    @Override
    public long getStartPosition()
        {
        return verb == null
                ? exprVer.getStartPosition()
                : verb.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return exprVer.getEndPosition();
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
        return verb == null ? exprVer.toString() : (verb.getId().TEXT + ' ' + exprVer.toString());
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }

    @Override
    public Map<String, Object> getDumpChildren()
        {
        return Collections.emptyMap();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * A token representing "allow", "avoid", or "prefer".
     *
     * Note: may be null.
     */
    protected Token verb;

    /**
     * The version literal expression.
     */
    protected LiteralExpression exprVer;

    private static final Field[] CHILD_FIELDS = fieldsForNames(VersionOverride.class, "exprVer");
    }
