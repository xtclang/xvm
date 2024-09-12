package org.xvm.tool.flag;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class IntValueTest
    {
    @Test
    public void shouldHaveNullValue() 
        {
        IntValue value = new IntValue();
        assertThat(value.asString(), is(nullValue()));
        assertThat(value.get(), is(nullValue()));
        }

    @Test
    public void shouldHaveSpecifiedValue()
        {
        IntValue value = new IntValue(19);
        assertThat(value.get(), is(19));
        }

    @Test
    public void shouldHaveSpecifiedIntValue()
        {
        IntValue value = new IntValue(76);
        assertThat(value.asString(), is("76"));
        }

    @Test
    public void shouldSetValue()
        {
        IntValue value = new IntValue();
        value.setValue(19);
        assertThat(value.get(), is(19));
        }

    @Test
    public void shouldSetStringValue()
        {
        IntValue value = new IntValue();
        value.setString("76");
        assertThat(value.get(), is(76));
        }

    @Test
    public void shouldSetNullStringValue()
        {
        IntValue value = new IntValue();
        value.setString(null);
        assertThat(value.get(), is(nullValue()));
        }

    @Test
    public void shouldSetEmptyStringValue()
        {
        IntValue value = new IntValue();
        value.setString("   ");
        assertThat(value.get(), is(nullValue()));
        }
    }
