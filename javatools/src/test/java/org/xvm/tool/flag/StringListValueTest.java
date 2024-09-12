package org.xvm.tool.flag;

import org.junit.jupiter.api.Test;
import org.xvm.util.Handy;

import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyIterable.emptyIterable;

public class StringListValueTest
    {
    @Test
    public void shouldBeEmptyByDefault()
        {
        StringListValue value = new StringListValue();
        assertThat(value.get(), is(emptyIterable()));
        }

    @Test
    public void shouldHaveSpecifiedStringArrayAsValue()
        {
        String          sOne  = "foo";
        String          sTwo  = "bar";
        StringListValue value = new StringListValue(sOne, sTwo);
        assertThat(value.get(), is(List.of(sOne, sTwo)));
        }

    @Test
    public void shouldAppendValues()
        {
        String          sOne  = "foo";
        String          sTwo  = "bar";
        StringListValue value = new StringListValue();
        value.append(sOne);
        assertThat(value.get(), is(List.of(sOne)));
        value.append(sTwo);
        assertThat(value.get(), is(List.of(sOne, sTwo)));
        }

    @Test
    public void shouldHaveEmptyStringValue()
        {
        StringListValue value = new StringListValue();
        assertThat(value.asString(), is(""));
        }

    @Test
    public void shouldHaveStringValueForSingleString()
        {
        String          sOne  = "foo";
        StringListValue value = new StringListValue(sOne);
        assertThat(value.asString(), is(Handy.quotedString(sOne)));
        }

    @Test
    public void shouldHaveStringValueForMultipleStrings()
        {
        String          sOne   = "foo";
        String          sTwo   = "bar";
        String          sThree = "baz";
        StringListValue value  = new StringListValue(sOne, sTwo, sThree);
        assertThat(value.asString(), is(Handy.quotedString(sOne) + ","
                + Handy.quotedString(sTwo) + "," + Handy.quotedString(sThree)));
        }

    @Test
    public void shouldReplaceStrings()
        {
        String          sOne   = "foo";
        String          sTwo   = "bar";
        String          sThree = "baz";
        StringListValue value  = new StringListValue(sOne);
        assertThat(value.get(), is(List.of(sOne)));
        value.replace(new String[]{sTwo, sThree});
        assertThat(value.get(), is(List.of(sTwo, sThree)));
        }

    @Test
    public void shouldSetStringValues()
        {
        String          sOne  = "foo";
        String          sTwo  = "bar";
        StringListValue value = new StringListValue();
        value.setString(sOne);
        assertThat(value.get(), is(List.of(sOne)));
        value.setString(sTwo);
        assertThat(value.get(), is(List.of(sOne, sTwo)));
        }
    }
