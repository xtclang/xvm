package org.xvm.tool;


import java.util.List;
import java.util.Set;

import org.xvm.api.Connector;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.ModuleStructure;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.text.xString;

import org.xvm.tool.flag.FlagSet;
import org.xvm.util.Severity;

/**
 * This is the Ecstasy execute command "xec" or "xtc run".
 * <pre>
 *  java org.xvm.tool.Runner [-L repo(s)] [-M method_name] app.xtc [argv]
 * </pre>
 * where the default method is "run" with no arguments.
 */
public class Runner
        extends BaseRunCommand
    {
    /**
     * Entry point from the OS.
     *
     * @param asArg command line arguments
     */
    public static void main(String[] asArg)
        {
        try
            {
            Launcher.launch(CMD_RUNNER, asArg);
            }
        catch (LauncherException e)
            {
            System.exit(e.error ? -1 : 0);
            }
        }

    /**
     * Runner constructor.
     */
    public Runner()
        {
        this(null);
        }

    /**
     * Runner constructor.
     *
     * @param console  representation of the terminal within which this command is run
     */
    public Runner(Console console)
        {
        this(CMD_RUNNER, console);
        }

    /**
     * Runner constructor.
     *
     * @param sName    the name of this command (as specified on the command line)
     * @param console  representation of the terminal within which this command is run
     */
    public Runner(String sName, Console console)
        {
        super(sName, console);
        }

        @Override
    protected void invoke(Connector connector, ConstantPool pool, ModuleStructure module)
        {
        String               sMethod    = getMethodName();
        Set<MethodStructure> setMethods = connector.getContainer().findMethods(sMethod);
        if (setMethods.size() != 1)
            {
            if (setMethods.isEmpty())
                {
                log(Severity.ERROR, "Missing method \"" + sMethod + "\" in module " + module.getName());
                }
            else
                {
                log(Severity.ERROR, "Ambiguous method \"" + sMethod + "\" in module " + module.getName());
                }
            abort(true);
            }

        String[]        asArg       = getMethodArgs();
        ObjectHandle[]  ahArg       = Utils.OBJECTS_NONE;
        MethodStructure method      = setMethods.iterator().next();
        TypeConstant    typeStrings = pool.ensureArrayType(pool.typeString());

        switch (method.getRequiredParamCount())
            {
            case 0:
                if (asArg != null)
                    {
                    // the method doesn't require anything, but there are args
                    if (method.getParamCount() > 0)
                        {
                        TypeConstant typeArg = method.getParam(0).getType();
                        if (typeStrings.isA(typeArg))
                            {
                            ahArg = new ObjectHandle[]{xString.makeArrayHandle(asArg)};
                            }
                        else
                            {
                            log(Severity.ERROR, "Unsupported argument type \"" +
                                typeArg.getValueString() + "\" for method \"" + sMethod + "\"");
                            abort(true);
                            }
                        }
                    else
                        {
                        log(Severity.WARNING, "Method \"" + sMethod +
                            "\" does not take any parameters; ignoring the specified arguments");
                        }
                    }
                break;

            case 1:
                {
                TypeConstant typeArg = method.getParam(0).getType();
                if (typeStrings.isA(typeArg))
                    {
                    // the method requires an array that we can supply
                    ahArg = new ObjectHandle[]{
                        asArg == null
                            ? xString.ensureEmptyArray()
                            : xString.makeArrayHandle(asArg)};
                    }
                else
                    {
                    log(Severity.ERROR, "Unsupported argument type \"" +
                        typeArg.getValueString() + "\" for method \"" + sMethod + "\"");
                    abort(true);
                    }
                break;
                }

            default:
                log(Severity.ERROR, "Unsupported method arguments \"" +
                    method.getIdentityConstant().getSignature().getValueString());
                abort(true);
            }
        connector.invoke0(sMethod, ahArg);
        }

    /**
     * Return the method name set with the --method flag.
     */
    public String getMethodName()
        {
        return flags().getString(ARG_METHOD);
        }

    /**
     * Return the method arguments.
     *
     * @return the method arguments
     */
    public String[] getMethodArgs()
        {
        FlagSet      flags   = flags();
        List<String> listArg = flags.getArguments();
        int          cLen    = Math.max(1, flags.getArgsLengthAtDash());
        return listArg.stream().skip(cLen).toArray(String[]::new);
        }

        // ----- text output and error handling --------------------------------------------------------

    @Override
    public String desc()
        {
        return """
            Ecstasy runner:

            Executes an Ecstasy module, compiling it first if necessary.
            Supports running any of the following file types:

                <filename>      - assumed to be a compiled module file name
                <filename>.x    - an Ecstasy source file name
                <filename>.xtc  - a compiled module file name""";
        }

    @Override
    protected String getShortDescription()
        {
        return "Executes an Ecstasy module, compiling it first if necessary.";
        }

    @Override
    protected String getUsageLine(String sName)
        {
        return sName + " [flags] <filename> [-- <program args>]";
        }

    // ----- command line flags --------------------------------------------------------------------

    @Override
    protected FlagSet instantiateFlags(String sName)
        {
        // the run commands add the --method flag to the base runner flags
        return super.instantiateFlags(sName)
                .withString(ARG_METHOD, 'm', "Method name; defaults to \"run\"", "run");
        }

    // ----- constants -----------------------------------------------------------------------------

    /**
     * The Ecstasy runner executable name.
     */
    public static final String CMD_RUNNER = "xec";

    /**
     * The "method" flag name.
     */
    public static final String ARG_METHOD = "method";
    }