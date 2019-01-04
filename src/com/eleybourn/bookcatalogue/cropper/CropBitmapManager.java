/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.eleybourn.bookcatalogue.cropper;

import android.graphics.BitmapFactory;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * This class provides several utilities to cancel bitmap decoding.
 *
 * The function decodeFileDescriptor() is used to decode a bitmap. During
 * decoding if another thread wants to cancel it, it calls the function
 * cancelThreadDecoding() specifying the Thread which is in decoding.
 *
 * cancelThreadDecoding() is sticky until allowThreadDecoding() is called.
 *
 * You can also cancel decoding for a set of threads using ThreadSet as the
 * parameter for cancelThreadDecoding. To put a thread into a ThreadSet, use the
 * add() method. A ThreadSet holds (weak) references to the threads, so you
 * don't need to remove Thread from it if some thread dies.
 */
class CropBitmapManager {
    @Nullable
    private static CropBitmapManager sManager = null;
    private final Map<Thread, ThreadStatus> mThreadStatus = new WeakHashMap<>();

    private CropBitmapManager() {
    }

    @NonNull
    public static synchronized CropBitmapManager instance() {
        if (sManager == null) {
            sManager = new CropBitmapManager();
        }
        return sManager;
    }

    /**
     * Get thread status and create one if specified.
     */
    @NonNull
    private synchronized ThreadStatus getOrCreateThreadStatus(@NonNull final Thread t) {
        ThreadStatus status = mThreadStatus.get(t);
        if (status == null) {
            status = new ThreadStatus();
            mThreadStatus.put(t, status);
        }
        return status;
    }

    /**
     * The following three methods are used to keep track of
     * BitmapFaction.Options used for decoding and cancelling.
     */
    @SuppressWarnings("unused")
    private synchronized void setDecodingOptions(@NonNull final Thread t,
                                                 @NonNull final BitmapFactory.Options options) {
        getOrCreateThreadStatus(t).mOptions = options;
    }

    @SuppressWarnings("unused")
    @Nullable
    synchronized BitmapFactory.Options getDecodingOptions(@NonNull final Thread t) {
        ThreadStatus status = mThreadStatus.get(t);
        return status != null ? status.mOptions : null;
    }

    @SuppressWarnings("unused")
    private synchronized void removeDecodingOptions(@NonNull final Thread t) {
        ThreadStatus status = mThreadStatus.get(t);
        status.mOptions = null;
    }

    /**
     * The following two methods are used to allow/cancel a set of threads for
     * bitmap decoding.
     */
    @SuppressWarnings("unused")
    public synchronized void allowThreadDecoding(@NonNull final ThreadSet threads) {
        for (Thread t : threads) {
            allowThreadDecoding(t);
        }
    }

    synchronized void cancelThreadDecoding(@NonNull final ThreadSet threads) {
        for (Thread t : threads) {
            cancelThreadDecoding(t);
        }
    }

    /**
     * The following three methods are used to keep track of which thread is
     * being disabled for bitmap decoding.
     */
    @SuppressWarnings("unused")
    private synchronized boolean canThreadDecoding(@NonNull final Thread t) {
        ThreadStatus status = mThreadStatus.get(t);
        // allow decoding by default
        return status == null || (status.mState != State.Cancel);

    }

    private synchronized void allowThreadDecoding(@NonNull final Thread t) {
        getOrCreateThreadStatus(t).mState = State.Allow;
    }

    private synchronized void cancelThreadDecoding(@NonNull final Thread t) {
        ThreadStatus status = getOrCreateThreadStatus(t);
        status.mState = State.Cancel;
        if (status.mOptions != null) {
            status.mOptions.requestCancelDecode();
        }

        // Wake up threads in waiting list
        notifyAll();
    }

    private enum State {
        Cancel, Allow;

        @NonNull
        @Override
        public String toString() {
            switch (this) {
                case Cancel:
                    return "Cancel";
                case Allow:
                    return "Allow";
            }
            return "";
        }
    }

    private static class ThreadStatus {
        @NonNull
        State mState = State.Allow;
        @Nullable
        BitmapFactory.Options mOptions;
    }

//	 /**
//	 * A debugging routine.
//	 */
//	 public synchronized void dump() {
//	 Iterator<Map.Entry<Thread, ThreadStatus>> i =
//	 mThreadStatus.entrySet().iterator();
//
//	 while (i.hasNext()) {
//	 Map.Entry<Thread, ThreadStatus> entry = i.next();
//	 }
//	 }

    public static class ThreadSet implements Iterable<Thread> {
        private final Map<Thread, Object> mWeakCollection = new WeakHashMap<>();

        public void add(@NonNull final Thread t) {
            mWeakCollection.put(t, null);
        }

        public void remove(@NonNull final Thread t) {
            mWeakCollection.remove(t);
        }

        @NonNull
        public Iterator<Thread> iterator() {
            return mWeakCollection.keySet().iterator();
        }
    }

//	/**
//	 * The real place to delegate bitmap decoding to BitmapFactory.
//	 */
//	@Nullable
//	public Bitmap decodeFileDescriptor(@NonNull final FileDescriptor fd,
//			BitmapFactory.Options options) {
//		if (options.mCancel) {
//			return null;
//		}
//
//		Thread thread = Thread.currentThread();
//		if (!canThreadDecoding(thread)) {
//			return null;
//		}
//
//		setDecodingOptions(thread, options);
//		Bitmap b = BitmapFactory.decodeFileDescriptor(fd, null, options);
//
//		removeDecodingOptions(thread);
//		return b;
//	}
}