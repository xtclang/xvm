package org.xvm.compiler.ast;


import org.xvm.asm.Version;

import org.xvm.compiler.Token;


/**
 * A version literal.
 *
 * @author cp 2017.06.20
 */
public class VersionExpression
        extends LiteralExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public VersionExpression(Token literal, Version ver, long lStartPos)
        {
        super(literal);
        this.ver       = ver;
        this.lStartPos = lStartPos;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the version value
     */
    public Version getVersion()
        {
        return ver;
        }

    @Override
    public long getStartPosition()
        {
        return lStartPos;
        }

    @Override
    public long getEndPosition()
        {
        return literal.getEndPosition();
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        return "v:\"" + ver.toString() + '\"';
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Version ver;
    protected long    lStartPos;
    }
