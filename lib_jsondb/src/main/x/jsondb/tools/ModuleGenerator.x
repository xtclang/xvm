import ecstasy.mgmt.ModuleRepository;

import ecstasy.reflect.AnnotationTemplate;
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

import ecstasy.text.Log;

import oodb.DBObject.DBCategory;


/**
 * The jsondb-based ModuleGenerator.
 */
class ModuleGenerator(String moduleName) {
    /**
     * The implementation name.
     */
    protected String implName = "jsondb";

    /**
     * The underlying (hosted) fully qualified module name.
     */
    protected String moduleName;

    /**
     * Generic templates.
     */
    protected String moduleSourceTemplate        = $./templates/_module.txt;
    protected String rootSchemaSourceTemplate    = $./templates/RootSchemaDeclaration.txt;
    protected String childSchemaSourceTemplate   = $./templates/ChildSchemaDeclaration.txt;
    protected String schemaInfoTemplate          = $./templates/SchemaInfo.txt;
    protected String schemaInstantiationTemplate = $./templates/SchemaInstantiation.txt;
    protected String propertyGetterTemplate      = $./templates/PropertyGetter.txt;
    protected String propertyInfoTemplate        = $./templates/PropertyInfo.txt;
    protected String customInstantiationTemplate = $./templates/CustomInstantiation.txt;
    protected String customDeclarationTemplate   = $./templates/CustomDeclaration.txt;
    protected String customMethodTemplate        = $./templates/CustomMethod.txt;

    /**
     * Generate (if necessary) all the necessary classes to use a DB module.
     *
     * @param repository  the repository to load necessary modules from
     * @param buildDir    the directory to place all generated artifacts to
     * @param errors      the error log
     *
     * @return True iff the module template was successfully created
     * @return the generated module (optional)
     */
    conditional ModuleTemplate ensureDBModule(
            ModuleRepository repository, Directory buildDir, Log errors) {
        ModuleTemplate dbModule = repository.getResolvedModule(moduleName);

        String appName   = moduleName;
        String qualifier = "";
        if (Int dot := appName.indexOf('.')) {
            qualifier = appName[dot ..< appName.size];
            appName   = appName[0 ..< dot];
        }

        String hostName = $"{appName}_{implName}{qualifier}";

        if (ModuleTemplate hostModule := repository.getModule(hostName)) {
            // try to see if the host module is newer than the original module;
            // if anything goes wrong - follow a regular path
            try {
                Time? dbStamp    = dbModule.parent.created;
                Time? hostStamp  = hostModule.parent.created;
                if (dbStamp != Null && hostStamp != Null && hostStamp > dbStamp) {
                    errors.add($"Info: Host module '{hostName}' for '{moduleName}' is up to date");
                    return True, hostModule;
                }
            } catch (Exception ignore) {}
        }

        ClassTemplate appSchemaTemplate;
        if (!(appSchemaTemplate := findSchema(dbModule))) {
            errors.add($"Error: Schema is not found in module '{moduleName}'");
            return False;
        }

        File sourceFile = buildDir.fileFor($"{appName}_{implName}.x");

        if (createModule(sourceFile, appName, qualifier, dbModule, appSchemaTemplate, errors) &&
            compileModule(repository, sourceFile, buildDir, errors)) {
            errors.add($"Info: Created a host module '{hostName}' for '{moduleName}'");
            return repository.getModule(hostName);
        }
        return False;
    }

    /**
     * Create module source file.
     *
     * @return True iff the source file has been successfully created
     */
    Boolean createModule(File sourceFile, String appName, String qualifier,
                         ModuleTemplate moduleTemplate, ClassTemplate appSchemaTemplate, Log errors) {
        if ((
            Int    pid,
            String propertyInfos,
            String propertyTypes,
            String customInstantiations,
            String customDeclarations,
            String childrenIds,
            String childrenNames,
            String rootSchemaSource
            ) :=
            createSchema(appName, moduleTemplate, appSchemaTemplate,
                         rootSchemaSourceTemplate, "", 0, errors)) {
            String appSchema    = appSchemaTemplate.name;
            String moduleSource = moduleSourceTemplate
                                    .replace("%appName%"             , appName)
                                    .replace("%appSchema%"           , appSchema)
                                    .replace("%qualifier%"           , qualifier)
                                    .replace("%ChildrenIds%"         , childrenIds)
                                    .replace("%ChildrenNames%"       , childrenNames)
                                    .replace("%PropertyInfos%"       , propertyInfos)
                                    .replace("%PropertyTypes%"       , propertyTypes)
                                    .replace("%CustomInstantiations%", customInstantiations)
                                    .replace("%CustomDeclarations%"  , customDeclarations)
                                    .replace("%RootSchema%"          , rootSchemaSource)
                                    ;
            sourceFile.create();
            sourceFile.contents = moduleSource.utf8();
            return True;
        }
        return False;
    }

    /**
     * Generate a schema.
     *
     * @return True iff the schema was successfully created
     */
    conditional
        (
        Int    pid,
        String propertyInfos,
        String propertyTypes,
        String customInstantiations,
        String customDeclarations,
        String childrenIds,
        String childrenNames,
        String schemaSource
        )
        createSchema(String         appName,
                     ModuleTemplate moduleTemplate,
                     ClassTemplate  schemaTemplate,
                     String         schemaSourceTemplate,
                     String         parentPath,
                     Int            pid,
                     Log            errors) {
        Tuple<PropertyTemplate, DBCategory>[] dbProps;
        if (!(dbProps := collectDBProps(schemaTemplate, errors))) {
            return False;
        }

        String schemaName           = schemaTemplate.name;
        String schemaParentId       = pid.toString();
        String propertyInfos        = "";
        String propertyTypes        = "";
        String propertyGetters      = "";
        String customInstantiations = "";
        String customDeclarations   = "";
        String childrenIds          = "";
        String childrenNames        = "";
        String childSchemas         = "";

        NextProperty:
        for (Tuple<PropertyTemplate, DBCategory> propInfo : dbProps) {
            PropertyTemplate property = propInfo[0];
            DBCategory       category = propInfo[1];

            TypeTemplate typeTemplate = property.type;

            // already checked at collectDBProps()
            assert Composition classTemplate := typeTemplate.fromClass(),
                   classTemplate.is(ClassTemplate);

            String propertyName  = property.name;
            String propertyPath  = $"{parentPath}/{property.name}";
            String propertyType  = displayName(typeTemplate, appName);
            String propertyId    = (++pid).toString();
            String transactional = "True";
            String options       = "";

            String propertyTypeName = classTemplate.name.replace(".", "_");
            String propertyStoreType;
            String propertyBaseType;
            String propertyTypeParams;

            childrenIds   += $"{propertyId},";
            childrenNames += $"\"{propertyName}\",";
            switch (category) {
            case DBSchema:
                if ((
                    pid,
                    String schemaPropertyInfos,
                    String schemaPropertyTypes,
                    String schemaCustomInstantiations,
                    String schemaCustomDeclarations,
                    String schemaChildrenIds,
                    String schemaChildrenNames,
                    String schemaSource
                    ) := createSchema(appName, moduleTemplate, classTemplate,
                                      childSchemaSourceTemplate, propertyPath, pid, errors)) {
                    propertyInfos += schemaInfoTemplate
                            .replace("%schemaPath%"    , propertyPath)
                            .replace("%schemaId%"      , propertyId)
                            .replace("%schemaParentId%", schemaParentId)
                            .replace("%ChildrenIds%"   , schemaChildrenIds)
                            .replace("%ChildrenNames%" , schemaChildrenNames)
                            ;

                    propertyGetters += propertyGetterTemplate
                            .replace("%appName%"     , appName)
                            .replace("%propertyName%", propertyName)
                            .replace("%propertyId%"  , propertyId)
                            .replace("%propertyType%", propertyType)
                            ;

                    customInstantiations += schemaInstantiationTemplate
                            .replace("%appName%"   , appName)
                            .replace("%schemaName%", propertyTypeName)
                            .replace("%schemaId%"  , propertyId)
                            ;

                    propertyInfos        += schemaPropertyInfos;
                    propertyTypes        += schemaPropertyTypes;
                    customInstantiations += schemaCustomInstantiations;
                    customDeclarations   += schemaCustomDeclarations;
                    childSchemas         += schemaSource;
                    continue NextProperty;
                }
                return False;

            case DBMap:
                TypeTemplate keyType;
                TypeTemplate valueType;
                if (keyType   := resolveFormalType(typeTemplate, "Key",   propertyName, errors),
                    valueType := resolveFormalType(typeTemplate, "Value", propertyName, errors)) {} else {
                    return False;
                }

                String keyTypeName   = displayName(keyType, appName);
                String valueTypeName = displayName(valueType, appName);

                propertyStoreType  = $"{implName}_.storage.MapStore<{keyTypeName}, {valueTypeName}>";
                propertyBaseType   = $"DBMapImpl<{keyTypeName}, {valueTypeName}>";
                propertyTypeParams = $"\"Key\"={keyTypeName}, \"Value\"={valueTypeName}";
                break;

            case DBCounter:
                propertyStoreType  = "{implName}_.storage.CounterStore";
                propertyBaseType   = "DBCounterImpl";
                propertyTypeParams = "";

                if (AnnotationTemplate annotation := property.findAnnotation("oodb.NoTx")) {
                    transactional = "False";
                }
                break;

            case DBValue:
                TypeTemplate valueType;
                if (!(valueType := resolveFormalType(typeTemplate, "Value", propertyName, errors))) {
                    return False;
                }

                String valueTypeName = displayName(valueType, appName);

                propertyStoreType  = $"{implName}_.storage.ValueStore<{valueTypeName}>";
                propertyBaseType   = $"DBValueImpl<{valueTypeName}>";
                propertyTypeParams = $"\"Value\"={valueTypeName}";

                String initialValue = "Null";
                if (AnnotationTemplate annotation := property.findAnnotation("oodb.Initial")) {
                    // TODO GG: we assume here that "value.toString()" can be compiled back,
                    //          which is only correct for few types
                    initialValue = displayValue(annotation.arguments[0].value);
                }

                if (initialValue == "Null") {
                    if (Composition valueClassTemplate := valueType.fromClass(),
                                    valueClassTemplate.is(ClassTemplate) &&
                                    valueClassTemplate.hasDefault) {
                        initialValue = $"{displayName(valueClassTemplate, appName)}.default";
                    } else {
                        errors.add($|Error: Property "{valueType} {propertyName}" must specify \
                                    |an initial value
                                  );
                        return False;
                    }
                }
                options = $"\"initial\"={initialValue}";
                break;

            case DBLog:
                TypeTemplate elementType;
                if (!(elementType := resolveFormalType(typeTemplate, "Element", propertyName, errors))) {
                    return False;
                }

                String elementTypeName = displayName(elementType, appName);

                propertyStoreType  = $"{implName}_.storage.LogStore<{elementTypeName}>";
                propertyBaseType   = $"DBLogImpl<{elementTypeName}>";
                propertyTypeParams = $"\"Element\"={elementTypeName}";

                if (AnnotationTemplate annotation := property.findAnnotation("oodb.NoTx")) {
                    transactional = "False";
                }
                if (AnnotationTemplate annotation := property.findAnnotation("oodb.AutoExpire")) {
                    Duration expiry = annotation.arguments[0].value.as(Duration);
                    options += $"\"expiry\"=Duration:{expiry.seconds}s";
                }

                if (AnnotationTemplate annotation := property.findAnnotation("oodb.AutoTruncate")) {
                    Int truncateSize = annotation.arguments[0].value.as(Int);
                    if (options.size > 0) {
                        options += ", ";
                    }
                    options += $"\"truncate\"=Int:{truncateSize}";
                }

                break;

            case DBProcessor:
                TypeTemplate messageType;
                if (!(messageType := resolveFormalType(typeTemplate, "Message", propertyName, errors))) {
                    return False;
                }

                String messageTypeName = displayName(messageType, appName);

                propertyStoreType  = $"{implName}_.storage.ProcessorStore<{messageTypeName}>";
                propertyBaseType   = $"DBProcessorImpl<{messageTypeName}>";
                propertyTypeParams = $"\"Message\"={messageTypeName}";
                break;

            default:
                throw new UnsupportedOperation($"property={propertyName}, category={category}");
            }

            propertyInfos += propertyInfoTemplate
                                .replace("%propertyPath%"      , propertyPath)
                                .replace("%propertyCategory%"  , category.name)
                                .replace("%propertyId%"        , propertyId)
                                .replace("%propertyParentId%"  , schemaParentId)
                                .replace("%propertyType%"      , propertyType)
                                .replace("%propertyTypeParams%", propertyTypeParams)
                                .replace("%transactional%"     , transactional)
                                .replace("%options%"           , options)
                                ;

            propertyGetters += propertyGetterTemplate
                                .replace("%appName%"     , appName)
                                .replace("%propertyName%", propertyName)
                                .replace("%propertyId%"  , propertyId)
                                .replace("%propertyType%", propertyType)
                                ;

            if (classTemplate.containingModule.qualifiedName == oodb.qualifiedName) {
                // this check assumes that the property type is one of the eight basic DBObject
                // types (DBSchema, DBCounter, DBValue, DBMap, DBList, DBQueue, DBProcessor, DBLog)
                // and doesn't come from the reflected module itself, in which case it must be a
                //"custom" mixin into one of the basic DBObjects
                continue;
            }

            if (classTemplate.format != Mixin) {
                errors.add($"Error: property '{propertyName}' customization '{classTemplate}' must be a mixin");
                return False;
            }

            String customMethods = createMethods(appName, classTemplate);

            customInstantiations += customInstantiationTemplate
                                    .replace("%appName%"          , appName)
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
                                    ;
        }

        String schemaMethods  = createMethods(appName, schemaTemplate);

        String schemaTypeName = displayName(schemaTemplate, appName);
        String schemaSource   = schemaSourceTemplate
                                .replace("%schemaName%"     , schemaName)
                                .replace("%schemaTypeName%" , schemaTypeName)
                                .replace("%PropertyGetters%", propertyGetters)
                                .replace("%SchemaMethods%"  , schemaMethods)
                                .replace("%ChildSchemas%"   , childSchemas)
                                ;
        return True, pid, propertyInfos, propertyTypes,
                     customInstantiations, customDeclarations, childrenIds, childrenNames, schemaSource;
    }

    /**
     * Create the routing logic for all custom methods.
     */
    String createMethods(String appName, ClassTemplate classTemplate) {
        String customMethods = "";

        for (MultiMethodTemplate multimethod : classTemplate.multimethods) {
            for (MethodTemplate method : multimethod.children()) {
                if (!method.isConstructor && !method.isStatic && method.access == Public) {
                    String              methodName = multimethod.name;
                    ParameterTemplate[] params     = method.parameters;
                    ParameterTemplate[] returns    = method.returns;
                    Int                 retCount   = returns.size;

                    String retType;
                    if (retCount == 0) {
                        retType = "void";
                    } else {
                        StringBuffer buf = new StringBuffer();
                        loop:
                        for (Int i : 0 ..< retCount) {
                            ParameterTemplate ret = returns[i];
                            if (ret.category == ConditionalReturn) {
                                "conditional ".appendTo(buf);
                            } else {
                                displayName(ret.type, appName).appendTo(buf);
                            }
                            if (!loop.last) {
                                ", ".append(buf);
                            }
                        }
                        retType = buf.toString();
                    }

                    // internal helper function
                    (String argName, String argDecl)
                            createArgDeclaration(ParameterTemplate param, String appName) {
                        String name = param.name? : assert;
                        String type = displayName(param.type, appName);
                        String dflt = param.category == DefaultParameter
                                ? $" = {displayValue(param.defaultValue)}"
                                : "";
                        return (name, $"{type} {name}{dflt}");
                    }

                    String argsDecl = "";
                    String args     = "";
                    switch (params.size) {
                    case 0:
                        break;

                    case 1:
                        (args, argsDecl) = createArgDeclaration(params[0], appName);
                        break;

                    default:
                        Loop:
                        for (ParameterTemplate param : params) {
                            (String name, String decl) = createArgDeclaration(param, appName);

                            if (!Loop.first) {
                                argsDecl += ", ";
                                args     += ", ";
                            }
                            argsDecl += decl;
                            args     += name;
                        }
                        break;
                    }

                    customMethods += customMethodTemplate
                                        .replace("%appName%" , appName)
                                        .replace("%name%"    , methodName)
                                        .replace("%retType%" , retType)
                                        .replace("%argsDecl%", argsDecl)
                                        .replace("%args%"    , args)
                                        ;
                }
            }
        }

        return customMethods;
    }


    // ----- common helper methods -----------------------------------------------------------------

    /**
     * Find a DB schema.
     */
    conditional ClassTemplate findSchema(ModuleTemplate dbModule) {
        Class         schemaClass    = oodb.RootSchema;
        ClassTemplate schemaTemplate = schemaClass.baseTemplate;

        for (ClassTemplate classTemplate : dbModule.classes) {
            if (classTemplate.format == Interface) {
                for (Contribution contrib : classTemplate.contribs) {
                    if (contrib.action == Implements) {
                        assert Composition template := contrib.ingredient.fromClass(),
                                           template.is(ClassTemplate);
                        if (template == schemaTemplate) {
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
            collectDBProps(ClassTemplate appSchemaTemplate, Log errors) {
        Tuple<PropertyTemplate, DBCategory>[] properties = new Array();

        NextProperty:
        for (PropertyTemplate prop : appSchemaTemplate.properties) {
            TypeTemplate typeTemplate = prop.type;

            if (Composition classTemplate := typeTemplate.fromClass(),
                            classTemplate.is(ClassTemplate)) {
                for ((DBCategory category, TypeTemplate dbType) : DB_TEMPLATES) {
                    if (typeTemplate.isA(dbType)) {
                        properties += Tuple:(prop, category);
                        continue NextProperty;
                    }
                }
            }
            errors.add($"Error: Unsupported property type: \"{prop.type} {prop.name}\"");
            return False;
        }

        // TODO recurse to super template
        return True, properties;
    }

    /**
     * Obtain the formal type for the specified name; log an error if the type cannot be resolved.
     */
    conditional TypeTemplate resolveFormalType(TypeTemplate typeTemplate, String formalName,
                                               String propertyName, Log errors) {
        if (TypeTemplate typeFormal := typeTemplate.resolveFormalType(formalName)) {
            return True, typeFormal;
        }
        errors.add($"Error: Property {propertyName.quoted()} must specify {formalName.quoted()} type");
        return False;
    }

    /**
     * Obtain a display name for the specified type for the specified application.
     */
    String displayName(TypeTemplate type, String appName) {
        switch (type.form) {
        case Class:
            assert Composition composition := type.fromClass();
            String name = displayName(composition, appName);

            if (TypeTemplate[] typeParams := type.parameterized()) {
                StringBuffer buf = new StringBuffer(name.size * typeParams.size);
                buf.append(name)
                   .add('<');

                loop:
                for (TypeTemplate typeParam : typeParams) {
                    if (!loop.first) {
                        buf.append(", ");
                    }
                    buf.append(displayName(typeParam, appName));
                }
                buf.add('>');
                name = buf.toString();
            }
            return name;

        case Intersection:
        case Union:
        case Difference:
            assert (TypeTemplate t1, TypeTemplate t2) := type.relational();
            String op = switch (type.form) {
            case Union:        " | ";
            case Intersection: " + ";
            case Difference:   " - ";
            default: assert;
        };
            return $"{displayName(t1, appName)}{op}{displayName(t2, appName)}";

        case Immutable:
            assert TypeTemplate t1 := type.modifying();
            return $"immutable {displayName(t1, appName)}";

        case Access:
            assert val access := type.accessSpecified();
            assert TypeTemplate t1 := type.modifying();
            return $"{displayName(t1, appName)}:{access.keyword}";

        default:
            assert as $"Not implemented {type=} {type.form=}";
        }
    }

    /**
     * Obtain a display name for the specified composition for the specified application.
     */
    String displayName(Composition composition, String appName) {
        if (composition.is(ClassTemplate)) {
            return composition.implicitName ?: (appName + "_." + composition.displayName);
        }
        TODO AnnotatingComposition
    }

    /**
     * Obtain a display value for the specified constant.
     */
    String displayValue(Object value) {
        Type typeActual = &value.actualType;
        if (typeActual.is(Type<String>)) {
            return value.as(String).quoted();
        }
        if (typeActual.is(Type<Char>)) {
            return $"'{value.as(Char).toString()}'";
        }

        return value.toString();
    }

    /**
     * Compile the specified source file.
     */
    Boolean compileModule(ModuleRepository repository, File sourceFile, Directory buildDir, Log errors) {
        @Inject ecstasy.lang.src.Compiler compiler;

        compiler.setLibraryRepository(repository);
        compiler.setResultLocation(buildDir);

        (Boolean success, String[] compilationErrors) = compiler.compile([sourceFile]);

        if (compilationErrors.size > 0) {
            errors.addAll(compilationErrors);
        }
        return success;
    }


    // ----- constants -----------------------------------------------------------------------------

    static Map<DBCategory, TypeTemplate> DB_TEMPLATES = Map:
            [
            DBSchema    = oodb.DBSchema   .baseTemplate.type,
            DBCounter   = oodb.DBCounter  .baseTemplate.type,
            DBValue     = oodb.DBValue    .baseTemplate.type,
            DBMap       = oodb.DBMap      .baseTemplate.type,
            DBList      = oodb.DBList     .baseTemplate.type,
            DBQueue     = oodb.DBQueue    .baseTemplate.type,
            DBProcessor = oodb.DBProcessor.baseTemplate.type,
            DBLog       = oodb.DBLog      .baseTemplate.type,
            ];
}