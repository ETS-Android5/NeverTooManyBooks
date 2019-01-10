package com.eleybourn.bookcatalogue.booklist.prefs;

import android.content.SharedPreferences;
import android.os.Parcel;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.utils.Prefs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

/**
 * Base class for a generic Preference
 *
 * @param <T> type of the value to store
 */
public abstract class PPrefBase<T>
    implements PPref<T> {

    /**
     * Copy of the style uuid this Preference belongs to.
     * Convenience only and not locally preserved.
     * Must be set in the constructor.
     */
    @Nullable
    protected final String uuid;
    /** key for the Preference */
    @StringRes
    private final int key;
    /** in memory value used for non-persistence situations */
    @Nullable
    protected T nonPersistedValue;
    /** in-memory default to use when value==null, or when the backend does not contain the key */
    @NonNull
    protected T defaultValue;

    /**
     * @param key          key of preference
     * @param uuid         of the style
     * @param defaultValue in memory default
     */
    PPrefBase(@StringRes final int key,
              @Nullable final String uuid,
              @NonNull final T defaultValue) {
        this.key = key;
        this.uuid = uuid;
        this.defaultValue = defaultValue;
    }

    @NonNull
    @Override
    public String getKey() {
        return BookCatalogueApp.getResourceString(key);
    }

    @Override
    public void remove() {
        if (uuid != null) {
            Prefs.getPrefs(uuid).edit().remove(getKey()).apply();
        }
    }

    /**
     * for single pref updated
     *
     * Stores the value as a String
     */
    @Override
    public void set(@Nullable final T value) {
        if (uuid == null) {
            nonPersistedValue = value;
        } else if (value == null) {
            Prefs.getPrefs(uuid).edit().remove(getKey()).apply();
        } else {
            Prefs.getPrefs(uuid).edit().putString(getKey(), String.valueOf(value)).apply();
        }
    }

    /**
     * for batch pref updated
     *
     * Stores the value as a String
     */
    @Override
    public void set(@NonNull final SharedPreferences.Editor ed,
                    @NonNull final T value) {
        if (value == null) {
            ed.remove(getKey());
        } else {
            ed.putString(getKey(), value.toString());
        }
    }

    @Override
    public void set(@NonNull final Parcel in) {
        //noinspection unchecked
        T tmp = (T) in.readValue(getClass().getClassLoader());
        if (tmp != null) {
            set(tmp);
        }
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest) {
        if (uuid == null) {
            // builtin ? then write the in-memory value to the parcel
            // do NOT use 'get' as that would return the default if the actual value is not set.
            dest.writeValue(nonPersistedValue);
        } else {
            // write the actual value, this could be the default if we have no value, but that
            // is what we want for user-defined styles anyhow.
            dest.writeValue(get());
        }
    }

    @Override
    public String toString() {
        return "PPrefBase{" +
            "key=" + BookCatalogueApp.getResourceString(key) +
            ", type=" + defaultValue.getClass().getSimpleName() +
            ", defaultValue=`" + defaultValue + '`' +
            ", nonPersistedValue=`" + nonPersistedValue + '`' +
            '}';
    }
}
