package org.xvm.tool.flag;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyIterable.emptyIterable;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class FlagSetParseTest
    {
    @Test
    public void shouldParseFlagSpaceValue() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withString("one", "")
                .withString("two", "");

        flagSet.parse("--one", "value-one");
        assertThat(flagSet.getString("one"), is("value-one"));
        }

    @Test
    public void shouldParseFlagEqualsValue() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withString("one", "")
                .withString("two", "");

        flagSet.parse("--one=value-one");
        assertThat(flagSet.getString("one"), is("value-one"));
        }

    @Test
    public void shouldParseShorthandSpaceValue() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withString("one", 'o', "")
                .withString("two", 't', "");

        flagSet.parse("-o", "value-one");
        assertThat(flagSet.getString("one"), is("value-one"));
        }

    @Test
    public void shouldParseShorthandEqualsValue() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withString("one", 'o', "")
                .withString("two", 't', "");

        flagSet.parse("-o=value-one");
        assertThat(flagSet.getString("one"), is("value-one"));
        }

    @Test
    public void shouldParseShorthandCombinedWithValue() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withString("one", 'o', "")
                .withString("two", 't', "");

        flagSet.parse("-ovalue-one");
        assertThat(flagSet.getString("one"), is("value-one"));
        }

    @Test
    public void shouldParseChainOfShorthandFlags() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withBoolean("one", 'a', "")
                .withBoolean("two", 'b', "")
                .withBoolean("three", 'c', "");

        flagSet.parse("-cab");
        assertThat(flagSet.getBoolean("one"), is(true));
        assertThat(flagSet.getBoolean("two"), is(true));
        assertThat(flagSet.getBoolean("three"), is(true));
        }

    @Test
    public void shouldParseChainOfShorthandFlagsEndingWithSpaceValue() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withBoolean("one", 'a', "")
                .withBoolean("two", 'b', "")
                .withString("three", 'c', "");

        flagSet.parse("-abc", "Foo");
        assertThat(flagSet.getBoolean("one"), is(true));
        assertThat(flagSet.getBoolean("two"), is(true));
        assertThat(flagSet.getString("three"), is("Foo"));
        }

    @Test
    public void shouldParseChainOfShorthandFlagsEndingWithEqualsValue() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withBoolean("one", 'a', "")
                .withBoolean("two", 'b', "")
                .withString("three", 'c', "");

        flagSet.parse("-abc=Foo");
        assertThat(flagSet.getBoolean("one"), is(true));
        assertThat(flagSet.getBoolean("two"), is(true));
        assertThat(flagSet.getString("three"), is("Foo"));
        }

    @Test
    public void shouldParseChainOfShorthandFlagsEndingWithCombinedValue() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withBoolean("one", 'a', "")
                .withBoolean("two", 'b', "")
                .withString("three", 'c', "");

        flagSet.parse("-abcFoo");
        assertThat(flagSet.getBoolean("one"), is(true));
        assertThat(flagSet.getBoolean("two"), is(true));
        assertThat(flagSet.getString("three"), is("Foo"));
        }

    @Test
    public void shouldGetBooleanFlagDefaultValueWhenNotOnCommandLine() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withBoolean("one", 'o', "")
                .withString("two", 't', "");

        flagSet.parse("--two", "foo");
        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("one"), is(false));
        assertThat(flagSet.getBoolean("one"), is(false));
        }

    @Test
    public void shouldParseBooleanFromLongName() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withBoolean("one", 'o', "")
                .withString("two", 't', "");

        flagSet.parse("--one");
        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("one"), is(true));
        assertThat(flagSet.getBoolean("one"), is(true));
        }

    @Test
    public void shouldParseBooleanFromShorthand() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withBoolean("one", 'o', "")
                .withString("two", 't', "");

        flagSet.parse("-o");
        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("one"), is(true));
        assertThat(flagSet.getBoolean("one"), is(true));
        }

    @Test
    public void shouldParseBooleanFromLongNameWithSpaceArg() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withBoolean("one", 'o', "", false)
                .withString("two", 't', "");

        flagSet.parse("--one", "false");
        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("one"), is(true));
        assertThat(flagSet.getBoolean("one"), is(false));
        }

    @Test
    public void shouldParseBooleanFromLongNameWithEqualsArg() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withBoolean("one", 'o', "")
                .withString("two", 't', "");

        flagSet.parse("--one=false");
        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("one"), is(true));
        assertThat(flagSet.getBoolean("one"), is(false));
        }

    @Test
    public void shouldParseBooleanFromShorthandWithSpaceArg() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withBoolean("one", 'o', "", false)
                .withString("two", 't', "");

        flagSet.parse("-o", "false");
        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("one"), is(true));
        assertThat(flagSet.getBoolean("one"), is(false));
        }

    @Test
    public void shouldParseBooleanFromShorthandWithEqualsArg() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withBoolean("one", 'o', "")
                .withString("two", 't', "");

        flagSet.parse("-o=false");
        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("one"), is(true));
        assertThat(flagSet.getBoolean("one"), is(false));
        }

    @Test
    public void shouldParseBooleanFromShorthandWithArg() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withBoolean("one", 'o', "", false)
                .withString("two", 't', "");

        flagSet.parse("-ofalse");
        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("one"), is(true));
        assertThat(flagSet.getBoolean("one"), is(false));
        }

    @Test
    public void shouldGetIntFlagDefaultValueWhenNotOnCommandLine() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withInt("one", 'o', "", 19)
                .withString("two", 't', "");

        flagSet.parse("--two", "foo");
        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("one"), is(false));
        assertThat(flagSet.getInt("one"), is(19));
        }

    @Test
    public void shouldParseIntFromLongName() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withInt("one", 'o', "", 0, 19)
                .withString("two", 't', "");

        flagSet.parse("--one");
        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("one"), is(true));
        assertThat(flagSet.getInt("one"), is(19));
        }


    @Test
    public void shouldParseIntFromShorthand() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withInt("one", 'o', "", 0, 19)
                .withString("two", 't', "");

        flagSet.parse("-o");
        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("one"), is(true));
        assertThat(flagSet.getInt("one"), is(19));
        }

    @Test
    public void shouldParseIntFromLongNameWithSpaceArg() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withInt("one", 'o', "", 100)
                .withString("two", 't', "");

        flagSet.parse("--one", "19");
        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("one"), is(true));
        assertThat(flagSet.getInt("one"), is(19));
        }

    @Test
    public void shouldParseIntFromLongNameWithEqualsArg() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withInt("one", 'o', "", 100)
                .withString("two", 't', "");

        flagSet.parse("--one=123");
        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("one"), is(true));
        assertThat(flagSet.getInt("one"), is(123));
        }

    @Test
    public void shouldParseIntFromShorthandWithSpaceArg() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withInt("one", 'o', "", 76)
                .withString("two", 't', "");

        flagSet.parse("-o", "66");
        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("one"), is(true));
        assertThat(flagSet.getInt("one"), is(66));
        }

    @Test
    public void shouldParseIntFromShorthandWithEqualsArg() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withInt("one", 'o', "", 19)
                .withString("two", 't', "");

        flagSet.parse("-o=20");
        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("one"), is(true));
        assertThat(flagSet.getInt("one"), is(20));
        }

    @Test
    public void shouldParseIntFromShorthandWithArg() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withInt("one", 'o', "", 987)
                .withString("two", 't', "");

        flagSet.parse("-o20");
        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("one"), is(true));
        assertThat(flagSet.getInt("one"), is(20));
        }

    @Test
    public void shouldGetFileFlagDefaultValueWhenNotOnCommandLine() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withFile("one", 'o', "")
                .withString("two", 't', "");

        flagSet.parse("--two", "foo");
        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("one"), is(false));
        assertThat(flagSet.getFile("one"), is(nullValue()));
        }

    @Test
    public void shouldParseFileFromLongNameWithSpaceArg() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withFile("one", 'o', "")
                .withString("two", 't', "");

        flagSet.parse("--one", "/foo/bar");
        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("one"), is(true));
        assertThat(flagSet.getFile("one"), is(new File("/foo/bar")));
        }

    @Test
    public void shouldParseFileFromLongNameWithEqualsArg() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withFile("one", 'o', "")
                .withString("two", 't', "");

        flagSet.parse("--one=/foo/bar");
        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("one"), is(true));
        assertThat(flagSet.getFile("one"), is(new File("/foo/bar")));
        }

    @Test
    public void shouldParseFileFromShorthandWithSpaceArg() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withFile("one", 'o', "")
                .withString("two", 't', "");

        flagSet.parse("-o", "/foo/bar");
        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("one"), is(true));
        assertThat(flagSet.getFile("one"), is(new File("/foo/bar")));
        }

    @Test
    public void shouldParseFileFromShorthandWithEqualsArg() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withFile("one", 'o', "")
                .withString("two", 't', "");

        flagSet.parse("-o=/foo/bar");
        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("one"), is(true));
        assertThat(flagSet.getFile("one"), is(new File("/foo/bar")));
        }

    @Test
    public void shouldParseFileFromShorthandWithArg() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withFile("one", 'o', "")
                .withString("two", 't', "");

        flagSet.parse("-o/foo/bar");
        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("one"), is(true));
        assertThat(flagSet.getFile("one"), is(new File("/foo/bar")));
        }

    @Test
    public void shouldGetFileListFlagWhenNotOnCommandLine() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withFileList("one", 'o', "")
                .withString("two", 't', "");

        flagSet.parse("--two", "foo");
        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("one"), is(false));
        assertThat(flagSet.getFileList("one"), is(emptyIterable()));
        }

    @Test
    public void shouldParseFileListFromLongNameWithSpaceArg() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withFileList("one", 'o', "")
                .withString("two", 't', "");

        flagSet.parse("--one", "/foo/bar");
        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("one"), is(true));
        assertThat(flagSet.getFileList("one"), is(List.of(new File("/foo/bar"))));
        }

    @Test
    public void shouldParseFileListFromLongNameWithEqualsArg() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withFileList("one", 'o', "")
                .withString("two", 't', "");

        flagSet.parse("--one=/foo/bar");
        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("one"), is(true));
        assertThat(flagSet.getFileList("one"), is(List.of(new File("/foo/bar"))));
        }

    @Test
    public void shouldParseFileListFromShorthandWithSpaceArg() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withFileList("one", 'o', "")
                .withString("two", 't', "");

        flagSet.parse("-o", "/foo/bar");
        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("one"), is(true));
        assertThat(flagSet.getFileList("one"), is(List.of(new File("/foo/bar"))));
        }

    @Test
    public void shouldParseFileListFromShorthandWithEqualsArg() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withFileList("one", 'o', "")
                .withString("two", 't', "");

        flagSet.parse("-o=/foo/bar");
        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("one"), is(true));
        assertThat(flagSet.getFileList("one"), is(List.of(new File("/foo/bar"))));
        }

    @Test
    public void shouldParseFileListFromShorthandWithArg() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withFileList("one", 'o', "")
                .withString("two", 't', "");

        flagSet.parse("-o/foo/bar");
        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("one"), is(true));
        assertThat(flagSet.getFileList("one"), is(List.of(new File("/foo/bar"))));
        }

    @Test
    public void shouldParseFileListWhenSpecifiedMultipleTimes() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withFileList("one", 'o', "")
                .withString("two", 't', "");

        flagSet.parse("--one", "/foo/one",
                "--one=/foo/three",
                "-o", "/foo/two",
                "-o=/foo/four",
                "-o/foo/five");

        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("one"), is(true));

        assertThat(flagSet.getFileList("one"), is(containsInAnyOrder(new File("/foo/one"),
                        new File("/foo/two"),
                        new File("/foo/three"),
                        new File("/foo/four"),
                        new File("/foo/five"))));
        }

    @Test
    public void shouldGetFileListFromFileFlag() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withFile("one", 'o', "")
                .withString("two", 't', "");

        flagSet.parse("--one", "/foo/bar");
        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("one"), is(true));
        assertThat(flagSet.getFileList("one"), is(List.of(new File("/foo/bar"))));
        }

    @Test
    public void shouldGetStringFlagDefaultValueWhenNotOnCommandLine() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withString("one", 'o', "", "foo")
                .withString("two", 't', "");

        flagSet.parse("--two", "foo");
        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("one"), is(false));
        assertThat(flagSet.getString("one"), is("foo"));
        }

    @Test
    public void shouldParseStringFromLongName() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withString("one", 'o', "", "", "foo", false, false, null)
                .withString("two", 't', "");

        flagSet.parse("--one");
        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("one"), is(true));
        assertThat(flagSet.getString("one"), is("foo"));
        }


    @Test
    public void shouldParseStringFromShorthand() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withString("one", 'o', "", "", "foo", false)
                .withString("two", 't', "");

        flagSet.parse("-o");
        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("one"), is(true));
        assertThat(flagSet.getString("one"), is("foo"));
        }

    @Test
    public void shouldParseStringFromLongNameWithSpaceArg() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withString("one", 'o', "", "")
                .withString("two", 't', "");

        flagSet.parse("--one", "foo");
        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("one"), is(true));
        assertThat(flagSet.getString("one"), is("foo"));
        }

    @Test
    public void shouldParseStringFromLongNameWithEqualsArg() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withString("one", 'o', "", "")
                .withString("two", 't', "");

        flagSet.parse("--one=foo");
        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("one"), is(true));
        assertThat(flagSet.getString("one"), is("foo"));
        }

    @Test
    public void shouldParseStringFromShorthandWithSpaceArg() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withString("one", 'o', "", "")
                .withString("two", 't', "");

        flagSet.parse("-o", "foo");
        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("one"), is(true));
        assertThat(flagSet.getString("one"), is("foo"));
        }

    @Test
    public void shouldParseStringFromShorthandWithEqualsArg() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withString("one", 'o', "", "")
                .withString("two", 't', "");

        flagSet.parse("-o=foo");
        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("one"), is(true));
        assertThat(flagSet.getString("one"), is("foo"));
        }

    @Test
    public void shouldParseStringFromShorthandWithArg() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withString("one", 'o', "", "")
                .withString("two", 't', "");

        flagSet.parse("-ofoo");
        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("one"), is(true));
        assertThat(flagSet.getString("one"), is("foo"));
        }

    @Test
    public void shouldGetStringListFlagDefaultValueWhenNotOnCommandLine() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withStringList("one", 'o', "")
                .withString("two", 't', "");

        flagSet.parse("--two", "foo");
        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("one"), is(false));
        assertThat(flagSet.getStringList("one"), is(emptyIterable()));
        }

    @Test
    public void shouldParseStringListFromLongNameWithSpaceArg() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withStringList("one", 'o', "")
                .withString("two", 't', "");

        flagSet.parse("--one", "foo");
        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("one"), is(true));
        assertThat(flagSet.getStringList("one"), is(List.of("foo")));
        }

    @Test
    public void shouldParseStringListFromLongNameWithEqualsArg() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withStringList("one", 'o', "")
                .withString("two", 't', "");

        flagSet.parse("--one=foo");
        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("one"), is(true));
        assertThat(flagSet.getStringList("one"), is(List.of("foo")));
        }

    @Test
    public void shouldParseStringListFromShorthandWithSpaceArg() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withStringList("one", 'o', "")
                .withString("two", 't', "");

        flagSet.parse("-o", "foo");
        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("one"), is(true));
        assertThat(flagSet.getStringList("one"), is(List.of("foo")));
        }

    @Test
    public void shouldParseStringListFromShorthandWithEqualsArg() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withStringList("one", 'o', "")
                .withString("two", 't', "");

        flagSet.parse("-o=foo");
        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("one"), is(true));
        assertThat(flagSet.getStringList("one"), is(List.of("foo")));
        }

    @Test
    public void shouldParseStringListFromShorthandWithArg() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withStringList("one", 'o', "")
                .withString("two", 't', "");

        flagSet.parse("-ofoo");
        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("one"), is(true));
        assertThat(flagSet.getStringList("one"), is(List.of("foo")));
        }

    @Test
    public void shouldParseStringListWhenSpecifiedMultipleTimes() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withStringList("one", 'o', "")
                .withString("two", 't', "");

        flagSet.parse("--one", "one", "--one=three", "-o", "two", "-o=four", "-ofive");

        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("one"), is(true));

        assertThat(flagSet.getStringList("one"), containsInAnyOrder("one", "two", "three", "four", "five"));
        }

    @Test
    public void shouldParseModulePathFlagWithLongName() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withModulePath()
                .withString("two", 't', "");

        Path fileTemp = Files.createTempFile("xdk-test", ".xtc");

        flagSet.parse("--module-path", fileTemp.toString());
        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("module-path"), is(true));
        assertThat(flagSet.getModulePath(), is(List.of(fileTemp.toFile())));
        }

    @Test
    public void shouldParseModulePathFlagWithShorthand() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withModulePath()
                .withString("two", 't', "");

        Path fileTemp = Files.createTempFile("xdk-test", ".xtc");

        flagSet.parse("-L", fileTemp.toString());
        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("module-path"), is(true));
        assertThat(flagSet.getModulePath(), is(List.of(fileTemp.toFile())));
        }

    @Test
    public void shouldParseHelpFlagWithLongName() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withHelp()
                .withString("two", 't', "");

        flagSet.parse("--help");
        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("help"), is(true));
        }

    @Test
    public void shouldParseHelpFlagWithShorthand() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withHelp()
                .withString("two", 't', "");

        flagSet.parse("-h");
        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("help"), is(true));
        }

    @Test
    public void shouldParseHelpFlagWithLongNameWhenNotSpecifiedAsFlag()
        {
        FlagSet flagSet = new FlagSet();
        assertThrows(FlagSet.ParseHelpException.class, () -> flagSet.parse("--help"));
        }

    @Test
    public void shouldParseHelpFlagWithShorthandWhenNotSpecifiedAsFlag()
        {
        FlagSet flagSet = new FlagSet();
        assertThrows(FlagSet.ParseHelpException.class, () -> flagSet.parse("-h"));
        }

    @Test
    public void shouldParseVersionFlag() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withVersionFlag()
                .withString("two", 't', "");

        flagSet.parse("--version");
        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("version"), is(true));
        assertThat(flagSet.isShowVersion(), is(true));
        }

    @Test
    public void shouldNotHaveShowVersionFlag() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withString("two", 't', "");

        flagSet.parse("--two", "foo");
        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("version"), is(false));
        assertThat(flagSet.isShowVersion(), is(false));
        }

    @Test
    public void shouldParseVerboseFlagUsingLongName() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withVerboseFlag()
                .withString("two", 't', "");

        flagSet.parse("--verbose");
        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("verbose"), is(true));
        assertThat(flagSet.isVerbose(), is(true));
        }

    @Test
    public void shouldParseVerboseFlagUsingShorthand() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withVerboseFlag()
                .withString("two", 't', "");

        flagSet.parse("-v");
        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("verbose"), is(true));
        assertThat(flagSet.isVerbose(), is(true));
        }

    @Test
    public void shouldNotHaveVerboseFlag() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withString("two", 't', "");

        flagSet.parse("--two", "foo");
        assertThat(flagSet.isParsed(), is(true));
        assertThat(flagSet.getActualFlags().containsKey("verbose"), is(false));
        assertThat(flagSet.isVerbose(), is(false));
        }

    @Test
    public void shouldParseSingleNonFlagArgument() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withString("foo", 'f', "");

        flagSet.parse("one");
        assertThat(flagSet.isParsed(), is(true));

        assertThat(flagSet.getArguments(), is(List.of("one")));
        assertThat(flagSet.getArgumentsBeforeDashDash(), is(List.of("one")));
        assertThat(flagSet.getArgumentsAfterDashDash(), is(emptyIterable()));
        assertThat(flagSet.getArgsLengthAtDash(), is(FlagSet.NO_DASH_DASH));
        }

    @Test
    public void shouldParseMultipleNonFlagArgument() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withString("foo", 'f', "");

        flagSet.parse("one", "two", "three");
        assertThat(flagSet.isParsed(), is(true));

        assertThat(flagSet.getArguments(), is(List.of("one", "two", "three")));
        assertThat(flagSet.getArgumentsBeforeDashDash(), is(List.of("one", "two", "three")));
        assertThat(flagSet.getArgumentsAfterDashDash(), is(emptyIterable()));
        assertThat(flagSet.getArgsLengthAtDash(), is(FlagSet.NO_DASH_DASH));
        }

    @Test
    public void shouldParseFlagAndNonFlagArgument() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withString("foo", 'f', "")
                .withString("bar", 'b', "");

        flagSet.parse("--foo", "foo-value", "one", "two", "--bar", "bar-value", "three");
        assertThat(flagSet.isParsed(), is(true));

        assertThat(flagSet.getArguments(), is(List.of("one", "two", "three")));
        assertThat(flagSet.getArgumentsBeforeDashDash(), is(List.of("one", "two", "three")));
        assertThat(flagSet.getArgumentsAfterDashDash(), is(emptyIterable()));
        assertThat(flagSet.getArgsLengthAtDash(), is(FlagSet.NO_DASH_DASH));
        }

    @Test
    public void shouldParseSingleNonFlagArgumentAfterDoubleDash() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withString("foo", 'f', "");

        flagSet.parse("--", "one");
        assertThat(flagSet.isParsed(), is(true));

        assertThat(flagSet.getArguments(), is(List.of("one")));
        assertThat(flagSet.getArgumentsBeforeDashDash(), is(emptyIterable()));
        assertThat(flagSet.getArgumentsAfterDashDash(), is(List.of("one")));
        assertThat(flagSet.getArgsLengthAtDash(), is(0));
        }

    @Test
    public void shouldParseMultipleNonFlagArgumentAfterDoubleDash() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withString("foo", 'f', "");

        flagSet.parse("--", "one", "two", "three");
        assertThat(flagSet.isParsed(), is(true));

        assertThat(flagSet.getArguments(), is(List.of("one", "two", "three")));
        assertThat(flagSet.getArgumentsBeforeDashDash(), is(emptyIterable()));
        assertThat(flagSet.getArgumentsAfterDashDash(), is(List.of("one", "two", "three")));
        assertThat(flagSet.getArgsLengthAtDash(), is(0));
        }

    @Test
    public void shouldParseFlagAndNonFlagArgumentsAfterDoubleDash() throws Exception
        {
        FlagSet flagSet = new FlagSet()
                .withString("foo", 'f', "")
                .withString("bar", 'b', "");

        flagSet.parse("--foo", "foo-value", "one", "two", "--", "--bar", "bar-value", "three");
        assertThat(flagSet.isParsed(), is(true));

        assertThat(flagSet.getArguments(), is(List.of("one", "two", "--bar", "bar-value", "three")));
        assertThat(flagSet.getArgumentsBeforeDashDash(), is(List.of("one", "two")));
        assertThat(flagSet.getArgumentsAfterDashDash(), is(List.of("--bar", "bar-value", "three")));
        assertThat(flagSet.getArgsLengthAtDash(), is(2));
        }

    }
