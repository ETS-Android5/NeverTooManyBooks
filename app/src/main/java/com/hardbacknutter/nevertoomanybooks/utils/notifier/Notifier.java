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
package com.hardbacknutter.nevertoomanybooks.utils.notifier;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.app.NotificationCompat;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

public interface Notifier
        extends AppLocale.OnLocaleChangedListener {

    /** Notification id. */
    int ID_GENERIC = 0;

    /** RequestCode. For now, we always use 0. */
    int RC_DEFAULT = 0;

    /**
     * Convenience wrapper which uses some sensible defaults for a generic notification.
     *
     * @param context Current context
     * @param channel to use
     * @param intent  to use for the {@link PendingIntent}
     * @param message to show
     */
    default void send(@NonNull final Context context,
                      @NonNull final Channel channel,
                      @NonNull final Intent intent,
                      @NonNull final CharSequence message) {
        send(context, channel, intent, message,
             android.R.string.dialog_alert_title,
             ID_GENERIC, RC_DEFAULT, false);
    }

    /**
     * Send a notification.
     *
     * @param context          Current context
     * @param channel          to use
     * @param intent           to use for the {@link PendingIntent}
     * @param message          to show
     * @param dialogTitleResId string resource for the notification title
     */
    void send(@NonNull Context context,
              @NonNull Channel channel,
              @NonNull Intent intent,
              @NonNull CharSequence message,
              @StringRes int dialogTitleResId,
              int notificationId,
              int requestCode,
              boolean withParentStack);

    enum Channel {
        Error("Error",
              R.string.notification_channel_error,
              R.drawable.ic_baseline_error_24,
              NotificationManager.IMPORTANCE_HIGH,
              NotificationCompat.PRIORITY_HIGH),
        Warning("Warning",
                R.string.notification_channel_warn,
                R.drawable.ic_baseline_warning_24,
                NotificationManager.IMPORTANCE_DEFAULT,
                NotificationCompat.PRIORITY_DEFAULT),
        Info("Info",
             R.string.notification_channel_info,
             R.drawable.ic_baseline_info_24,
             NotificationManager.IMPORTANCE_LOW,
             NotificationCompat.PRIORITY_LOW);

        @NonNull
        final String id;
        final int stringId;
        final int drawableId;
        final int importance;
        final int priority;

        Channel(@NonNull final String id,
                @StringRes final int stringId,
                @DrawableRes final int drawableId,
                final int importance,
                final int priority) {
            this.id = id;
            this.stringId = stringId;
            this.drawableId = drawableId;
            this.importance = importance;
            this.priority = priority;
        }
    }
}
