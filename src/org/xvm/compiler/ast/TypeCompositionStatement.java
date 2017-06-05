package org.xvm.compiler.ast;


import org.xvm.asm.Component;
import org.xvm.asm.Component.Format;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.FileStructure;
import org.xvm.asm.ModuleStructure;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.CompilerException;
import org.xvm.compiler.ErrorListener;
import org.xvm.compiler.Source;
import org.xvm.compiler.Token;

import org.xvm.util.Severity;

import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.List;

import static org.xvm.compiler.Lexer.CR;
import static org.xvm.compiler.Lexer.LF;
import static org.xvm.compiler.Lexer.isLineTerminator;
import static org.xvm.compiler.Lexer.isValidQualifiedModule;
import static org.xvm.compiler.Lexer.isWhitespace;
import static org.xvm.util.Handy.appendString;
import static org.xvm.util.Handy.indentLines;


/**
 * A type declaration.
 *
 * @author cp 2017.03.28
 */
public class TypeCompositionStatement
        extends ComponentStatement
    {
    // ----- constructors --------------------------------------------------------------------------

    public TypeCompositionStatement(Source            source,
                                    long              lStartPos,
                                    long              lEndPos,
                                    Expression        condition,
                                    List<Token>       modifiers,
                                    List<Annotation>  annotations,
                                    Token             category,
                                    Token             name,
                                    List<Token>       qualified,
                                    List<Parameter>   typeParams,
                                    List<Parameter>   constructorParams,
                                    List<Composition> composition,
                                    StatementBlock    body,
                                    Token             doc)
        {
        super(lStartPos, lEndPos);

        this.source            = source;
        this.condition         = condition;
        this.modifiers         = modifiers;
        this.annotations       = annotations;
        this.category          = category;
        this.name              = name;
        this.qualified         = qualified;
        this.typeParams        = typeParams;               
        this.constructorParams = constructorParams;        
        this.composition       = composition;              
        this.body              = body;
        this.doc               = doc;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public Access getDefaultAccess()
        {
        Access access = getAccess(modifiers);
        return access == null
                ? super.getDefaultAccess()
                : access;
        }

    public Token getCategory()
        {
        return category;
        }

    public String getName()
        {
        if (category.getId() == Token.Id.MODULE)
            {
            StringBuilder sb = new StringBuilder();
            for (Token suffix : qualified)
                {
                sb.append('.')
                  .append(suffix.getValue());
                }
            return sb.substring(1).toString();
            }
        else
            {
            return (String) name.getValue();
            }
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- compile phases ------------------------------------------------------------------------

    /**
     * Add an enclosed type composition to this type composition. Because the parser may have to
     * wrap the parsed type composition into a statement block, this method takes a Statement
     * instead of a TypeCompositionStatement, but the idea is the same: the argument to this method
     * should be an object that was returned from {@link org.xvm.compiler.Parser#parseSource()}.
     * <p/>
     * This method is used to combine multiple files that were parsed independently into a single
     * parse tree -- a single "AST" for an entire module.
     *
     * @param stmt  a statement returned from {@link org.xvm.compiler.Parser#parseSource()}
     */
    public void addEnclosed(Statement stmt)
        {
        if (enclosed == null)
            {
            if (body == null)
                {
                body = new StatementBlock(new ArrayList<>());
                }

            enclosed = new StatementBlock(new ArrayList<>());
            body.addStatement(enclosed);
            }

        enclosed.addStatement(stmt);
        }

    /**
     * Instantiate and populate the initial FileStructure for this module.
     *
     * @return  a new FileStructure for this module, with the module, packages, and classes
     *          registered
     */
    public FileStructure createModuleStructure(ErrorListener errs)
        {
        assert category.getId() == Token.Id.MODULE;     // it has to be a module!
        assert condition == null;                       // module cannot be conditional
        assert getComponent() == null;                  // it can't already have been created!

        // validate the module name
        String sName = getName();
        if (!isValidQualifiedModule(sName))
            {
            log(errs, Severity.ERROR, Compiler.MODULE_BAD_NAME, sName);
            throw new CompilerException("unable to create module with illegal name: " + sName);
            }

        // create the FileStructure and "this" ModuleStructure
        FileStructure struct = new FileStructure(getName());
        ModuleStructure module = struct.getModule();
        setStructure(module);

        // validate modifiers
        if (modifiers != null && !modifiers.isEmpty())
            {
            boolean fFoundPublic = false;
            for (int i = 0, c = modifiers.size(); i < c; ++i)
                {
                Token token = modifiers.get(i);
                switch (token.getId())
                    {
                    case PUBLIC:
                        if (fFoundPublic)
                            {
                            errs.log(Severity.ERROR, Compiler.DUPLICATE_MODIFIER, new Object[] {token.getId().TEXT},
                                    source, token.getStartPosition(), token.getEndPosition());
                            }
                        else
                            {
                            fFoundPublic = true;
                            }
                        break;

                    case PROTECTED:
                    case PRIVATE:
                    case STATIC:
                        log(errs, Severity.ERROR, Compiler.ILLEGAL_MODIFIER, token.getId().TEXT);
                        break;

                    default:
                        throw new IllegalStateException("token=" + token);
                    }
                }
            }

        // type parameters are not permitted
        disallowTypeParams(errs);

        // constructor parameters are not permitted unless they have default values
        requireConstructorParamValues(errs);

        // validate composition
        boolean fAlreadyExtends = false;
        for (Composition composition : this.composition)
            {
            switch (composition.getKeyword().getId())
                {
                case EXTENDS:
                    // only one extends is allowed
                    if (fAlreadyExtends)
                        {
                        Token token = composition.getKeyword();
                        errs.log(Severity.ERROR, Compiler.MULTIPLE_EXTENDS, new Object[] {composition},
                                source, token.getStartPosition(), token.getEndPosition());
                        }
                    else
                        {
                        fAlreadyExtends = true;
                        }
                    break;

                case DELEGATES:
                case IMPLEMENTS:
                case INCORPORATES:
                    // these are all OK; other checks will be done after the types are resolved
                    break;

                case IMPORT:
                case IMPORT_EMBED:
                case IMPORT_REQ:
                case IMPORT_WANT:
                case IMPORT_OPT:
                case INTO:
                    // "import" composition not allowed for modules (only used by packages)
                    // "into" not allowed (only used by traits & mixins)
                    Token token = composition.getKeyword();
                    errs.log(Severity.ERROR, Compiler.KEYWORD_UNEXPECTED, new Object[] {composition},
                            source, token.getStartPosition(), token.getEndPosition());
                    break;
                }
            }

        if (doc != null)
            {
            module.setDocumentation(extractDocumentation(doc));
            }

        super.registerStructures(null, errs);

        // TODO validate any constructor parameters and their default values, and transfer the info to the constructor

        return struct;
        }

    @Override
    protected void registerStructures(AstNode parent, ErrorListener errs)
        {
        Component componentParent = parent.getComponent();
        if (componentParent != null)
            {
            // create the structure for this package or class (etc.)
            assert getComponent() == null;

            String    sName     = (String) name.getValue();
            Access    access    = getDefaultAccess();
            Component container = parent.getComponent();
            switch (category.getId())
                {
                case MODULE:
                    errs.log(Severity.ERROR, Compiler.MODULE_UNEXPECTED, null,
                            getSource(), category.getStartPosition(), category.getEndPosition());
                    break;

                case PACKAGE:
                    if (container.isPackageContainer())
                        {
                        // the check for duplicates is deferred, since it is possible (thanks to
                        // the complexity of conditionals) to have multiple components occupying
                        // the same location within the namespace at this point in the compilation
                        setStructure(container.createPackage(access, sName));
                        }
                    else
                        {
                        errs.log(Severity.ERROR, Compiler.PACKAGE_UNEXPECTED,
                                new String[] {container.toString()},
                                getSource(), category.getStartPosition(), category.getEndPosition());
                        }
                    break;

                case CLASS:
                case INTERFACE:
                case SERVICE:
                case CONST:
                case ENUM:
                case TRAIT:
                case MIXIN:
                    if (container.isClassContainer())
                        {
                        Format format;
                        switch (category.getId())
                            {
                            case CLASS:
                                format = Format.CLASS;
                                break;

                            case INTERFACE:
                                format = Format.INTERFACE;
                                break;

                            case SERVICE:
                                format = Format.SERVICE;
                                break;

                            case CONST:
                                format = Format.CONST;
                                break;

                            case ENUM:
                                format = Format.ENUM;
                                break;

                            case TRAIT:
                                format = Format.TRAIT;
                                break;

                            case MIXIN:
                                format = Format.MIXIN;
                                break;

                            default:
                                throw new IllegalStateException();
                            }

                        setStructure(container.createClass(getDefaultAccess(), format, sName));
                        }
                    else
                        {
                        errs.log(Severity.ERROR, Compiler.CLASS_UNEXPECTED,
                                new String[] {container.toString()},
                                getSource(), category.getStartPosition(), category.getEndPosition());
                        }
                    break;

                default:
                    throw new UnsupportedOperationException("unable to guess structure for: "
                            + category.getId().TEXT);
                }
            }

        super.registerStructures(parent, errs);
        }

    private void disallowTypeParams(ErrorListener errs)
        {
        // type parameters are not permitted
        if (typeParams != null && !typeParams.isEmpty())
            {
            // note: currently no way to determine the location of the parameters
            // Parameter paramFirst = typeParams.get(0);
            // Parameter paramLast  = typeParams.get(typeParams.size() - 1);

            Token tokFirst = category == null ? name : category;
            Token tokLast  = name == null ? category : name;
            errs.log(Severity.ERROR, Compiler.TYPE_PARAMS_UNEXPECTED, null,
                    getSource(), tokFirst.getStartPosition(), tokLast.getEndPosition());
            }
        }

    private void disallowConstructorParams(ErrorListener errs)
        {
        // constructor parameters are not permitted
        if (constructorParams != null && !constructorParams.isEmpty())
            {
            // note: currently no way to determine the location of the parameters
            // Parameter paramFirst = constructorParams.get(0);
            // Parameter paramLast  = constructorParams.get(constructorParams.size() - 1);

            Token tokFirst = category == null ? name : category;
            Token tokLast  = name == null ? category : name;
            errs.log(Severity.ERROR, Compiler.CONSTRUCTOR_PARAMS_UNEXPECTED, null,
                    getSource(), tokFirst.getStartPosition(), tokLast.getEndPosition());
            }
        }

    private void requireConstructorParamValues(ErrorListener errs)
        {
        // constructor parameters are not permitted
        if (constructorParams != null && !constructorParams.isEmpty())
            {
            for (Parameter param : constructorParams)
                {
                if (param.value == null)
                    {
                    // note: currently no way to determine the location of the parameter
                    Token tokFirst = category == null ? name : category;
                    Token tokLast  = name == null ? category : name;
                    errs.log(Severity.ERROR, Compiler.CONSTRUCTOR_PARAM_DEFAULT_REQUIRED, null,
                            getSource(), tokFirst.getStartPosition(), tokLast.getEndPosition());
                    }
                }
            }
        }

    /**
     * Parse a documentation comment, extracting the "body" of the documentation inside it.
     *
     * @param token  a documentation token
     *
     * @return the "body" of the documentation, as LF-delimited lines, without the leading "* "
     */
    public static String extractDocumentation(Token token)
        {
        if (token == null)
            {
            return null;
            }

        String sDoc = (String) token.getValue();
        if (sDoc == null || sDoc.length() <= 1 || sDoc.charAt(0) != '*')
            {
            return null;
            }

        StringBuilder sb = new StringBuilder();
        int nState = 0;
        NextChar: for (char ch : sDoc.substring(1).toCharArray())
            {
            switch (nState)
                {
                case 0:         // leading whitespace expected
                    if (!isLineTerminator(ch))
                        {
                        if (isWhitespace(ch))
                            {
                            continue NextChar;
                            }

                        if (ch == '*')
                            {
                            nState = 1;
                            continue NextChar;
                            }

                        // weird - it's actual text to append; we didn't find the leading '*'
                        break;
                        }
                    // fall through

                case 1:         // ate the asterisk; expecting one space
                    if (!isLineTerminator(ch))
                        {
                        if (isWhitespace(ch))
                            {
                            nState = 2;
                            continue NextChar;
                            }

                        // weird - it's actual text to append; there was no ' ' after the '*'
                        break;
                        }
                    // fall through

                case 2:         // in the text
                    if (isLineTerminator(ch))
                        {
                        if (sb.length() > 0)
                            {
                            sb.append(LF);
                            }
                        nState = ch == CR ? 3 : 0;
                        continue NextChar;
                        }
                    break;

                case 3:         // ate a CR, emitted an LF
                    if (ch == LF || isWhitespace(ch))
                        {
                        nState = 0;
                        continue NextChar;
                        }

                    if (ch == '*')
                        {
                        nState = 1;
                        continue NextChar;
                        }

                    // weird - it's actual text to append; we didn't find the leading '*'
                    break;
                }

            nState = 2;
            sb.append(ch);
            }

        // trim any trailing whitespace & line terminators
        int cch = sb.length();
        while (isWhitespace(sb.charAt(--cch)))
            {
            sb.setLength(cch);
            }

        return sb.toString();
        }


    // ----- debugging assistance ------------------------------------------------------------------

    public String toSignatureString()
        {
        StringBuilder sb = new StringBuilder();

        if (modifiers != null)
            {
            for (Token token : modifiers)
                {
                sb.append(token.getId().TEXT)
                  .append(' ');
                }
            }

        if (annotations != null)
            {
            for (Annotation annotation : annotations)
                {
                sb.append(annotation)
                  .append(' ');
                }
            }

        sb.append(category.getId().TEXT)
          .append(' ');

        if (qualified == null)
            {
            sb.append(name.getValue());
            }
        else
            {
            boolean first = true;
            for (Token token : qualified)
                {
                if (first)
                    {
                    first = false;
                    }
                else
                    {
                    sb.append('.');
                    }
                sb.append(token.getValue());
                }
            }

        if (typeParams != null)
            {
            sb.append('<');
            boolean first = true;
            for (Parameter param : typeParams)
                {
                if (first)
                    {
                    first = false;
                    }
                else
                    {
                    sb.append(", ");
                    }
                sb.append(param.toTypeParamString());
                }
            sb.append('>');
            }

        if (constructorParams != null)
            {
            sb.append('(');
            boolean first = true;
            for (Parameter param : constructorParams)
                {
                if (first)
                    {
                    first = false;
                    }
                else
                    {
                    sb.append(", ");
                    }
                sb.append(param);
                }
            sb.append(')');
            }

        return sb.toString();
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        if (doc != null)
            {
            String sDoc = String.valueOf(doc.getValue());
            if (sDoc.length() > 100)
                {
                sDoc = sDoc.substring(0, 97) + "...";
                }
            appendString(sb.append("/*"), sDoc).append("*/\n");
            }

        sb.append(toSignatureString());

        for (Composition composition : this.composition)
            {
            sb.append("\n        ")
              .append(composition);
            }

        if (body == null)
            {
            sb.append(';');
            }
        else
            {
            String sBody = body.toString();
            if (sBody.indexOf('\n') >= 0)
                {
                sb.append('\n')
                  .append(indentLines(sBody, "    "));
                }
            else
                {
                sb.append(' ')
                  .append(sBody);
                }
            }

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toSignatureString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Source            source;
    protected Expression        condition;
    protected List<Token>       modifiers;
    protected List<Annotation>  annotations;
    protected Token             category;
    protected Token             name;
    protected List<Token>       qualified;
    protected List<Parameter>   typeParams;
    protected List<Parameter>   constructorParams;
    protected List<Composition> composition;
    protected StatementBlock    body;
    protected Token             doc;
    protected StatementBlock    enclosed;

    private static final Field[] CHILD_FIELDS = fieldsForNames(TypeCompositionStatement.class,
            "annotations", "typeParams", "constructorParams", "composition", "body");
    }
