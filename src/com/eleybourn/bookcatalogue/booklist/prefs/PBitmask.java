package com.eleybourn.bookcatalogue.booklist.prefs;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Set;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * Used for {@link androidx.preference.MultiSelectListPreference}
 * <p>
 * We basically want a bitmask/int.
 * But the Preference insists on a Set<String>
 */
public class PBitmask
        extends PPrefBase<Integer>
        implements PInt {

    /**
     * Constructor. Uses the global setting as the default value,
     * or the passed default if no global default.
     *
     * @param key          of the preference
     * @param uuid         the style id
     * @param defaultValue default to use if there is no global default
     */
    public PBitmask(@NonNull final String key,
                    @NonNull final String uuid,
                    final int defaultValue) {
        super(key, uuid, App.getMultiSelectListPreference(key, defaultValue));
    }

    /**
     * converts the Integer bitmask and stores it as a Set<String>
     */
    @Override
    public void set(@Nullable final Integer value) {
        if (mUuid.isEmpty()) {
            mNonPersistedValue = value;
        } else if (value == null) {
            App.getPrefs(mUuid).edit().remove(getKey()).apply();
        } else {
            App.getPrefs(mUuid).edit()
               .putStringSet(getKey(), Utils.toStringSet(value)).apply();
        }
    }

    /**
     * converts the Integer bitmask and stores it as a Set<String>
     */
    @Override
    public void set(@NonNull final SharedPreferences.Editor ed,
                    @Nullable final Integer value) {
        if (value != null) {
            ed.putStringSet(getKey(), Utils.toStringSet(value));
        } else {
            ed.remove(getKey());
        }
    }

    /**
     * Reads a Set<String> from storage, and converts it to an Integer bitmask.
     */
    @NonNull
    @Override
    public Integer get() {
        if (mUuid.isEmpty()) {
            return mNonPersistedValue != null ? mNonPersistedValue : mDefaultValue;
        } else {
            Set<String> sValue = App.getPrefs(mUuid).getStringSet(getKey(), null);
            if (sValue == null || sValue.isEmpty()) {
                return mDefaultValue;
            }
            return Utils.toInteger(sValue);
        }
    }

    @Override
    @NonNull
    public String toString() {
        return "PBitmask{" + super.toString()
                + ",value=`" + Utils.toStringSet(get()) + '`'
                + '}';
    }
}
