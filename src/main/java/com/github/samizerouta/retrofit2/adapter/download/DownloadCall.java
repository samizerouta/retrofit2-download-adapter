/*
 * Copyright 2016 Sami Zerouta
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.samizerouta.retrofit2.adapter.download;

import java.io.OutputStream;

public interface DownloadCall extends Cloneable {

    interface Callback {

        void onSuccess(DownloadCall call);

        void onFailure(DownloadCall call, Throwable throwable);

        void onProgress(DownloadCall call, long totalBytesDownloaded, long totalBytes);
    }

    void enqueue(OutputStream to, Callback callback);

    boolean isExecuted();

    void cancel();

    boolean isCanceled();

    DownloadCall clone();
}
