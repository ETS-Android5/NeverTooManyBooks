/*
 * @Copyright 2018-2021 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertoomanybooks.utils;

import java.math.BigDecimal;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import com.hardbacknutter.nevertoomanybooks.Base;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MoneyTest
        extends Base {

    private static final BigDecimal twelveDotThreeFour = BigDecimal.valueOf(12.34d);

    @Test
    void uk00() {
        final Money m = new Money(Locale.UK, "GBP&nbsp;12.34");
        assertEquals(twelveDotThreeFour, m.getValue());
        assertEquals(Money.GBP, m.getCurrencyCode());
    }

    @Test
    void uk01() {
        final Money m = new Money(Locale.UK, "£ 12.34");
        assertEquals(twelveDotThreeFour, m.getValue());
        assertEquals(Money.GBP, m.getCurrencyCode());
    }

    @Test
    void uk02() {
        final Money m = new Money(Locale.UK, "£12.34");
        assertEquals(twelveDotThreeFour, m.getValue());
        assertEquals(Money.GBP, m.getCurrencyCode());
    }

    @Test
    void uk03() {
        final Money m = new Money(Locale.UK, "GBP12.34");
        assertEquals(twelveDotThreeFour, m.getValue());
        assertEquals(Money.GBP, m.getCurrencyCode());
    }

    @Test
    void uk04() {
        final Money m = new Money(Locale.UK, "£12");
        assertEquals(BigDecimal.valueOf(12.0d), m.getValue());
        assertEquals(Money.GBP, m.getCurrencyCode());
    }


    @Test
    void fr01() {
        final Money m = new Money(Locale.FRANCE, "12,34&nbsp;€");
        assertEquals(twelveDotThreeFour, m.getValue());
        assertEquals(Money.EUR, m.getCurrencyCode());
    }

    @Test
    void fr02() {
        final Money m = new Money(Locale.FRANCE, "12,34 €");
        assertEquals(twelveDotThreeFour, m.getValue());
        assertEquals(Money.EUR, m.getCurrencyCode());
    }

    @Test
    void fr03() {
        final Money m = new Money(Locale.FRANCE, "12,34€");
        assertEquals(twelveDotThreeFour, m.getValue());
        assertEquals(Money.EUR, m.getCurrencyCode());
    }

    @Test
    void fr04() {
        final Money m = new Money(Locale.FRANCE, "12,34 eur");
        assertEquals(twelveDotThreeFour, m.getValue());
        assertEquals(Money.EUR, m.getCurrencyCode());
    }
}
