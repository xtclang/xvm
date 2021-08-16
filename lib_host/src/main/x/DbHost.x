import ecstasy.io.Log;

import ecstasy.mgmt.Container;
import ecstasy.mgmt.ModuleRepository;

import ecstasy.reflect.ClassTemplate;
import ecstasy.reflect.ClassTemplate.Composition;
import ecstasy.reflect.ClassTemplate.Contribution;
import ecstasy.reflect.MethodTemplate;
import ecstasy.reflect.ModuleTemplate;
import ecstasy.reflect.MultiMethodTemplate;
import ecstasy.reflect.ParameterTemplate;
import ecstasy.reflect.PropertyTemplate;
import ecstasy.reflect.TypeParameter;
import ecstasy.reflect.TypeTemplate;


import oodb.DBObject;
import oodb.DBObject.DBCategory;

/**
 * An abstract host for a DB module.
 */
@Abstract
class DbHost
    {
    @Abstract
    @RO String hostName;

    @Abstract
    @RO String moduleSourceTemplate;

    @Abstract
    @RO String propertyGetterTemplate;

    @Abstract
    @RO String propertyInfoTemplate;

    @Abstract
    @RO String customInstantiationTemplate;

    @Abstract
    @RO String customDeclarationTemplate;

    @Abstract
    @RO String customMethodTemplate;

    @Abstract
    @RO String customInvocationTemplate;


    /**
     * Generate all the necessary classes to use a DB modules.
     *
     * @param repository    the repository to load necessary modules from
     * @param dbModuleName  the name of the hosted DB module
     * @param buildDir      the directory to place all generated artifacts to
     * @param errors        the error log
     *
     * @return True iff the module template was successfully created
     * @return the generated module (optional)
     */
    conditional ModuleTemplate generateDBModule(
            ModuleRepository repository, String dbModuleName, Directory buildDir, Log errors)
        {
        ModuleTemplate dbModule = repository.getResolvedModule(dbModuleName);

        String appName = dbModuleName; // TODO GG: allow fully qualified name

        Directory moduleDir = buildDir.dirFor($"{appName}_{hostName}");
        if (moduleDir.exists)
            {
            moduleDir.deleteRecursively();
            }
        moduleDir.create();

        ClassTemplate appSchemaTemplate;
        if (appSchemaTemplate := findSchema(dbModule)) {}
        else
            {
            errors.add($"Schema is not found in {dbModuleName} module");
            return False;
            }

        File sourceFile = moduleDir.fileFor("module.x");

        if (createModule(sourceFile, appName, dbModule, appSchemaTemplate, errors) &&
            compileModule(sourceFile, buildDir, errors))
            {
            return True, repository.getModule($"{dbModuleName}_{hostName}");
            }
        return False;
        }

    /**
     * Create module.x source file.
     */
    Boolean createModule(File sourceFile, String appName,
                         ModuleTemplate moduleTemplate, ClassTemplate appSchemaTemplate, Log errors)
        {
        String appSchema = appSchemaTemplate.name;

        Tuple<PropertyTemplate, DBCategory>[] dbProps;
        if (dbProps := collectDBProps(appSchemaTemplate, errors)) {}
        else
            {
            return False;
            }

        String propertyInfos        = "";
        String propertyTypes        = "";
        String propertyGetters      = "";
        String customInstantiations = "";
        String customDeclarations   = "";

        Int pid = 0;
        for (Tuple<PropertyTemplate, DBCategory> propInfo : dbProps)
            {
            PropertyTemplate property = propInfo[0];
            DBCategory       category = propInfo[1];

            TypeTemplate typeTemplate = property.type;

            // already checked at collectDBProps()
            assert Composition classTemplate := typeTemplate.fromClass(),
                   classTemplate.is(ClassTemplate);

            String propertyName = property.name;
            String propertyType = displayName(classTemplate, appName);
            String propertyId   = (++pid).toString();

            String propertyTypeName = classTemplate.name.replace(".", "_");
            String propertyStoreType;
            String propertyBaseType;
            String propertyTypeParams;

            switch (category)
                {
                case DBMap:
                    assert TypeTemplate keyType   := typeTemplate.resolveFormalType("Key");
                    assert TypeTemplate valueType := typeTemplate.resolveFormalType("Value");

                    String keyTypeName   = displayName(keyType, appName);
                    String valueTypeName = displayName(valueType, appName);

                    propertyStoreType  = $"{hostName}_.storage.MapStore<{keyTypeName}, {valueTypeName}>";
                    propertyBaseType   = $"DBMapImpl<{keyTypeName}, {valueTypeName}>";
                    propertyTypeParams = $"\"Key\"={keyTypeName}, \"Value\"={valueTypeName}";
                    break;

                case DBCounter:
                    propertyStoreType  = "{hostName}_.storage.CounterStore";
                    propertyBaseType   = "DBCounterImpl";
                    propertyTypeParams = "";
                    break;

                default:
                    TODO
                }

            propertyInfos += propertyInfoTemplate
                                .replace("%propertyName%"      , propertyName)
                                .replace("%propertyCategory%"  , category.name)
                                .replace("%propertyId%"        , propertyId)
                                .replace("%propertyParentId%"  , "0") // TODO
                                .replace("%propertyType%"      , propertyType)
                                .replace("%propertyTypeParams%", propertyTypeParams)
                                ;

            propertyGetters += propertyGetterTemplate
                                .replace("%appName%"     , appName)
                                .replace("%propertyName%", propertyName)
                                .replace("%propertyId%"  , propertyId)
                                .replace("%propertyType%", propertyType)
                                ;

            if (classTemplate.containingModule != moduleTemplate)
                {
                continue;
                }

            String customMethods     = "";
            String customInvocations = "";

            for (MultiMethodTemplate multimethod : classTemplate.multimethods)
                {
                String methodName = multimethod.name;
                for (MethodTemplate method : multimethod.children())
                    {
                    if (!method.isConstructor && !method.isStatic && method.access == Public)
                        {
                        ParameterTemplate[] params  = method.parameters;
                        ParameterTemplate[] returns = method.returns;

                        String retType = switch (returns.size)
                                {
                                case 0 : "void";
                                case 1 : displayName(returns[0].type, appName);
                                default: $"({{for (val r : returns) {$.addAll(displayName(r.type, appName)); $.add(',');} }})";

// TODO CP: the equivalent multi-line doesn't parse
//                                default: $|({{for (val r : returns)
//                                          |    {
//                                          |    $.addAll(displayName(r.type, appName));
//                                          |    $.add(',');
//                                          |    }
//                                          |}})
//                                          ;
                                };

                        String argDecl     = "";
                        String args        = "";
                        String argTypes    = "";
                        String tupleValues = "";
                        switch (params.size)
                            {
                            case 0:
                                break;

                            case 1:
                                args        = params[0].name? : assert;
                                argTypes    = displayName(params[0].type, appName);
                                argDecl     = $"{argTypes} {args}";
                                tupleValues = "args[0]";
                                break;

                            default:
                                Loop:
                                for (ParameterTemplate param : params)
                                    {
                                    String name = param.name? : assert;
                                    String type = displayName(param.type, appName);

                                    if (!Loop.first)
                                        {
                                        argDecl     += ", ";
                                        args        += ", ";
                                        argTypes    += ", ";
                                        tupleValues += ", ";
                                        }
                                    argDecl     += $"{type} {name}";
                                    args        += name;
                                    argTypes    += type;
                                    tupleValues += $"args[{Loop.count}]";
                                    }
                                break;
                            }

                        customMethods += customMethodTemplate
                                            .replace("%appName%", appName)
                                            .replace("%name%"   , methodName)
                                            .replace("%retType%", retType)
                                            .replace("%argDecl%", argDecl)
                                            .replace("%args%"   , args)
                                            ;

                        customInvocations += customInvocationTemplate
                                            .replace("%name%"        , methodName)
                                            .replace("%argTypes%"    , argTypes)
                                            .replace("%arg%"         , args)
                                            .replace("%tupleValues%" , tupleValues)
                                            ;
                        }
                    }
                }

            customInstantiations += customInstantiationTemplate
                                    .replace("%appName%"          , appName)
                                    .replace("%propertyName%"     , propertyName)
                                    .replace("%propertyId%"       , propertyId)
                                    .replace("%propertyType%"     , propertyType)
                                    .replace("%propertyTypeName%" , propertyTypeName)
                                    .replace("%propertyStoreType%", propertyStoreType)
                                    ;

            customDeclarations += customDeclarationTemplate
                                    .replace("%propertyType%"     , propertyType)
                                    .replace("%propertyTypeName%" , propertyTypeName)
                                    .replace("%propertyStoreType%", propertyStoreType)
                                    .replace("%propertyBaseType%" , propertyBaseType)
                                    .replace("%CustomMethods%"    , customMethods)
                                    .replace("%CustomInvocations%", customInvocations)
                                    ;
            }

        String moduleSource = moduleSourceTemplate
                                .replace("%appName%"             , appName)
                                .replace("%appSchema%"           , appSchema)
                                .replace("%PropertyInfos%"       , propertyInfos)
                                .replace("%PropertyTypes%"       , propertyTypes)
                                .replace("%PropertyGetters%"     , propertyGetters)
                                .replace("%CustomInstantiations%", customInstantiations)
                                .replace("%CustomDeclarations%"  , customDeclarations)
                                ;

        sourceFile.create();
        writeUtf(sourceFile, moduleSource);
        return True;
        }

    /**
     * Check an existence of the DB (e.g. on disk); create or recover if necessary.
     *
     * @return a connection factory
     */
    function oodb.Connection(oodb.DBUser)
        ensureDatabase(Map<String, String>? configOverrides = Null);

    /**
     * The Container that hosts the DB module.
     */
    @Unassigned
    Container dbContainer;


    // ----- common helper methods -----------------------------------------------------------------

    /**
     * Find a DB schema.
     */
    conditional ClassTemplate findSchema(ModuleTemplate dbModule)
        {
        Class         schemaClass    = oodb.RootSchema;
        ClassTemplate schemaTemplate = schemaClass.baseTemplate;

        for (ClassTemplate classTemplate : dbModule.classes)
            {
            if (classTemplate.format == Interface)
                {
                for (Contribution contrib : classTemplate.contribs)
                    {
                    if (contrib.action == Implements)
                        {
                        ClassTemplate template = contrib.ingredient.as(ClassTemplate);
                        if (template == schemaTemplate)
                            {
                            return True, classTemplate;
                            }
                        }
                    }
                }
            }
        return False;
        }

    /**
     * Collect all DB properties.
     */
    conditional Tuple<PropertyTemplate, DBCategory>[]
            collectDBProps(ClassTemplate appSchemaTemplate, Log errors)
        {
        Tuple<PropertyTemplate, DBCategory>[] properties = new Array();

        NextProperty:
        for (PropertyTemplate prop : appSchemaTemplate.properties)
            {
            TypeTemplate typeTemplate = prop.type;

            if (Composition classTemplate := typeTemplate.fromClass(),
                            classTemplate.is(ClassTemplate))
                {
                for ((DBCategory category, TypeTemplate dbType) : DB_TEMPLATES)
                    {
                    if (typeTemplate.isA(dbType))
                        {
                        properties += Tuple:(prop, category);
                        continue NextProperty;
                        }
                    }
                }
            errors.add($"Unsupported property type: {prop.name} {prop.type}");
            return False;
            }

        // TODO recurse to super template
        return True, properties;
        }

    /**
     * Obtain a display name for the specified type for the specified application.
     */
    String displayName(TypeTemplate type, String appName)
        {
        assert Composition composition := type.fromClass();
        return displayName(composition, appName);
        }

    /**
     * Obtain a display name for the specified composition for the specified application.
     */
    String displayName(Composition composition, String appName)
        {
        if (composition.is(ClassTemplate))
            {
            return composition.implicitName ?: (appName + "_." + composition.displayName);
            }
        TODO AnnotatingComposition
        }

    /**
     * Compile the specified source file.
     */
    Boolean compileModule(File sourceFile, Directory buildDir, Log errors)
        {
        @Inject ecstasy.lang.src.Compiler compiler;

        compiler.setResultLocation(buildDir);

        (Boolean success, String[] compilationErrors) = compiler.compile([sourceFile]);

        if (compilationErrors.size > 0)
            {
            errors.addAll(compilationErrors);
            }
        return success;
        }

    /**
     * The code below should be replaced with
     *      file.contents = contents.utfBytes();
     */
    void writeUtf(File file, String contents)
        {
        import ecstasy.io.ByteArrayOutputStream as Stream;
        import ecstasy.io.UTF8Writer;
        import ecstasy.io.Writer;

        Stream out    = new Stream(contents.size);
        Writer writer = new UTF8Writer(out);
        writer.addAll(contents);

        file.contents = out.bytes.freeze(True);
        }


    // ----- constants -----------------------------------------------------------------------------

    static TypeTemplate DBObject_TEMPLATE = DBObject.baseTemplate.type;

    static Map<DBCategory, TypeTemplate> DB_TEMPLATES = Map:
            [
            DBMap       = oodb.DBMap     .baseTemplate.type,
            DBList      = oodb.DBList    .baseTemplate.type,
            DBQueue     = oodb.DBQueue   .baseTemplate.type,
            DBLog       = oodb.DBLog     .baseTemplate.type,
            DBCounter   = oodb.DBCounter .baseTemplate.type,
            DBValue     = oodb.DBValue   .baseTemplate.type,
            DBFunction  = oodb.DBFunction.baseTemplate.type,
            DBSchema    = oodb.DBSchema  .baseTemplate.type
            ];
    }