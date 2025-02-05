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
package com.hardbacknutter.nevertoomanybooks.tasks;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import java.util.Optional;

/**
 * Prevent acting twice on a delivered {@link LiveData} event.
 * <p>
 * See <a href="https://medium.com/androiddevelopers/ac2622673150">this Medium post</a>
 */
public class LiveDataEvent<T> {

    @NonNull
    private final T mData;
    private boolean mHasBeenHandled;

    public LiveDataEvent(@NonNull final T data) {
        mData = data;
    }

    /**
     * Get the payload.
     * <p>
     * This method will return a {@code Optional.of(data)} the first time it's called.
     * Any subsequent calls will return an {@code Optional.empty()}.
     *
     * @return data as an Optional
     */
    @NonNull
    public Optional<T> getData() {
        if (mHasBeenHandled) {
            return Optional.empty();
        } else {
            mHasBeenHandled = true;
            return Optional.of(mData);
        }
    }

    @Override
    @NonNull
    public String toString() {
        return "LiveDataEvent{" +
               "mHasBeenHandled=" + mHasBeenHandled +
               ", mData=" + mData +
               '}';
    }
}
