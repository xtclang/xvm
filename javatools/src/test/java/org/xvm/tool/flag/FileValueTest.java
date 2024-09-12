package org.xvm.tool.flag;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class FileValueTest
    {
    @Test
    public void shouldBeEmptyByDefault()
        {
        FileValue value = new FileValue();
        assertThat(value.get(), is(nullValue()));
        }

    @Test
    public void shouldHaveSpecifiedFileArrayAsValue()
        {
        File      file  = new File("foo");
        FileValue value = new FileValue(file);
        assertThat(value.get(), is(file));
        }

    @Test
    public void shouldHaveNullStringValue()
        {
        FileValue value = new FileValue();
        assertThat(value.asString(), is(nullValue()));
        }

    @Test
    public void shouldHaveStringValueForFile()
        {
        File      fileOne = new File("foo");
        FileValue value   = new FileValue(fileOne);
        assertThat(value.asString(), is(fileOne.getName()));
        }

    @Test
    public void shouldSetStringValues()
        {
        File      fileOne = new File("foo");
        File      fileTwo = new File("bar");
        FileValue value   = new FileValue();
        value.setString(fileOne.getName());
        assertThat(value.get(), is(fileOne));
        value.setString(fileTwo.getName());
        assertThat(value.get(), is(fileTwo));
        }

    @Test
    public void shouldNotAllowPathDelimitedStringValue()
        {
        File      fileOne = new File("foo");
        File      fileTwo = new File("bar");
        FileValue value   = new FileValue();
        assertThrows(IllegalArgumentException.class,
                () -> value.setString(fileOne.getName() + File.pathSeparator + fileTwo.getName()));
        }

    @Test
    public void shouldSetFileUnderHomeDirectory()
        {
        String    home  = System.getProperty("user.home");
        FileValue value = new FileValue();
        value.setString("~" + File.separator + "foo.txt");
        assertThat(value.get(), is(new File(home + File.separator + "foo.txt")));
        }
    }
