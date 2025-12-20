package org.xvm.compiler.ast;


import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import org.xvm.asm.Version;

import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;


/**
 * A version override specifies allowable, avoided, and preferred version information.
 */
public class VersionOverride
        extends AstNode {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a default version requirement.
     *
     * @param exprVer  denotes the version required
     */
    public VersionOverride(LiteralExpression exprVer) {
        this(null, exprVer);
    }

    /**
     * Construct a version override.
     *
     * @param verb     the overriding verb ("allow", "avoid", or "prefer")
     * @param exprVer  denotes the version associated with the verb
     */
    public VersionOverride(@Nullable Token verb, @NotNull LiteralExpression exprVer) {
        assert exprVer.literal.getId() == Id.LIT_VERSION;
        this.verb    = verb;
        this.exprVer = Objects.requireNonNull(exprVer);
    }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the version number
     */
    public Version getVersion() {
        return exprVer.getVersion();
    }

    /**
     * @return true iff the version is allowed or preferred, or false if the version is avoided
     */
    public boolean isAllowed() {
        return verb == null || verb.getId() != Token.Id.AVOID;
    }

    /**
     * @return true iff the version is a preferred version
     */
    public boolean isPreferred() {
        return verb != null && verb.getId() == Token.Id.PREFER;
    }

    @Override
    public long getStartPosition() {
        return verb == null
                ? exprVer.getStartPosition()
                : verb.getStartPosition();
    }

    @Override
    public long getEndPosition() {
        return exprVer.getEndPosition();
    }

    @Override
    public <T> T forEachChild(Function<AstNode, T> visitor) {
        return visitor.apply(exprVer);
    }

    @Override
    protected AstNode withChildren(List<AstNode> children) {
        LiteralExpression newExprVer = (LiteralExpression) children.get(0);
        return new VersionOverride(verb, newExprVer);
    }

    @Override
    protected void replaceChild(AstNode oldChild, AstNode newChild) {
        if (tryReplace(oldChild, newChild, exprVer, n -> exprVer = n)) {
            return;
        }
        throw new IllegalStateException("no such child \"" + oldChild + "\" on \"" + this + '\"');
    }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString() {
        return verb == null ? exprVer.toString() : (verb.getId().TEXT + ' ' + exprVer.toString());
    }

    @Override
    public String getDumpDesc() {
        return toString();
    }

    @Override
    public Map<String, Object> getDumpChildren() {
        return Collections.emptyMap();
    }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * A token representing "allow", "avoid", or "prefer".
     */
    protected @Nullable Token verb;

    /**
     * The version literal expression.
     */
    protected @NotNull LiteralExpression exprVer;
 }
