package org.xvm.tool.flag;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class StringValueTest
    {
    @Test
    public void shouldHaveNullValue() 
        {
        StringValue value = new StringValue();
        assertThat(value.asString(), is(nullValue()));
        assertThat(value.get(), is(nullValue()));
        }

    @Test
    public void shouldHaveSpecifiedValue()
        {
        StringValue value = new StringValue("foo");
        assertThat(value.get(), is("foo"));
        }

    @Test
    public void shouldHaveSpecifiedStringValue()
        {
        StringValue value = new StringValue("bar");
        assertThat(value.asString(), is("bar"));
        }

    @Test
    public void shouldSetValue()
        {
        StringValue value = new StringValue();
        value.setValue("foo");
        assertThat(value.get(), is("foo"));
        }

    @Test
    public void shouldSetStringValue()
        {
        StringValue value = new StringValue();
        value.setString("foo");
        assertThat(value.get(), is("foo"));
        }

    @Test
    public void shouldWrapString()
        {
        StringValue value = StringValue.valueOrNull("foo");
        assertThat(value.get(), is("foo"));
        }

    @Test
    @SuppressWarnings("ConstantValue")
    public void shouldWrapNullString()
        {
        StringValue value = StringValue.valueOrNull(null);
        assertThat(value, is(nullValue()));
        }

    }
