/**
 * A ComponentTemplate is a representation of the compiled form of an Ecstasy `module`, `package`, `class`,
 * `const`, `enum`, `service`, `mixin`, or `interface`, or the constructs recursively contained
 * therein.
 *
 * Runtime objects, such as classes and functions, may be able to provide a template, but the
 * opposite is never true; any such relationship is unidirectional.
 */
interface ComponentTemplate
    {
    /**
     * The ComponentTemplate Format enumerates the various forms of
     *
     * Those beginning with "Reserved_" are reserved, and must not be used.
     */
    enum Format<TemplateType extends ComponentTemplate>(Boolean implicitlyStatic, Boolean autoNarrowingAllowed)
        {
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
     * The template within which this template exists. For runtime templates, only the file template
     * will have no parent.
     */
    @RO ComponentTemplate!? parent;

    /**
     * The first ClassTemplate encountered while walking up the parentage chain of this template.
     */
    @RO ClassTemplate? containingClass.get()
        {
        ComponentTemplate!? parent = this.parent;
        while (parent != Null)
            {
            if (parent.is(ClassTemplate))
                {
                return parent;
                }
            parent = parent.parent;
            }
        return Null;
        }

    /**
     * The simple name of the template. The name ordinarily identifies the template within the scope
     * of its parent; the exceptions to this rule are the file and method templates.
     */
    @RO String name;

    @RO String path.get()
        {
        return parent?.path + '.' + name : name;
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
     * Iterate over the child templates of this template.
     */
    Iterator<ComponentTemplate!> children();
    }
