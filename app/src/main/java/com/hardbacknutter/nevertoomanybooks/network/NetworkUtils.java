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
package com.hardbacknutter.nevertoomanybooks.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.util.Log;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.preference.PreferenceManager;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.debug.TestFlags;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.tasks.ASyncExecutor;

public final class NetworkUtils {

    /** Log tag. */
    private static final String TAG = "NetworkUtils";

    /** Timeout for {@link #ping(String)}; connection to the DNS server. */
    private static final long DNS_TIMEOUT_MS = 5_000L;

    /** Timeout for {@link #ping(String)}; connection to the host. */
    private static final int PING_TIMEOUT_MS = 5_000;

    private NetworkUtils() {
    }

    /**
     * Check if we have network access; taking into account whether the user permits
     * metered (i.e. pay-per-usage) networks or not.
     * <p>
     * When running a JUnit test, this method will always
     * return {@link TestFlags#isInternetConnected}.
     *
     * @return {@code true} if the application can access the internet
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    @AnyThread
    public static boolean isNetworkAvailable(@NonNull final Context context) {
        if (BuildConfig.DEBUG /* always */) {
            if (TestFlags.isJUnit) {
                return TestFlags.isInternetConnected;
            }
        }

        final ConnectivityManager connMgr = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        final Network network = connMgr.getActiveNetwork();
        if (network != null) {
            // https://developer.android.com/training/basics/network-ops/reading-network-state
            final NetworkCapabilities nc = connMgr.getNetworkCapabilities(network);
            if (nc != null) {

                // Indicates that the network is set up to access the internet.
                // Note that this is about setup and not actual ability to reach public servers.
                final boolean hasInternet =
                        nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);

                // Indicates the network has been found to provide actual access to
                // the public internet last time it was probed.w
                final boolean isValidated =
                        nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);

                // Indicates that the network is (not) metered.
                final boolean isMetered =
                        !nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);

                final boolean isMeteredAllowed = PreferenceManager
                        .getDefaultSharedPreferences(context)
                        .getBoolean(Prefs.pk_network_allow_metered, false);

                if (BuildConfig.DEBUG && DEBUG_SWITCHES.NETWORK) {
                    Log.d(TAG, "getNetworkCapabilities"
                               + "|hasInternet=" + hasInternet
                               + "|isValidated=" + isValidated
                               + "|isMetered=" + isMetered
                               + "|isMeteredAllowed=" + isMeteredAllowed
                         );
                }

                return hasInternet
                       && isValidated
                       && (isMetered || isMeteredAllowed);
            }
        }

        // network unavailable
        return false;
    }

    /**
     * Low level check if a url is reachable.
     * <p>
     * A call to {@link #isNetworkAvailable(Context)} should be made before calling this method.
     *
     * @param urlStr url to check
     *
     * @throws UnknownHostException   the IP address of a host could not be determined.
     * @throws IOException            if we cannot reach the site
     * @throws SocketTimeoutException on timeouts (both DNS and host itself)
     */
    @WorkerThread
    public static void ping(@NonNull final String urlStr)
            throws UnknownHostException,
                   IOException,
                   SocketTimeoutException,
                   MalformedURLException {

        final URL url = new URL(urlStr.toLowerCase(Locale.ROOT));
        final String host = url.getHost();
        int port = url.getPort();
        if (port == -1) {
            switch (url.getProtocol()) {
                case "https":
                    port = 443;
                    break;

                case "http":
                    port = 80;
                    break;

                default:
                    // should never get here... flw
                    throw new MalformedURLException(urlStr);
            }
        }

        final InetAddress inetAddress = new DNSService().lookup(host, DNS_TIMEOUT_MS);

        final Socket sock = new Socket();
        sock.connect(new InetSocketAddress(inetAddress, port), PING_TIMEOUT_MS);
        sock.close();
    }

    /**
     * Workaround for {@link InetAddress#getByName(String)} which does not support a timeout.
     */
    private static class DNSService {

        @NonNull
        InetAddress lookup(@NonNull final String host,
                           @SuppressWarnings("SameParameterValue") final long timeoutMs)
                throws CancellationException,
                       IOException,
                       SocketTimeoutException,
                       UnknownHostException {

            Future<InetAddress> future = null;
            try {
                future = ASyncExecutor.SERVICE.submit(() -> InetAddress.getByName(host));

                final InetAddress inetAddress = future.get(timeoutMs, TimeUnit.MILLISECONDS);
                // sanity check
                if (inetAddress == null) {
                    throw new UnknownHostException(host);
                }
                return inetAddress;

            } catch (@NonNull final ExecutionException e) {
                final Throwable cause = e.getCause();

                if (cause instanceof IOException) {
                    throw (IOException) cause;
                }

                if (BuildConfig.DEBUG /* always */) {
                    Log.d(TAG, "", e);
                }
                throw new UnknownHostException(host);

            } catch (@NonNull final RejectedExecutionException | InterruptedException e) {
                if (BuildConfig.DEBUG /* always */) {
                    Log.d(TAG, "", e);
                }
                throw new UnknownHostException(host);

            } catch (@NonNull final TimeoutException e) {
                // re-throw as if it's coming from the network call.
                throw new SocketTimeoutException(host);

            } finally {
                // paranoia
                if (future != null) {
                    future.cancel(true);
                }
            }
        }
    }
}
