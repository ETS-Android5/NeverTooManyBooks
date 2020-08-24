/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.goodreads;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.BooksOnBookshelf;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.utils.Notifier;

/**
 * Trivial Activity to handle the callback URI.
 */
public class GoodreadsAuthorizationActivity
        extends BaseActivity {

    @SuppressLint("SyntheticAccessor")
    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Uri uri = getIntent().getData();
        if (uri != null) {
            // Goodreads does not set the verifier...but we may as well check for it.
            // The verifier was added in version 1.0A, and Goodreads seems to implement 1.0.
            //String verifier = uri.getQueryParameter("oauth_verifier");

            // Handle the auth response by passing it off to a background task to check.
            new AuthorizationResultCheckTask().execute();
        }

        // Bring our app back to the top
        final Intent intent = new Intent(this, BooksOnBookshelf.class);
        startActivity(intent);
        finish();
    }

    /**
     * Simple task to verify Goodreads credentials and  display a notification with the result.
     * <p>
     * This task is run as the last part of the Goodreads auth process.
     * Runs in background because it can take several seconds.
     */
    private static class AuthorizationResultCheckTask
            extends AsyncTask<Void, Void, Boolean> {

        /** Log tag. */
        private static final String TAG = "GR.AuthResultCheckTask";

        @Nullable
        private Exception mException;

        @Override
        protected Boolean doInBackground(@Nullable final Void... voids) {
            Thread.currentThread().setName(TAG);
            final Context context = App.getTaskContext();

            final GoodreadsAuth grAuth = new GoodreadsAuth(context);
            try {
                return grAuth.handleAuthenticationAfterAuthorization(context);

            } catch (@NonNull final GoodreadsAuth.AuthorizationException | IOException e) {
                mException = e;
                return false;
            }
        }

        @Override
        protected void onPostExecute(@NonNull final Boolean result) {
            final Context context = LocaleUtils.applyLocale(App.getAppContext());

            if (result) {
                final PendingIntent pendingIntent = Notifier
                        .createPendingIntent(context, BooksOnBookshelf.class);
                Notifier.getInstance(context)
                        .sendInfo(context, Notifier.ID_GOODREADS, pendingIntent,
                                  R.string.info_authorized,
                                  context.getString(R.string.info_site_authorization_successful,
                                                    context.getString(R.string.site_goodreads)));

            } else {
                final String msg;
                if (mException != null) {
                    msg = context.getString(R.string.error_site_authorization_failed,
                                            context.getString(R.string.site_goodreads)) + ' '
                          + context.getString(R.string.error_if_the_problem_persists,
                                              context.getString(R.string.lbl_send_debug));
                } else {
                    msg = context.getString(R.string.error_site_authentication_failed,
                                            context.getString(R.string.site_goodreads));
                }


                final PendingIntent pendingIntent = Notifier
                        .createPendingIntentWithParentStack(context,
                                                            GoodreadsRegistrationActivity.class);
                Notifier.getInstance(context)
                        .sendError(context, Notifier.ID_GOODREADS, pendingIntent,
                                   R.string.info_not_authorized,
                                   msg);
            }
        }
    }
}
