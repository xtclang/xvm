import ecstasy.mgmt.ModuleRepository;

import ecstasy.reflect.ClassTemplate;
import ecstasy.reflect.ClassTemplate.Composition;
import ecstasy.reflect.ClassTemplate.Contribution;
import ecstasy.reflect.FileTemplate;
import ecstasy.reflect.ModuleTemplate;
import ecstasy.reflect.PropertyTemplate;
import ecstasy.reflect.TypeParameter;
import ecstasy.reflect.TypeTemplate;

import oodb.Connection;
import oodb.DBObject;
import oodb.DBObject.DBCategory;
import oodb.DBUser;

import imdb.CatalogMetadata;

/**
 * Host for imdb-based db module.
 */
class ImdbHost
        extends DbHost
    {
    @Inject Console console;

    @Override
    ModuleTemplate generateStubs(ModuleRepository repository, String dbModuleName, Directory buildDir)
        {
        ModuleTemplate dbModule = repository.getResolvedModule(dbModuleName);

        String appName = dbModuleName; // TODO GG: allow fully qualified name

        Directory moduleDir = buildDir.dirFor(appName + "_imdb");
        if (moduleDir.exists)
            {
            moduleDir.deleteRecursively();
            }
        moduleDir.create();

        ClassTemplate appSchemaTemplate;
        if (appSchemaTemplate := findSchema(dbModule)) {}
        else
            {
            throw new IllegalState($"Schema is not found in {dbModuleName} module");
            }

        File moduleFile = moduleDir.fileFor("module.x");

        createModule(moduleFile, appName, appSchemaTemplate);

        // temporary; replace with the compilation of generated source
        return repository.getModule(dbModuleName + "_imdb");
        }

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
                            return (True, classTemplate);
                            }
                        }
                    }
                }
            }
        return False;
        }

    /**
     * Create module.x source file.
     */
    void createModule(File moduleFile, String appName, ClassTemplate appSchemaTemplate)
        {
        String appSchema = appSchemaTemplate.name;

        Tuple<PropertyTemplate, DBCategory>[] dbProps = collectDBProps(appSchemaTemplate);

        String propertyInfos            = "";
        String propertyGetters          = "";
        String customImplInstantiations = "";
        String customImplDeclarations   = "";

        for (Tuple<PropertyTemplate, DBCategory> propInfo : dbProps)
            {
            PropertyTemplate property = propInfo[0];
            DBCategory       category = propInfo[1];

            assert Composition typeTemplate := property.type.fromClass();
            assert typeTemplate.is(ClassTemplate);

            String propertyName     = property.name;
            String propertyType     = displayName(typeTemplate, appName);
            String propertyTypeName = typeTemplate.name; // TODO handle composite type

            String propertyInfo = $./templates/PropertyInfo.txt;

            propertyInfos += propertyInfo
                                .replace("%propertyName%"    , propertyName)
                                .replace("%propertyCategory%", category.name)
                                .replace("%propertyType%"    , propertyType)
                                ;

            String propertyGetter = $./templates/PropertyGetter.txt;

            propertyGetters += propertyGetter
                                .replace("%appName%"     , appName)
                                .replace("%propertyName%", propertyName)
                                .replace("%propertyType%", propertyType)
                                ;

//            switch (category)
//                {
//                case DBMap:
//                    assert TypeTemplate keyType       := property.type.resolveFormalType("Key");
//                    assert TypeTemplate valueType     := property.type.resolveFormalType("Value");
//                    assert Composition  keyTemplate   := keyType.fromClass();
//                    assert Composition  valueTemplate := valueType.fromClass();
//                    assert keyTemplate.is(ClassTemplate);
//                    assert valueTemplate.is(ClassTemplate);
//
//                    String childClass = $./templates/ClientDBMap.txt;
//                    String methods     = "";
//                    String invocations = "";
//
//                    childClasses  += childClass
//                                    .replace("%appSchema%"             , appSchema)
//                                    .replace("%propertyType%"          , propertyType)
//                                    .replace("%propertyTypeName%"      , propertyTypeName)
//                                    .replace("%keyType%"               , displayName(keyTemplate, appName))
//                                    .replace("%valueType%"             , displayName(valueTemplate, appName))
//                                    .replace("%ClientDBMapMethods%"    , methods)
//                                    .replace("%ClientDBMapInvocations%", invocations)
//                                    ;
//                    break;
//
//                case DBCounter:
//                    String childClass = $./templates/ClientDBCounter.txt;
//                    childClasses  += childClass
//                                    .replace("%appSchema%"       , appSchema)
//                                    .replace("%propertyType%"    , propertyType)
//                                    .replace("%propertyTypeName%", propertyTypeName);
//                    break;
//
//                default:
//                    TODO
//                }
            }

        String moduleTemplate = $./templates/_module.txt;
        String moduleSource   = moduleTemplate
                                .replace("%appName%"        , appName)
                                .replace("%appSchema%"      , appSchema)
                                .replace("%PropertyInfos%"  , propertyInfos)
                                .replace("%PropertyGetters%", propertyGetters)
                                ;

        moduleFile.create();
        writeUtf(moduleFile, moduleSource);
        }

    /**
     * Collect all DB properties.
     */
    Tuple<PropertyTemplate, DBCategory>[] collectDBProps(ClassTemplate appSchemaTemplate)
        {
        Tuple<PropertyTemplate, DBCategory>[] properties = new Array();

        NextProperty:
        for (PropertyTemplate prop : appSchemaTemplate.properties)
            {
            for ((DBCategory category, TypeTemplate dbType) : DB_TEMPLATES)
                {
                if (prop.type.isA(dbType))
                    {
                    properties += Tuple:(prop, category);
                    continue NextProperty;
                    }
                }
            throw new UnsupportedOperation($"Unsupported property type {prop.name} {prop.type}");
            }

        // TODO recurse to super template
        return properties;
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

    /**
     * Obtain a display name for the specified type for the specified application.
     */
    String displayName(ClassTemplate template, String appName)
        {
        return template.implicitName ?: (appName + "_." + template.displayName);
        }

    @Override
    function oodb.Connection(DBUser)
            ensureDatabase(Map<String, String>? configOverrides = Null)
        {
        CatalogMetadata meta = dbContainer.innerTypeSystem.primaryModule.as(CatalogMetadata);
        return meta.ensureConnectionFactory();
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