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

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import androidx.annotation.NonNull;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * A trivial wrapper for {@link PackageInfoWrapper} to hide the different API versions
 * from the rest of the application.
 */
public final class PackageInfoWrapper {

    @NonNull
    private final PackageInfo mInfo;

    /**
     * Private constructor. Use the factory methods instead.
     *
     * @param context Current context
     * @param flags   Additional option flags to modify the data returned.
     */
    private PackageInfoWrapper(@NonNull final Context context,
                               final int flags) {
        try {
            mInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), flags);
        } catch (@NonNull final PackageManager.NameNotFoundException ignore) {
            throw new IllegalStateException("no PackageManager?");
        }
    }

    /**
     * Constructor.
     *
     * @param context Current context
     *
     * @return instance
     */
    public static PackageInfoWrapper create(@NonNull final Context context) {
        return new PackageInfoWrapper(context, 0);
    }

    /**
     * Constructor.
     *
     * @param context Current context
     *
     * @return instance with signing certificates loaded
     */
    public static PackageInfoWrapper createWithSignatures(@NonNull final Context context) {
        final PackageInfoWrapper info;
        if (Build.VERSION.SDK_INT >= 28) {
            info = new PackageInfoWrapper(context, PackageManager.GET_SIGNING_CERTIFICATES);
        } else {
            info = new PackageInfoWrapper(context, PackageManager.GET_SIGNATURES);
        }
        return info;
    }

    /**
     * The name of this package. From the <manifest> tag's "name" attribute.
     *
     * @return name
     */
    @NonNull
    public String getPackageName() {
        return mInfo.packageName;
    }

    /**
     * Reads the application version from the manifest.
     *
     * @return the version
     */
    public long getVersionCode() {
        if (Build.VERSION.SDK_INT >= 28) {
            return mInfo.getLongVersionCode();
        } else {
            //noinspection deprecation
            return mInfo.versionCode;
        }
    }

    /**
     * Reads the application version from the manifest.
     *
     * @return the version
     */
    @NonNull
    public String getVersionName() {
        return mInfo.versionName;
    }

    /**
     * Return the SHA256 hash of the public key that signed this app, or a useful
     * text message if an error or other problem occurred.
     *
     * <pre>
     *     {@code
     *     keytool -list -keystore myKeyStore.jks -storepass myPassword -v
     *      ...
     *      Certificate fingerprints:
     *          ...
     *          SHA256: D4:98:1C:F7:...    <= this one
     *     }
     * </pre>
     */
    @NonNull
    public String getSignedBy() {
        if (mInfo.signatures == null) {
            return "Not signed, likely development version";
        }

        final StringBuilder signedBy = new StringBuilder();
        try {
            // concat the signature chain.
            for (final Signature sig : mInfo.signatures) {
                if (sig != null) {
                    final MessageDigest md = MessageDigest.getInstance("SHA256");
                    final byte[] publicKey = md.digest(sig.toByteArray());
                    // Turn the hex bytes into a more traditional string representation.
                    final StringBuilder hexString = new StringBuilder();
                    boolean first = true;
                    for (final byte aPublicKey : publicKey) {
                        if (first) {
                            first = false;
                        } else {
                            hexString.append(':');
                        }
                        final String byteString = Integer.toHexString(0xFF & aPublicKey);
                        if (byteString.length() == 1) {
                            hexString.append('0');
                        }
                        hexString.append(byteString);
                    }
                    final String fingerprint = hexString.toString();

                    if (signedBy.length() == 0) {
                        signedBy.append(fingerprint);
                    } else {
                        signedBy.append('/').append(fingerprint);
                    }
                }
            }
        } catch (@NonNull final NoSuchAlgorithmException | RuntimeException e) {
            return e.toString();
        }
        return signedBy.toString();
    }
}
