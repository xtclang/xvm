package org.xvm.tool.flag;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Map;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.ArrayMatching.arrayContaining;

public class FlagSetTest
    {
    @Test
    public void shouldCreateDefaultFlagSet()
        {
        FlagSet flagSet = new FlagSet();
        assertThat(flagSet.hasFlags(), is(false));
        assertThat(flagSet.isAllowInterspersed(), is(true));
        assertThat(flagSet.isAllowUnknownFlags(), is(false));
        assertThat(flagSet.isParsed(), is(false));
        assertThat(flagSet.hasNonHiddenFlags(), is(false));
        }

    @Test
    public void shouldCreateFlagSet()
        {
        boolean allowInterspersed = false;
        boolean allowUnknown      = true;
        FlagSet flagSet           = new FlagSet(allowInterspersed, allowUnknown);
        assertThat(flagSet.hasFlags(), is(false));
        assertThat(flagSet.isAllowInterspersed(), is(allowInterspersed));
        assertThat(flagSet.isAllowUnknownFlags(), is(allowUnknown));
        assertThat(flagSet.isParsed(), is(false));
        }

    @Test
    public void shouldCreateFlagSetWithHelpFlag()
        {
        boolean allowInterspersed = false;
        boolean allowUnknown      = true;
        FlagSet flagSet           = new FlagSet(allowInterspersed, allowUnknown).withHelp();

        assertThat(flagSet.hasFlags(), is(true));

        Map<String, Flag<?>> mapFlag = flagSet.getFormalFlags();
        assertThat(mapFlag, is(notNullValue()));
        assertThat(mapFlag.size(), is(1));
        assertThat(flagSet.hasNonHiddenFlags(), is(true));

        Flag<?> flag = mapFlag.get(FlagSet.FLAG_HELP);
        assertThat(flag, is(notNullValue()));
        assertThat(flag.getName(), is(FlagSet.FLAG_HELP));
        }

    @Test
    public void shouldCreateFlagSetWithModulePathFlag()
        {
        boolean allowInterspersed = false;
        boolean allowUnknown      = true;
        FlagSet flagSet           = new FlagSet(allowInterspersed, allowUnknown).withModulePath();

        assertThat(flagSet.hasFlags(), is(true));

        Map<String, Flag<?>> mapFlag = flagSet.getFormalFlags();
        assertThat(mapFlag, is(notNullValue()));
        assertThat(mapFlag.size(), is(1));
        assertThat(flagSet.hasNonHiddenFlags(), is(true));

        Flag<?> flag = mapFlag.get(FlagSet.FLAG_MODULE_PATH);
        assertThat(flag, is(notNullValue()));
        assertThat(flag.getName(), is(FlagSet.FLAG_MODULE_PATH));
        }

    @Test
    public void shouldCreateFlagSetWithHiddenModulePathFlag()
        {
        boolean allowInterspersed = false;
        boolean allowUnknown      = true;
        FlagSet flagSet           = new FlagSet(allowInterspersed, allowUnknown).withModulePath(true);

        assertThat(flagSet.hasFlags(), is(true));

        Map<String, Flag<?>> mapFlag = flagSet.getFormalFlags();
        assertThat(mapFlag, is(notNullValue()));
        assertThat(mapFlag.size(), is(1));
        assertThat(flagSet.hasNonHiddenFlags(), is(false));

        Flag<?> flag = mapFlag.get(FlagSet.FLAG_MODULE_PATH);
        assertThat(flag, is(notNullValue()));
        assertThat(flag.getName(), is(FlagSet.FLAG_MODULE_PATH));
        }

    @Test
    public void shouldCreateFlagSetWithVersionFlag()
        {
        boolean allowInterspersed = false;
        boolean allowUnknown      = true;
        FlagSet flagSet           = new FlagSet(allowInterspersed, allowUnknown).withVersionFlag();

        assertThat(flagSet.hasFlags(), is(true));

        Map<String, Flag<?>> mapFlag = flagSet.getFormalFlags();
        assertThat(mapFlag, is(notNullValue()));
        assertThat(mapFlag.size(), is(1));
        assertThat(flagSet.hasNonHiddenFlags(), is(true));

        Flag<?> flag = mapFlag.get(FlagSet.FLAG_VERSION);
        assertThat(flag, is(notNullValue()));
        assertThat(flag.getName(), is(FlagSet.FLAG_VERSION));
        }

    @Test
    public void shouldCreateFlagSetWithVerboseFlag()
        {
        boolean allowInterspersed = false;
        boolean allowUnknown      = true;
        FlagSet flagSet           = new FlagSet(allowInterspersed, allowUnknown).withVerboseFlag();

        assertThat(flagSet.hasFlags(), is(true));

        Map<String, Flag<?>> mapFlag = flagSet.getFormalFlags();
        assertThat(mapFlag, is(notNullValue()));
        assertThat(mapFlag.size(), is(1));
        assertThat(flagSet.hasNonHiddenFlags(), is(true));

        Flag<?> flag = mapFlag.get(FlagSet.FLAG_VERBOSE);
        assertThat(flag, is(notNullValue()));
        assertThat(flag.getName(), is(FlagSet.FLAG_VERBOSE));
        }

    @Test
    public void shouldReturnNullValueForNonExistentFlag()
        {
        FlagSet flagSet = new FlagSet();
        assertThat(flagSet.get("foo", String.class), is(nullValue()));
        }

    @Test
    public void shouldAddBooleanFlag()
        {
        String  name        = "foo";
        char    shortcut    = 'f';
        String  usage       = "testing...";
        boolean dfltValue   = false;
        boolean noOptValue  = true;
        boolean hidden      = false;
        boolean passThru    = true;
        String passThruName = "foo.pass.thru";

        FlagSet flagSet    = new FlagSet().withBoolean(name, shortcut, usage, dfltValue, noOptValue,
                hidden, passThru,passThruName);

        assertThat(flagSet.hasFlags(), is(true));
        assertThat(flagSet.hasNoArgDefault(name), is(true));

        Map<String, Flag<?>> mapFlag = flagSet.getFormalFlags();
        assertThat(mapFlag, is(notNullValue()));
        assertThat(mapFlag.size(), is(1));
        assertThat(flagSet.hasNonHiddenFlags(), is(true));

        Flag<?> flag = mapFlag.get("foo");
        assertThat(flag, is(notNullValue()));
        assertThat(flag.getName(), is(name));
        assertThat(flag.hasShorthand(), is(true));
        assertThat(flag.getShorthand(), is(shortcut));
        assertThat(flag.getUsage(), is(usage));
        assertThat(flag.getValue(), is(instanceOf(BooleanValue.class)));
        assertThat(flag.getDefaultValue(), is(instanceOf(BooleanValue.class)));
        assertThat(flag.getNoArgDefault(), is(instanceOf(BooleanValue.class)));
        assertThat(flag.isHidden(), is(hidden));
        assertThat(flag.isPassThru(), is(passThru));
        assertThat(flag.getPassThruName(), is(passThruName));

        Map<Character, Flag<?>> mapShorthandFlag = flagSet.getShorthandFlags();
        Flag<?> flagShort = mapShorthandFlag.get('f');
        assertThat(flagShort, is(sameInstance(flagShort)));

        assertThat(flagSet.getBoolean(name), is(false));
        }

    @Test
    public void shouldAddBooleanFlagWithoutValues()
        {
        String  sName   = "foo";
        char    chShort = 'f';
        String  sUsage  = "testing...";
        FlagSet flagSet = new FlagSet().withBoolean(sName, chShort, sUsage);

        assertThat(flagSet.hasFlags(), is(true));
        assertThat(flagSet.hasNoArgDefault(sName), is(true));

        Map<String, Flag<?>> mapFlag = flagSet.getFormalFlags();
        assertThat(mapFlag, is(notNullValue()));
        assertThat(mapFlag.size(), is(1));
        assertThat(flagSet.hasNonHiddenFlags(), is(true));

        Flag<?> flag = mapFlag.get("foo");
        assertThat(flag, is(notNullValue()));
        assertThat(flag.getName(), is(sName));
        assertThat(flag.hasShorthand(), is(true));
        assertThat(flag.getShorthand(), is(chShort));
        assertThat(flag.getUsage(), is(sUsage));
        assertThat(flag.getValue(), is(instanceOf(BooleanValue.class)));
        assertThat(flag.getDefaultValue(), is(instanceOf(BooleanValue.class)));
        assertThat(flag.getNoArgDefault(), is(instanceOf(BooleanValue.class)));

        Map<Character, Flag<?>> mapShorthandFlag = flagSet.getShorthandFlags();
        Flag<?> flagShort = mapShorthandFlag.get('f');
        assertThat(flagShort, is(sameInstance(flagShort)));

        assertThat(flagSet.getBoolean(sName), is(false));
        }

    @Test
    public void shouldAddBooleanFlagWithoutShorthand()
        {
        String  name    = "foo";
        String  usage   = "testing...";
        FlagSet flagSet = new FlagSet().withBoolean(name, usage);

        assertThat(flagSet.hasFlags(), is(true));
        assertThat(flagSet.hasNoArgDefault(name), is(true));

        Map<String, Flag<?>> mapFlag = flagSet.getFormalFlags();
        assertThat(mapFlag, is(notNullValue()));
        assertThat(mapFlag.size(), is(1));
        assertThat(flagSet.hasNonHiddenFlags(), is(true));

        Flag<?> flag = mapFlag.get("foo");
        assertThat(flag, is(notNullValue()));
        assertThat(flag.getName(), is(name));
        assertThat(flag.hasShorthand(), is(false));
        assertThat(flag.getShorthand(), is(Flag.NO_SHORTHAND));
        assertThat(flag.getUsage(), is(usage));
        assertThat(flag.getValue(), is(instanceOf(BooleanValue.class)));
        assertThat(flag.getDefaultValue(), is(instanceOf(BooleanValue.class)));
        assertThat(flag.getNoArgDefault(), is(instanceOf(BooleanValue.class)));

        Map<Character, Flag<?>> mapShorthandFlag = flagSet.getShorthandFlags();
        Flag<?> flagShort = mapShorthandFlag.get('f');
        assertThat(flagShort, is(nullValue()));

        assertThat(flagSet.getBoolean(name), is(false));
        }

    @Test
    public void shouldAddBooleanFlagWithNoArgDefault()
        {
        String  name       = "foo";
        char    shortcut   = 'f';
        String  usage      = "testing...";
        boolean dfltValue  = false;
        boolean noOptValue = false;
        boolean hidden     = false;
        FlagSet flagSet    = new FlagSet().withBoolean(name, shortcut, usage, dfltValue, noOptValue, hidden);

        assertThat(flagSet.hasFlags(), is(true));
        assertThat(flagSet.hasNoArgDefault(name), is(true));

        Map<String, Flag<?>> mapFlag = flagSet.getFormalFlags();
        assertThat(mapFlag, is(notNullValue()));
        assertThat(mapFlag.size(), is(1));
        assertThat(flagSet.hasNonHiddenFlags(), is(true));

        Flag<?> flag = mapFlag.get("foo");
        assertThat(flag, is(notNullValue()));
        assertThat(flag.getName(), is(name));
        assertThat(flag.hasShorthand(), is(true));
        assertThat(flag.getShorthand(), is(shortcut));
        assertThat(flag.getUsage(), is(usage));
        assertThat(flag.getValue(), is(instanceOf(BooleanValue.class)));
        assertThat(flag.getDefaultValue(), is(instanceOf(BooleanValue.class)));
        assertThat(flag.getDefaultValue().get(), is(dfltValue));
        assertThat(flag.getNoArgDefault(), is(instanceOf(BooleanValue.class)));
        assertThat(flag.getNoArgDefault().get(), is(noOptValue));

        assertThat(flagSet.getBoolean(name), is(false));
        }


    @Test
    public void shouldAddFileFlag()
        {
        String  name         = "foo";
        char    shortcut     = 'f';
        String  usage        = "testing...";
        File    dfltValue    = new File("/home");
        boolean hidden       = false;
        boolean passThru     = true;
        String  passThruName = "foo.pass.thru";

        FlagSet flagSet    = new FlagSet().withFile(name, shortcut, usage, dfltValue,
                hidden, passThru,passThruName);

        assertThat(flagSet.hasFlags(), is(true));
        assertThat(flagSet.hasNoArgDefault(name), is(false));

        Map<String, Flag<?>> mapFlag = flagSet.getFormalFlags();
        assertThat(mapFlag, is(notNullValue()));
        assertThat(mapFlag.size(), is(1));
        assertThat(flagSet.hasNonHiddenFlags(), is(true));

        Flag<?> flag = mapFlag.get("foo");
        assertThat(flag, is(notNullValue()));
        assertThat(flag.getName(), is(name));
        assertThat(flag.hasShorthand(), is(true));
        assertThat(flag.getShorthand(), is(shortcut));
        assertThat(flag.getUsage(), is(usage));
        assertThat(flag.getValue(), is(instanceOf(FileValue.class)));
        assertThat(flag.getDefaultValue(), is(instanceOf(FileValue.class)));
        assertThat(flag.getDefaultValue().get(), is(dfltValue));
        assertThat(flag.getNoArgDefault(), is(nullValue()));
        assertThat(flag.isHidden(), is(hidden));
        assertThat(flag.isPassThru(), is(passThru));
        assertThat(flag.getPassThruName(), is(passThruName));

        Map<Character, Flag<?>> mapShorthandFlag = flagSet.getShorthandFlags();
        Flag<?> flagShort = mapShorthandFlag.get('f');
        assertThat(flagShort, is(sameInstance(flagShort)));

        assertThat(flagSet.getFile(name), is(dfltValue));
        }

    @Test
    public void shouldAddFileFlagWithoutValues()
        {
        String  name     = "foo";
        char    shortcut = 'f';
        String  usage    = "testing...";
        FlagSet flagSet  = new FlagSet().withFile(name, shortcut, usage);

        assertThat(flagSet.hasFlags(), is(true));
        assertThat(flagSet.hasNoArgDefault(name), is(false));

        Map<String, Flag<?>> mapFlag = flagSet.getFormalFlags();
        assertThat(mapFlag, is(notNullValue()));
        assertThat(mapFlag.size(), is(1));
        assertThat(flagSet.hasNonHiddenFlags(), is(true));

        Flag<?> flag = mapFlag.get("foo");
        assertThat(flag, is(notNullValue()));
        assertThat(flag.getName(), is(name));
        assertThat(flag.hasShorthand(), is(true));
        assertThat(flag.getShorthand(), is(shortcut));
        assertThat(flag.getUsage(), is(usage));
        assertThat(flag.getValue(), is(instanceOf(FileValue.class)));
        assertThat(flag.getDefaultValue(), is(nullValue()));
        assertThat(flag.getNoArgDefault(), is(nullValue()));

        Map<Character, Flag<?>> mapShorthandFlag = flagSet.getShorthandFlags();
        Flag<?> flagShort = mapShorthandFlag.get('f');
        assertThat(flagShort, is(sameInstance(flagShort)));

        assertThat(flagSet.getFile(name), is(nullValue()));
        }

    @Test
    public void shouldAddFileFlagWithoutShorthand()
        {
        String  name    = "foo";
        String  usage   = "testing...";
        FlagSet flagSet = new FlagSet().withFile(name, usage);

        assertThat(flagSet.hasFlags(), is(true));
        assertThat(flagSet.hasNoArgDefault(name), is(false));

        Map<String, Flag<?>> mapFlag = flagSet.getFormalFlags();
        assertThat(mapFlag, is(notNullValue()));
        assertThat(mapFlag.size(), is(1));
        assertThat(flagSet.hasNonHiddenFlags(), is(true));

        Flag<?> flag = mapFlag.get("foo");
        assertThat(flag, is(notNullValue()));
        assertThat(flag.getName(), is(name));
        assertThat(flag.hasShorthand(), is(false));
        assertThat(flag.getShorthand(), is(Flag.NO_SHORTHAND));
        assertThat(flag.getUsage(), is(usage));
        assertThat(flag.getValue(), is(instanceOf(FileValue.class)));
        assertThat(flag.getDefaultValue(), is(nullValue()));
        assertThat(flag.getNoArgDefault(), is(nullValue()));

        Map<Character, Flag<?>> mapShorthandFlag = flagSet.getShorthandFlags();
        Flag<?> flagShort = mapShorthandFlag.get('f');
        assertThat(flagShort, is(nullValue()));

        assertThat(flagSet.getFile(name), is(nullValue()));
        }

    @Test
    public void shouldAddFileFlagWithDefaultValue()
        {
        String  name       = "foo";
        char    shortcut   = 'f';
        String  usage      = "testing...";
        File    dfltValue  = new File("/home");
        FlagSet flagSet    = new FlagSet().withFile(name, shortcut, usage, dfltValue);

        assertThat(flagSet.hasFlags(), is(true));
        assertThat(flagSet.hasNoArgDefault(name), is(false));

        Map<String, Flag<?>> mapFlag = flagSet.getFormalFlags();
        assertThat(mapFlag, is(notNullValue()));
        assertThat(mapFlag.size(), is(1));
        assertThat(flagSet.hasNonHiddenFlags(), is(true));

        Flag<?> flag = mapFlag.get("foo");
        assertThat(flag, is(notNullValue()));
        assertThat(flag.getName(), is(name));
        assertThat(flag.hasShorthand(), is(true));
        assertThat(flag.getShorthand(), is(shortcut));
        assertThat(flag.getUsage(), is(usage));
        assertThat(flag.getValue(), is(instanceOf(FileValue.class)));
        assertThat(flag.getDefaultValue(), is(instanceOf(FileValue.class)));
        assertThat(flag.getDefaultValue().get(), is(dfltValue));
        assertThat(flag.getNoArgDefault(), is(nullValue()));

        assertThat(flagSet.getFile(name), is(dfltValue));
        }

    @Test
    public void shouldHavePassThruFlags() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withString("one", Flag.NO_SHORTHAND, "testing", null, null, false, true, null)
                .withString("two", Flag.NO_SHORTHAND, "testing", null, null, false, false, null)
                .withString("three", Flag.NO_SHORTHAND, "testing", null, null, false, true, "arg.three");

        flagSet.parse("--one", "value-one", "--two", "value-two", "--three", "value-three");

        Map<String, ?> map = flagSet.getPassThruArguments();
        assertThat(map, is(notNullValue()));
        assertThat(map.get("one"), is("value-one"));         // one is pass-thru
        assertThat(map.get("two"), is(nullValue()));               // two is not pass-thru
        assertThat(map.get("arg.three"), is("value-three")); // three is pass-thru with alternate name
        }

    @Test
    public void shouldHaveStringArrayPassThruFlag() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withStringList("one", Flag.NO_SHORTHAND, "testing", false, true, null)
                .withStringList("two", Flag.NO_SHORTHAND, "testing", false, true, "arg.two");

        flagSet.parse("--one", "value-one-1", "--two", "value-two", "--one", "value-one-2");

        Map<String, ?> map = flagSet.getPassThruArguments();
        assertThat(map, is(notNullValue()));
        assertThat(map.get("one"), is(instanceOf(String[].class)));
        assertThat((String[]) map.get("one"), is(arrayContaining("value-one-1", "value-one-2")));
        assertThat(map.get("arg.two"), is(instanceOf(String[].class)));
        assertThat((String[]) map.get("arg.two"), is(arrayContaining("value-two")));
        }

    @Test
    public void shouldNotUseBlankPassThruName() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withString("one", Flag.NO_SHORTHAND, "testing", null, null, false, true, "")
                .withString("two", Flag.NO_SHORTHAND, "testing", null, null, false, true, " ")
                .withString("three", Flag.NO_SHORTHAND, "testing", null, null, false, true, "\t");

        flagSet.parse("--one", "value-one", "--two", "value-two", "--three", "value-three");

        Map<String, ?> map = flagSet.getPassThruArguments();
        assertThat(map, is(notNullValue()));
        assertThat(map.get("one"), is("value-one"));
        assertThat(map.get("two"), is("value-two"));
        assertThat(map.get("three"), is("value-three"));
        }
    }
