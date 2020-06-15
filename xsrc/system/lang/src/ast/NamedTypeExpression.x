import io.TextPosition;

import reflect.InvalidType;

import src.Lexer.Token;


/**
 * Represents a named type, including optional access, non-narrowing designation, and
 * parameters. For example:
 *
 *     ecstasy.collections.HashMap!<String?, IntLiteral>
 */
const NamedTypeExpression(Token[]?          moduleNames,
                          Token[]           names,
                          Token?            access,
                          Token?            noNarrow,
                          TypeExpression[]? params,
                          TextPosition      end)
        extends TypeExpression
    {
    construct(Token[]           names,
              Token?            access,
              Token?            noNarrow,
              TypeExpression[]? params,
              TextPosition      end)
        {
        construct NamedTypeExpression(Null, names, access, noNarrow, params, end);
        }

    @Override
    TextPosition start.get()
        {
        return moduleNames?[0].start : names[0].start;
        }

    @Override
    conditional Type resolveType(TypeSystem typeSystem, Boolean hideExceptions = False)
        {
        Package? pkg = Null; // TODO GG - this should not need to be initialized to Null
        if (moduleNames == Null)
            {
            pkg = typeSystem.primaryModule;
            }
        else
            {
            // build the module name
            String moduleName = toDotDelimString(moduleNames);
            if (pkg := typeSystem.moduleByQualifiedName.get(moduleName))
                {
                }
            else if (!moduleName.indexOf('.'), pkg := typeSystem.moduleBySimpleName.get(moduleName))
                {
                }
            else
                {
                // no matching module
                return False;
                }
            }

        // process names
        Class clz = &pkg.actualClass;
        Loop: for (Token name : names)
            {
            // TODO CP use TypeSystem.resolveChild()

            try
                {
                clz = clz.childForName(name.valueText);
                }
            catch (Exception e)
                {
                if (Loop.first && moduleNames == Null,
                        Type type := TypeSystem.implicitTypes.get(name.valueText))
                    {
                    assert clz := type.fromClass();
                    }
                else
                    {
                    return False;
                    }
                }

            // TODO GG COMPILER-91: The expression type is not nullable: "Ecstasy:Module". ("pkg != Null")
            private Package? makeNullable(Package? pkg) {return pkg;}
            pkg = makeNullable(pkg);

            if (pkg != Null && clz.PublicType.isA(Package), pkg := clz.as(Class<Package>).isSingleton())
                {
                // if the class is a package, that package may actually be an import of another
                // module, so follow that link
                if (pkg := pkg.isModuleImport())
                    {
                    clz = &pkg.actualClass;
                    }
                }
            else
                {
                pkg = Null;
                }
            }

        // process access
        Type type = access == Null
                ? clz
                : switch (access.id)
                    {
                    case Public:    clz.PublicType;
                    case Protected: clz.ProtectedType;
                    case Private:   clz.PrivateType;
                    case Struct:    clz.StructType;
                    default: assert;
                    };

        // process noNarrow
        // -> noNarrow has no meaning at runtime

        // process params
        if (params != Null)
            {
            Type[] paramTypes = new Type[];
            for (Int i : [0..params.size))
                {
                if (Type paramType := params[i].resolveType(typeSystem, hideExceptions))
                    {
                    // TODO GG paramTypes[i] = paramType;
                    //java.lang.NullPointerException
                    //	at org.xvm.runtime.template._native.reflect.xRTType.invokeParameterize(xRTType.java:1247)
                    //	at org.xvm.runtime.template._native.reflect.xRTType.invokeNative1(xRTType.java:246)
                    //	at org.xvm.runtime.CallChain.invoke(CallChain.java:137)

                    paramTypes.add(paramType);
                    }
                else
                    {
                    return False;
                    }
                }
            try
                {
                type = type.parameterize(paramTypes);
                }
            catch (InvalidType e)
                {
                if (hideExceptions)
                    {
                    return False;
                    }

                throw e;
                }
            }

        return True, type;
        }

    @Override
    String toString()
        {
        StringBuffer buf = new StringBuffer();

        if (moduleNames != Null)
            {
            Loop: for (Token token : moduleNames)
                {
                if (!Loop.first)
                    {
                    buf.add('.');
                    }
                token.appendTo(buf);
                }

            buf.add(':');
            }

        Loop: for (Token token : names)
            {
            if (!Loop.first)
                {
                buf.add('.');
                }
            token.appendTo(buf);
            }

        if (access != Null)
            {
            buf.add(':');
            access.id.text.appendTo(buf);
            }

        if (noNarrow != Null)
            {
            noNarrow.id.text.appendTo(buf);
            }

        if (params != Null)
            {
            buf.add('<');
            Loop: for (TypeExpression param : params)
                {
                if (!Loop.first)
                    {
                    buf.add(',').add(' ');
                    }
                param.appendTo(buf);
                }
            buf.add('>');
            }

        return buf.toString();
        }
    }
