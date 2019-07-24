package com.eleybourn.bookcatalogue.dialogs;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

/**
 * The main reason that you need to extend this is because each type of encapsulated item
 * will have its own way of storing a label (to display next to the checkbox).
 * Using .toString() is not really a nice solution, hence... extends this class
 * and implement: String {@link CheckListItem#getLabel}
 *
 * @param <T> type of encapsulated item
 */
public abstract class CheckListItemBase<T>
        implements CheckListItem<T>, Parcelable {

    protected T item;
    private boolean mSelected;

    /**
     * Constructor.
     *
     * @param item     to encapsulate
     * @param selected the current status
     */
    protected CheckListItemBase(@NonNull final T item,
                                final boolean selected) {
        this.item = item;
        mSelected = selected;
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * Subclass must handle the {@link #item}.
     *
     * @param in Parcel to construct the object from
     */
    protected CheckListItemBase(@NonNull final Parcel in) {
        mSelected = in.readInt() != 0;
    }

    /**
     * <br>{@inheritDoc}
     * <br>
     * <p>Subclass must handle the {@link #item}.
     */
    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeInt(mSelected ? 1 : 0);
    }

    @SuppressWarnings("SameReturnValue")
    @Override
    public int describeContents() {
        return 0;
    }

    @NonNull
    @Override
    public T getItem() {
        return item;
    }

    @Override
    public abstract String getLabel(@NonNull final Context context);

    @Override
    public boolean isChecked() {
        return mSelected;
    }

    @Override
    public void setChecked(final boolean selected) {
        mSelected = selected;
    }
}
