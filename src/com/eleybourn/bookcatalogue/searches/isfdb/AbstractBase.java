package com.eleybourn.bookcatalogue.searches.isfdb;

import androidx.annotation.NonNull;

import com.eleybourn.bookcatalogue.EditBookTOCFragment;
import com.eleybourn.bookcatalogue.debug.Logger;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.net.SocketTimeoutException;

abstract class AbstractBase {

    /**
     * Trim extraneous punctuation and whitespace from the titles and authors.
     * <p>
     * Original code in {@link EditBookTOCFragment} had:
     * CLEANUP_REGEX = "[\\,\\.\\'\\:\\;\\`\\~\\@\\#\\$\\%\\^\\&\\*\\(\\)\\-\\=\\_\\+]*$";
     * <p>
     * Android Studio:
     * Reports character escapes that are replaceable with the unescaped character without a
     * change in meaning. Note that inside the square brackets of a character class, many
     * escapes are unnecessary that would be necessary outside of a character class.
     * For example the regex [\.] is identical to [.]
     * <p>
     * So that became:
     * private static final String CLEANUP_REGEX = "[,.':;`~@#$%^&*()\\-=_+]*$";
     * <p>
     * But given a title like "Introduction (The Father-Thing)"
     * you loose the ")" at the end, so remove that from the regex, see below
     */
    private static final String CLEANUP_TITLE_REGEX = "[,.':;`~@#$%^&*(\\-=_+]*$";

    String mPath;
    Document mDoc;

    /**
     * Fetch the URL as defined by {@link #mPath} and parse it into {@link #mDoc}.
     * <p>
     * the connect call uses a set of defaults. For example the user-agent:
     * {@link org.jsoup.helper.HttpConnection#DEFAULT_UA}
     * <p>
     * The content encoding by default is: "Accept-Encoding", "gzip"
     *
     * @return <tt>true</tt> when fetched and parsed ok.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean loadPage()
            throws SocketTimeoutException {
        if (mDoc == null) {
            Connection con = Jsoup.connect(mPath)
                                  // added due to https://github.com/square/okhttp/issues/1517
                                  // it's a server issue, this is a workaround.
                                  .header("Connection", "close")
                                  // connect and read-timeout. Default is 30.
                                  .timeout(60_000)
                                  // maximum bytes to read before connection is closed, default 1mb
                                  .maxBodySize(2_000_000);

            try {
                /*
                 * @throws java.net.MalformedURLException if the request URL is not a HTTP
                 * or HTTPS URL, or is otherwise malformed
                 * @throws HttpStatusException(IOException) if the response is not OK and
                  * HTTP response errors are not ignored
                 * @throws UnsupportedMimeTypeException if the response mime type is not
                  * supported and those errors are not ignored
                 * @throws java.net.SocketTimeoutException if the connection times out
                 * @throws IOException on error
                 */
                mDoc = con.get();

            } catch (java.net.SocketTimeoutException e) {
                throw e;
            } catch (IOException e) {
                Logger.error(e, mPath);
                return false;
            }
        }
        return true;
    }

    @NonNull
    String cleanUpName(@NonNull final String s) {
        return s.trim()
                .replace("\n", " ")
                .replaceAll(CLEANUP_TITLE_REGEX, "")
                .trim();
    }

    long stripNumber(@NonNull final String url) {
        return Long.parseLong(url.split("\\?")[1]);
    }
}
