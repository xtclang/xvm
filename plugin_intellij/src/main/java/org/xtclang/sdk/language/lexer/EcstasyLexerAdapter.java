package org.xtclang.sdk.language.lexer;


import com.intellij.lexer.LexerBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xtclang.sdk.language.EcstasyLanguage;
import org.xvm.asm.ErrorList;
import org.xvm.asm.ErrorListener;
import org.xvm.compiler.Lexer;
import org.xvm.compiler.Source;
import org.xvm.compiler.Token;


public class EcstasyLexerAdapter extends LexerBase
    {
    private Lexer ecstasyLexer;

    private Source source;

    private Token currentToken;

    private int tokenStart;
    private int tokenEnd;


    private int bufferEnd;

    @Override
    public void start(CharSequence buffer, int startOffset, int endOffset, int initialState)
        {
        // TODO: Do we need to do anything else with `endOffset`?
        bufferEnd = endOffset;
        tokenStart = tokenEnd = startOffset;

        source = new Source(buffer.toString());
        ErrorListener errorListener = new ErrorList(10); // TODO: fill this in with a real value
        ecstasyLexer = new Lexer(source, errorListener);

        // TODO: This startOffset is wrong! The Ecstasy lexer uses a special position format that
        //  encodes lines and line offsets into a long, and `setPosition` takes an arg of that
        //  format. We need to convert startOffset (in characters) to that format.
        ecstasyLexer.setPosition(startOffset);
        currentToken = null; // Just to be explicit
        }

    @Override
    public int getState()
        {
        locateToken();
        return 0; // TODO: I'm not sure exactly what we need here.
        }

    @Override
    public @Nullable IElementType getTokenType()
        {
        locateToken();
        if (currentToken == null)
            {
            return null;
            }
        else
            {
            return new IElementType(currentToken.getId().name(), EcstasyLanguage.INSTANCE);
            }
        }

    @Override
    public int getTokenStart()
        {
        locateToken();
        return tokenStart;
        }

    @Override
    public int getTokenEnd()
        {
        locateToken();
        return tokenEnd;
        }

    @Override
    public void advance()
        {
        locateToken();
        currentToken = null;
        }

    /**
     * Unfortunately the Intellij framework expects us to auto-advance when it calls the get methods
     * above, so we need to add this method that actually does the advancing.
     */
    private void locateToken()
        {
        if (currentToken != null)
            {
            return;
            }

        if (!ecstasyLexer.hasNext())
            {
            currentToken = null;
            }
        else
            {
            // The intellij framework requires that tokens be adjacent, but the ecstasy lexer does
            // not consider whitespace part of tokens. For that reason we need to store the end of
            // the last token and return is as the "start of the new token".
            tokenStart = tokenEnd;
            currentToken = ecstasyLexer.next();
            tokenEnd = source.getMOffset();
            }
        }

    @Override
    public @NotNull CharSequence getBufferSequence()
        {
        return source.toRawString();
        }

    @Override
    public int getBufferEnd()
        {
        return bufferEnd;
        }

    @Override
    public String toString()
        {
        return "FlexAdapter for " + ecstasyLexer.getClass().getName();
        }
    }

