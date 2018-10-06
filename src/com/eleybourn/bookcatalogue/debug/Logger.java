/*
 * @copyright 2011 Evan Leybourn
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue.debug;

import android.annotation.SuppressLint;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.Log;

import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Logger {

    public static final String TAG = "BC Logger";

    private Logger() {
    }

    public static void logError(@NonNull final Exception e) {
        logError(e, "");
    }

    public static void logError(@NonNull final Error e) {
        logError(new RuntimeException(e), "");
    }

    /**
     * Write the exception stacktrace to the error log file
     *
     * @param e       The exception to log
     * @param message extra message (don't pass e.getMessage(), that one is logged automatically)
     */
    public static void logError(@NonNull final Exception e, @NonNull final String message) {
        @SuppressLint("SimpleDateFormat")
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String now = dateFormat.format(new Date());

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        e.printStackTrace(pw);

        String exMsg = e.getMessage();
        String error = "An Exception/Error Occurred @ " + now + "\n" +
                (exMsg != null ? exMsg + "\n" : "") +
                "In Phone " + Build.MODEL + " (" + Build.VERSION.SDK_INT + ") \n" +
                message + "\n" +
                sw;

        try {
            // FIXME Remove Log.e! Replace with ACRA?
            Log.e(TAG, error);
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(StorageUtils.getErrorLog()), "utf8"), 8192);
            out.write(error);
            out.close();
        } catch (Exception ignored) {
            // do nothing - we can't log an error in the error logger. (and we don't want to FC the app)
        }
    }

    /**
     * Clear the error log each time the app is started; preserve previous if non-empty
     */
    public static void clearLog() {
        try {
            try {
                File orig = new File(StorageUtils.getErrorLog());
                if (orig.exists() && orig.length() > 0) {
                    File backup = new File(StorageUtils.getErrorLog() + ".bak");
                    StorageUtils.renameFile(orig, backup);
                }
            } catch (Exception ignore) {
                // Ignore backup failure...
            }
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(StorageUtils.getErrorLog()), "utf8"), 8192);
            out.write("");
            out.close();
        } catch (Exception ignore) {
            // do nothing - we can't log an error in the error logger. (and we don't want to FC the app)
        }
    }
}
