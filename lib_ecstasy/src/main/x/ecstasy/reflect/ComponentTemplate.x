/**
 * A ComponentTemplate is a representation of the compiled form of an Ecstasy `module`, `package`, `class`,
 * `const`, `enum`, `service`, `mixin`, or `interface`, or the constructs recursively contained
 * therein.
 *
 * Runtime objects, such as classes and functions, may be able to provide a template, but the
 * opposite is never true; any such relationship is unidirectional.
 */
interface ComponentTemplate
        extends Stringable {
    /**
     * The ComponentTemplate Format enumerates the various forms of
     *
     * Those beginning with "Reserved_" are reserved, and must not be used.
     */
    enum Format<TemplateType extends ComponentTemplate>(Boolean implicitlyStatic, Boolean autoNarrowingAllowed) {
        Interface  <ClassTemplate      >(False, True ),
        Class      <ClassTemplate      >(False, True ),
        Const      <ClassTemplate      >(False, True ),
        Enum       <ClassTemplate      >(True , False),
        EnumValue  <ClassTemplate      >(True , False),
        Mixin      <ClassTemplate      >(False, True ),
        Service    <ClassTemplate      >(False, True ),
        Package    <PackageTemplate    >(True , False),
        Module     <ModuleTemplate     >(True , False),
        TypeDef    <TypedefTemplate    >(False, False),
        Property   <PropertyTemplate   >(False, False),
        Method     <MethodTemplate     >(False, False),
        Reserved_C <ComponentTemplate  >(False, False),
        Reserved_D <ComponentTemplate  >(False, False),
        MultiMethod<MultiMethodTemplate>(False, False),
        File       <FileTemplate       >(False, False)
    }

    /**
     * The template format.
     */
    @RO Format format;

    /**
     * The template within which this template exists. For runtime templates, only the [FileTemplate]
     * will have no parent.
     */
    @RO ComponentTemplate!? parent;

    /**
     * The first ClassTemplate encountered while walking up the parentage chain of this template.
     */
    @RO ClassTemplate? containingClass.get() {
        ComponentTemplate? parent = this.parent;
        while (parent != Null) {
            if (parent.is(ClassTemplate)) {
                return parent;
            }
            parent = parent.parent;
        }
        return Null;
    }

    /**
     * The ModuleTemplate this template belongs to. For runtime templates, only the [FileTemplate]
     * will have no `containingModule`.
     */
    @RO ModuleTemplate? containingModule.get() {
        ComponentTemplate? parent = this.parent;
        while (parent != Null) {
            if (parent.is(ModuleTemplate)) {
                return parent;
            }
            parent = parent.parent;
        }
        return Null;
    }

    /**
     * The FileTemplate this template belongs to.
     */
    @RO FileTemplate containingFile.get() {
        ComponentTemplate? parent = this.parent;
        while (True) {
            if (parent.is(FileTemplate)) {
                return parent;
            }

            assert parent != Null;
            parent = parent.parent;
        }
    }

    /**
     * The simple name of the template. The name ordinarily identifies the template within the scope
     * of its parent; the exceptions to this rule are the file and method templates.
     */
    @RO String name;

    /**
     * The path of the template is composed of its module qualified name followed by a colon,
     * followed by a dot-delimited sequence of names necessary to identify this class within its
     * module.
     */
    @RO String path.get() {
        ComponentTemplate? parent = this.parent;
        return parent == Null
                ? name
                : parent.is(ModuleTemplate) // ModuleTemplate.path is always ':'-terminated
                    ? parent.path + name
                    : parent.path + '.' + name;
    }

    /**
     * Templates include an "access" indicator. The meaning of the access indicator is specific to
     * the combination of the context of the template, and the type of the template.
     */
    @RO Access access;

    /**
     * Templates include an "abstract" indicator. The typical meaning of this indicator is that the
     * class which this template defines (or is part of) cannot be instantiated.
     */
    @RO Boolean isAbstract;

    /**
     * Templates always encode a "static" indicator. The meaning of this indicator is specific to
     * the combination of the context of the template, and the type of the template.
     */
    @RO Boolean isStatic;

    /**
     * Templates always encode a "synthetic" indicator. This indicator usually implies that the
     * template was created automatically by the compilation process, and thus does not correspond
     * directly to a similarly named portion of source code.
     */
    @RO Boolean synthetic;

    @RO String? doc;

    /**
     * The child templates of this template.
     */
    ComponentTemplate![] children();


    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength() {
        return path.size;
    }

    @Override
    Appender<Char> appendTo(Appender<Char> buf) {
        return path.appendTo(buf);
    }
}