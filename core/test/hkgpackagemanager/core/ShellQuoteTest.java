package hkgpackagemanager.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ShellQuoteTest {
    @Test public void quotesSingleQuotesAndDollar() {
        assertEquals("'a b'", ShellQuote.quote("a b"));
        assertEquals("'it'\\''s $HOME'", ShellQuote.quote("it's $HOME"));
    }
    @Test(expected = IllegalArgumentException.class) public void refusesNewline() { ShellQuote.quote("a\nb"); }
    @Test(expected = IllegalArgumentException.class) public void refusesCarriageReturn() { ShellQuote.quote("a\rb"); }
    @Test(expected = IllegalArgumentException.class) public void refusesNul() { ShellQuote.quote("a\0b"); }
}
