/**
 * This is the native runtime implementation of Property.
 */
const RTProperty<Target, Referent, Implementation extends Ref<Referent>>
        implements Property<Target, Referent, Implementation>
    {
    @Override @RO String  name                  .get() { TODO("native"); }
    @Override @RO Boolean readOnly              .get() { TODO("native"); }
    @Override @RO Boolean hasUnreachableSetter  .get() { TODO("native"); }
    @Override @RO Boolean formal                .get() { TODO("native"); }
    @Override @RO Boolean hasField              .get() { TODO("native"); }
    @Override @RO Boolean injected              .get() { TODO("native"); }
    @Override @RO Boolean lazy                  .get() { TODO("native"); }
    @Override @RO Boolean atomic                .get() { TODO("native"); }
    @Override @RO Boolean abstract              .get() { TODO("native"); }

    @Override conditional Referent isConstant()        { TODO("native"); }
    @Override Implementation of(Target target)         { TODO("native"); }
    @Override Referent get(Target target)              { TODO("native"); }
    @Override void set(Target target, Referent value)  { TODO("native"); }


    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        return Referent.estimateStringLength() + 1 + name.size;
        }

    @Override
    void appendTo(Appender<Char> appender)
        {
        Referent.appendTo(appender);
        appender.add(' ')
                .add(name);
        }
    }
