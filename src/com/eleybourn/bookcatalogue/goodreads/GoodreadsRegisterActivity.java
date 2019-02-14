/*
 * @copyright 2012 Philip Warner
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

package com.eleybourn.bookcatalogue.goodreads;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.tasks.TaskWithProgress;
import com.eleybourn.bookcatalogue.utils.AuthorizationException;

import java.io.IOException;

/**
 * Activity to allow the user to authorize the application to access their Goodreads account and
 * to explain Goodreads.
 *
 * @author Philip Warner
 */
public class GoodreadsRegisterActivity
        extends BaseActivity {

    /**
     * Called by button click to start a non-UI-thread task to do the work.
     */
    public static void requestAuthorizationInBackground(@NonNull final FragmentActivity activity) {
        new RequestAuthTask(activity).execute();
    }

    @Override
    protected int getLayoutId() {
        return R.layout.goodreads_register;
    }

    @Override
    @CallSuper
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.goodreads);

        // GR Reg Link
        TextView register = findViewById(R.id.goodreads_url);
        register.setText(GoodreadsManager.WEBSITE);
        register.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                String url = GoodreadsManager.WEBSITE;
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            }
        });

        // Auth button
        findViewById(R.id.authorize).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                requestAuthorizationInBackground(GoodreadsRegisterActivity.this);
            }
        });

        // Forget credentials
        View blurb = findViewById(R.id.forget_blurb);
        View blurbButton = findViewById(R.id.btn_forget_credentials);
        if (GoodreadsManager.hasCredentials()) {
            blurb.setVisibility(View.VISIBLE);
            blurbButton.setVisibility(View.VISIBLE);
            blurbButton.setOnClickListener(new OnClickListener() {

                @Override
                public void onClick(@NonNull final View v) {
                    GoodreadsManager.forgetCredentials();
                }
            });
        } else {
            blurb.setVisibility(View.GONE);
            blurbButton.setVisibility(View.GONE);
        }
    }

    private static class RequestAuthTask
            extends TaskWithProgress<Integer> {

        RequestAuthTask(@NonNull final FragmentActivity context) {
            super(0, context, R.string.progress_msg_connecting_to_web_site, true);
        }

        @Override
        @Nullable
        protected Integer doInBackground(final Void... params) {
            GoodreadsManager grMgr = new GoodreadsManager();
            // This next step can take several seconds....
            if (!grMgr.hasValidCredentials()) {
                try {
                    grMgr.requestAuthorization(mFragment.requireContext());
                } catch (IOException e) {
                    Logger.error(e);
                    return R.string.gr_access_error;
                } catch (AuthorizationException e) {
                    return R.string.error_authorization_failed;
                }
            } else {
                return R.string.gr_auth_access_already_auth;
            }
            if (mFragment.isCancelled()) {
                return R.string.progress_end_cancelled;
            }
            return R.string.info_authorized;
        }

        @Override
        protected void onPostExecute(@NonNull final Integer result) {
            StandardDialogs.showUserMessage(result);
            super.onPostExecute(result);
        }
    }
}
