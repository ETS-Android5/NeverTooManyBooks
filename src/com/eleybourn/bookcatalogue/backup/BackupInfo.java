/*
 * @copyright 2013 Philip Warner
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
package com.eleybourn.bookcatalogue.backup;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.utils.DateUtils;

import java.util.Date;

/**
 * Class to encapsulate the INFO block from an archive
 *
 * @author pjw
 */
public class BackupInfo {
    /** Standard INFO item */
    private static final String INFO_ARCHVERSION = "ArchVersion";
    /** Standard INFO item */
    private static final String INFO_CREATEDATE = "CreateDate";
    /** Standard INFO item */
    private static final String INFO_NUMBOOKS = "NumBooks";
    /** Standard INFO item */
    private static final String INFO_NUMCOVERS = "NumCovers";
    /** Standard INFO item */
    private static final String INFO_APPPACKAGE = "AppPackage";
    /** Standard INFO item */
    private static final String INFO_APPVERSIONNAME = "AppVersionName";
    /** Standard INFO item */
    private static final String INFO_APPVERSIONCODE = "AppVersionCode";
    /** Standard INFO item */
    private static final String INFO_SDK = "SDK";
    /** Standard INFO item */
    private static final String INFO_COMPATARCHIVER = "CompatArchiver";
    /** Standard INFO item */
    private static final String INFO_HAS_BOOKS = "HasBooks";
    /** Standard INFO item */
    private static final String INFO_HAS_COVERS = "HasCovers";
    /** Standard INFO item */
    private static final String INFO_HAS_DATABASE = "HasDatabase";
    /** Standard INFO item */
    private static final String INFO_HAS_SETTINGS = "HasSettings";
    /** Standard INFO item */
    private static final String INFO_HAS_BOOKLIST_STYLES = "HasBooklistStyles";
    /** Bundle retrieved from the archive for this instance */
    @NonNull
    private final Bundle mBundle;


    public BackupInfo(@NonNull final Bundle b) {
        mBundle = b;
    }

    /**
     * Static method to create an INFO block based on the current environment.
     *
     * @param container The container being used (we want the version)
     * @param context   Context (for package-related info)
     *
     * @return a new BackupInfo object
     */
    @NonNull
    static BackupInfo createInfo(@NonNull final BackupContainer container,
                                 @NonNull final Context context,
                                 final int bookCount,
                                 final int coverCount) {
        Bundle info = new Bundle();

        info.putInt(INFO_ARCHVERSION, container.getVersion());
        info.putInt(INFO_COMPATARCHIVER, 1);
        info.putString(INFO_CREATEDATE, DateUtils.toSqlDateTime(new Date()));
        info.putInt(INFO_NUMBOOKS, bookCount);
        info.putInt(INFO_NUMCOVERS, coverCount);
        try {
            // Get app info
            PackageManager manager = context.getPackageManager();
            PackageInfo appInfo = manager.getPackageInfo(context.getPackageName(), 0);
            info.putString(INFO_APPPACKAGE, appInfo.packageName);
            info.putString(INFO_APPVERSIONNAME, appInfo.versionName);
            info.putInt(INFO_APPVERSIONCODE, appInfo.versionCode);
        } catch (Exception e1) {
            // Not much we can do inside error logger...
        }
        info.putInt(INFO_SDK, Build.VERSION.SDK_INT);
        return new BackupInfo(info);
    }

    @NonNull
    public Bundle getBundle() {
        return mBundle;
    }

    public int getArchVersion() {
        return mBundle.getInt(INFO_ARCHVERSION);
    }

    public int getCompatArchiver() {
        return mBundle.getInt(INFO_COMPATARCHIVER);
    }

    @Nullable
    public Date getCreateDate() {
        return DateUtils.parseDate(mBundle.getString(INFO_CREATEDATE));
    }

    public int getNumBooks() {
        return mBundle.getInt(INFO_NUMBOOKS);
    }

    @Nullable
    public String getAppPackage() {
        return mBundle.getString(INFO_APPPACKAGE);
    }

    @Nullable
    public String getAppVersionName() {
        return mBundle.getString(INFO_APPVERSIONNAME);
    }

    public int getAppVersionCode() {
        return mBundle.getInt(INFO_APPVERSIONCODE);
    }

    public int getSdk() {
        return mBundle.getInt(INFO_APPVERSIONCODE);
    }

    public boolean hasBooks() {
        return mBundle.getBoolean(INFO_HAS_BOOKS);
    }

    public boolean hasCoverCount() {
        return mBundle.containsKey(INFO_NUMCOVERS);
    }

    public boolean hasCovers() {
        return mBundle.getBoolean(INFO_HAS_COVERS);
    }

    public boolean hasDatabase() {
        return mBundle.getBoolean(INFO_HAS_DATABASE);
    }

    public boolean hasPreferences() {
        return mBundle.getBoolean(INFO_HAS_SETTINGS);
    }

    public boolean hasBooklistStyles() {
        return mBundle.getBoolean(INFO_HAS_BOOKLIST_STYLES);
    }

    public int getBookCount() {
        return mBundle.getInt(INFO_NUMBOOKS);
    }

    public int getCoverCount() {
        return mBundle.getInt(INFO_NUMCOVERS);
    }

}
