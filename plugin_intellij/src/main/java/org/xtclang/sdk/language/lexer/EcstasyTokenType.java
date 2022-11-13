package org.xtclang.sdk.language.lexer;


import com.intellij.psi.tree.IElementType;
import org.xtclang.sdk.language.EcstasyLanguage;
import org.xvm.compiler.Token;


public class EcstasyTokenType extends IElementType
    {
    private final Token ecstasyToken;

    public EcstasyTokenType(Token ecstasyToken)
        {
        // TODO: Not sure if `getValueText` is the right thing to use as the `debugName` param.
        super(ecstasyToken.getValueText(), EcstasyLanguage.INSTANCE);
        this.ecstasyToken = ecstasyToken;
        }

    public Token getEcstasyToken()
        {
        return ecstasyToken;
        }

    @Override
    public String toString()
        {
        return "EcstasyTokenAdapter." + super.toString();
        }
    }
