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
package com.hardbacknutter.nevertoomanybooks.entities;

import android.content.Context;
import android.os.Parcel;

import androidx.test.filters.MediumTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.Test;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.database.dao.SeriesDao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@MediumTest
public class SeriesTest
        extends BaseDBTest {

    @Test
    public void parcelling() {
        final Series series = Series.from("test");
        series.setNumber("5");

        final Parcel parcel = Parcel.obtain();
        series.writeToParcel(parcel, series.describeContents());
        parcel.setDataPosition(0);
        final Series pSeries = Series.CREATOR.createFromParcel(parcel);

        assertEquals(pSeries, series);

        assertEquals(pSeries.getId(), series.getId());
        assertEquals(pSeries.getTitle(), series.getTitle());
        assertEquals(pSeries.getNumber(), series.getNumber());
        assertEquals(pSeries.isComplete(), series.isComplete());
    }

    @Test
    public void pruneSeries01List() {
        final Context context = mSl.getLocalizedAppContext();
        final SeriesDao seriesDao = mSl.getSeriesDao();

        final List<Series> list = new ArrayList<>();
        Series series;

        // keep, position 0
        series = Series.from("The series (5)");
        long id0 = seriesDao.fixId(context, series, false, Locale.getDefault());
        if (id0 == 0) {
            id0 = seriesDao.insert(context, series, Locale.getDefault());
        }
        series.setId(100);
        series.setComplete(true);
        list.add(series);

        // discard in favour of position 0 which has a number set
        series = Series.from("The series");
        series.setId(100);
        list.add(series);

        // discard in favour of position 1 (added two below here) which has a number set
        series = Series.from("De reeks");
        series.setId(200);
        list.add(series);

        // discard in favour of position 1 (added one below here) which has a number set
        series = Series.from("De reeks");
        series.setId(200);
        list.add(series);

        // keep, position 1
        series = Series.from("De reeks (1)");
        long id1 = seriesDao.fixId(context, series, false, Locale.getDefault());
        if (id1 == 0) {
            id1 = seriesDao.insert(context, series, Locale.getDefault());
        }
        series.setId(200);
        list.add(series);

        // discard in favour of position 0 where we already had the number "5".
        // Note the difference in 'isComplete' is disregarded (first occurrence 'wins')
        series = Series.from("The series (5)");
        series.setId(100);
        series.setComplete(false);
        list.add(series);

        // keep, position 2. Note duplicate id, but different nr as compared to position 0
        series = Series.from("The series (6)");
        long id2 = seriesDao.fixId(context, series, false, Locale.getDefault());
        if (id2 == 0) {
            id2 = seriesDao.insert(context, series, Locale.getDefault());
        }
        series.setId(100);
        list.add(series);

        final boolean modified = seriesDao.pruneList(context, list, false,
                                                     Locale.getDefault());

        assertTrue(list.toString(), modified);
        assertEquals(list.toString(), 3, list.size());

        assertTrue(id0 > 0);
        assertTrue(id1 > 0);
        assertTrue(id2 > 0);

        series = list.get(0);
        assertEquals(id0, series.getId());
        assertEquals("The series", series.getTitle());
        assertEquals("5", series.getNumber());
        assertTrue(series.isComplete());

        series = list.get(1);
        assertEquals(id1, series.getId());
        assertEquals("De reeks", series.getTitle());
        assertEquals("1", series.getNumber());

        series = list.get(2);
        assertEquals(id2, series.getId());
        assertEquals("The series", series.getTitle());
        assertEquals("6", series.getNumber());
    }
}
