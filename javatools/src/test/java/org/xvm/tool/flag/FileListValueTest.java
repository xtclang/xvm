package org.xvm.tool.flag;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyIterable.emptyIterable;

public class FileListValueTest
    {
    @Test
    public void shouldBeEmptyByDefault()
        {
        FileListValue value = new FileListValue();
        assertThat(value.get(), is(emptyIterable()));
        }

    @Test
    public void shouldHaveSpecifiedFileArrayAsValue()
        {
        File          fileOne = new File("foo");
        File          fileTwo = new File("bar");
        FileListValue value   = new FileListValue(fileOne, fileTwo);
        assertThat(value.get(), is(List.of(fileOne, fileTwo)));
        }

    @Test
    public void shouldAppendValues()
        {
        File          fileOne = new File("foo");
        File          fileTwo = new File("bar");
        FileListValue value   = new FileListValue();
        value.append(fileOne.getName());
        assertThat(value.get(), is(List.of(fileOne)));
        value.append(fileTwo.getName());
        assertThat(value.get(), is(List.of(fileOne, fileTwo)));
        }

    @Test
    public void shouldHaveEmptyStringValue()
        {
        FileListValue value = new FileListValue();
        assertThat(value.asString(), is(""));
        }

    @Test
    public void shouldHaveStringValueForSingleFile()
        {
        File          fileOne = new File("foo");
        FileListValue value   = new FileListValue(fileOne);
        assertThat(value.asString(), is(fileOne.getName()));
        }

    @Test
    public void shouldHaveStringValueForMultipleFiles()
        {
        File          fileOne   = new File("foo");
        File          fileTwo   = new File("bar");
        File          fileThree = new File("baz");
        FileListValue value     = new FileListValue(fileOne, fileTwo, fileThree);
        assertThat(value.asString(), is(fileOne.getName() + "," + fileTwo.getName() + "," + fileThree.getName()));
        }

    @Test
    public void shouldReplaceFiles()
        {
        File          fileOne   = new File("foo");
        File          fileTwo   = new File("bar");
        File          fileThree = new File("baz");
        FileListValue value     = new FileListValue(fileOne);
        assertThat(value.get(), is(List.of(fileOne)));
        value.replace(new String[]{fileTwo.getName(), fileThree.getName()});
        assertThat(value.get(), is(List.of(fileTwo, fileThree)));
        }

    @Test
    public void shouldSetStringValues()
        {
        File          fileOne = new File("foo");
        File          fileTwo = new File("bar");
        FileListValue value   = new FileListValue();
        value.setString(fileOne.getName());
        assertThat(value.get(), is(List.of(fileOne)));
        value.setString(fileTwo.getName());
        assertThat(value.get(), is(List.of(fileOne, fileTwo)));
        }

    @Test
    public void shouldSetPathDelimitedStringValue()
        {
        File          fileOne = new File("foo");
        File          fileTwo = new File("bar");
        FileListValue value   = new FileListValue();
        value.setString(fileOne.getName() + File.pathSeparator + fileTwo.getName());
        assertThat(value.get(), is(List.of(fileOne, fileTwo)));
        }

    @Test
    public void shouldSetFileUnderHomeDirectory()
        {
        String        sHome = System.getProperty("user.home");
        FileListValue value = new FileListValue();
        value.setString("~" + File.separator + "foo.txt");
        assertThat(value.get(), is(List.of(new File(sHome + File.separator + "foo.txt"))));
        }

    @Test
    public void shouldAddWildCardFiles() throws Exception
        {
        FileListValue value        = new FileListValue();
        List<File>    listExpected = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get("."), "*.*"))
            {
            stream.forEach(path -> listExpected.add(path.toFile()));
            }

        value.setString("*.*");
        assertThat(value.get(), is(listExpected));
        }
    }
