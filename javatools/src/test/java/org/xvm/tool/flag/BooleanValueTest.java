package org.xvm.tool.flag;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class BooleanValueTest
    {
    @Test
    public void shouldBeFalseByDefault()
        {
        BooleanValue value = new BooleanValue();
        assertThat(value.get(), is(false));
        }

    @Test
    public void shouldHaveSpecifiedValue()
        {
        BooleanValue value = new BooleanValue(true);
        assertThat(value.get(), is(true));
        }

    @Test
    public void shouldSetValue()
        {
        BooleanValue value = new BooleanValue();
        value.setValue(true);
        assertThat(value.get(), is(true));
        value.setValue(false);
        assertThat(value.get(), is(false));
        }

    @Test
    public void shouldHaveCorrectStringValue()
        {
        BooleanValue valueFalse = new BooleanValue(false);
        assertThat(valueFalse.asString(), is("false"));
        BooleanValue valueTrue = new BooleanValue(true);
        assertThat(valueTrue.asString(), is("true"));
        }

    @Test
    public void shouldSetStringValueToLowercaseTrue()
        {
        BooleanValue value = new BooleanValue();
        value.setString("true");
        }

    @Test
    public void shouldSetStringValueToUppercaseTrue()
        {
        BooleanValue value = new BooleanValue();
        value.setString("TRUE");
        assertThat(value.get(), is(true));
        }

    @Test
    public void shouldSetStringValueToMixedCaseTrue()
        {
        BooleanValue value = new BooleanValue();
        value.setString("TrUe");
        assertThat(value.get(), is(true));
        }

    @Test
    public void shouldSetStringValueToLowercaseYes()
        {
        BooleanValue value = new BooleanValue();
        value.setString("yes");
        assertThat(value.get(), is(true));
        }

    @Test
    public void shouldSetStringValueToUppercaseYes()
        {
        BooleanValue value = new BooleanValue();
        value.setString("YES");
        assertThat(value.get(), is(true));
        }

    @Test
    public void shouldSetStringValueToMixedCaseYes()
        {
        BooleanValue value = new BooleanValue();
        value.setString("YeS");
        assertThat(value.get(), is(true));
        }

    @Test
    public void shouldSetStringValueToUppercaseT()
        {
        BooleanValue value = new BooleanValue();
        value.setString("T");
        assertThat(value.get(), is(true));
        }

    @Test
    public void shouldSetStringValueToLowercaseT()
        {
        BooleanValue value = new BooleanValue();
        value.setString("t");
        assertThat(value.get(), is(true));
        }

    @Test
    public void shouldSetStringValueToUppercaseY()
        {
        BooleanValue value = new BooleanValue();
        value.setString("Y");
        assertThat(value.get(), is(true));
        }

    @Test
    public void shouldSetStringValueToLowercaseY()
        {
        BooleanValue value = new BooleanValue();
        value.setString("y");
        assertThat(value.get(), is(true));
        }

    @Test
    public void shouldSetStringValueToOne()
        {
        BooleanValue value = new BooleanValue();
        value.setString("1");
        assertThat(value.get(), is(true));
        }

    @Test
    public void shouldSetStringValueToLowercaseFalse()
        {
        BooleanValue value = new BooleanValue();
        value.setString("false");
        assertThat(value.get(), is(false));
        }

    @Test
    public void shouldSetStringValueToUppercaseFalse()
        {
        BooleanValue value = new BooleanValue();
        value.setString("FALSE");
        assertThat(value.get(), is(false));
        }

    @Test
    public void shouldSetStringValueToMixedCaseFalse()
        {
        BooleanValue value = new BooleanValue();
        value.setString("FaLsE");
        assertThat(value.get(), is(false));
        }

    @Test
    public void shouldSetStringValueToLowercaseNo()
        {
        BooleanValue value = new BooleanValue();
        value.setString("no");
        assertThat(value.get(), is(false));
        }

    @Test
    public void shouldSetStringValueToUppercaseNo()
        {
        BooleanValue value = new BooleanValue();
        value.setString("NO");
        assertThat(value.get(), is(false));
        }

    @Test
    public void shouldSetStringValueToMixedCaseNo()
        {
        BooleanValue value = new BooleanValue();
        value.setString("No");
        assertThat(value.get(), is(false));
        }

    @Test
    public void shouldSetStringValueToUppercaseF()
        {
        BooleanValue value = new BooleanValue();
        value.setString("F");
        assertThat(value.get(), is(false));
        }

    @Test
    public void shouldSetStringValueLowercaseF()
        {
        BooleanValue value = new BooleanValue();
        value.setString("f");
        assertThat(value.get(), is(false));
        }

    @Test
    public void shouldSetStringValueUppercaseN()
        {
        BooleanValue value = new BooleanValue();
        value.setString("N");
        assertThat(value.get(), is(false));
        }

    @Test
    public void shouldSetStringValueLowercaseN()
        {
        BooleanValue value = new BooleanValue();
        value.setString("n");
        assertThat(value.get(), is(false));
        }

    @Test
    public void shouldSetStringValueToZero()
        {
        BooleanValue value = new BooleanValue();
        value.setString("0");
        assertThat(value.get(), is(false));
        }

    }
