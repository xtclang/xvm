package org.xvm.tool.flag;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class StringMapValueTest
    {
    @Test
    public void shouldBeEmptyByDefault()
        {
        StringMapValue value = new StringMapValue();
        assertThat(value.get(), is(Collections.emptyMap()));
        }

    @Test
    public void shouldCreateWithInitialMapValue()
        {
        String              keyOne    = "one";
        String              valueOne  = "value-one";
        String              keyTwo    = "two";
        String              valueTwo  = "value-two";
        Map<String, String> map       = Map.of(keyOne, valueOne, keyTwo, valueTwo);
        StringMapValue value = new StringMapValue(map);
        assertThat(value.get(), is(map));
        }

    @Test
    public void shouldAppendValues()
        {
        StringMapValue value = new StringMapValue();
        value.append("one=value-one");
        assertThat(value.get(), is(Map.of("one", "value-one")));
        value.append("two=value-two");
        assertThat(value.get(), is(Map.of("one", "value-one", "two", "value-two")));
        }

    @Test
    public void shouldHaveEmptyStringValue()
        {
        StringMapValue value = new StringMapValue();
        assertThat(value.asString(), is(""));
        }

    @Test
    public void shouldReplaceStrings()
        {
        StringMapValue value  = new StringMapValue(Map.of("one", "value-one"));
        assertThat(value.get(), is(Map.of("one", "value-one")));
        value.replace(new String[]{"two=value-two", "three=value-three"});
        assertThat(value.get(), is(Map.of("two", "value-two", "three", "value-three")));
        }

    @Test
    public void shouldSetStringValues()
        {
        StringMapValue value = new StringMapValue();
        value.setString("one=value-one");
        assertThat(value.get(), is(Map.of("one", "value-one")));
        value.setString("two=value-two");
        assertThat(value.get(), is(Map.of("one", "value-one", "two", "value-two")));
        }    }
