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

import okhttp3.ResponseBody;
import okio.*;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.Executor;

import static com.github.samizerouta.retrofit2.adapter.download.Util.checkNotNull;
import static com.github.samizerouta.retrofit2.adapter.download.Util.closeQuietly;

final class RealDownloadCall implements DownloadCall {

    private final Call<ResponseBody> call;
    final Executor callbackExecutor;
    volatile boolean canceled;

    RealDownloadCall(Call<ResponseBody> call,
                     Executor callbackExecutor) {
        this.call = call;
        this.callbackExecutor = callbackExecutor;
    }

    @Override
    public void enqueue(final OutputStream to, final Callback callback) {
        checkNotNull(to, "to == null");
        checkNotNull(callback, "callback == null");

        call.enqueue(new retrofit2.Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (!response.isSuccessful()) {
                    callFailure(new Exception("HTTP: " + response.code() + " " + response.message()));
                    closeQuietly(to);
                } else {
                    final ResponseBody body = response.body();
                    BufferedSink sink = Okio.buffer(Okio.sink(to));
                    Source source = new ForwardingSource(body.source()) {
                        long totalBytesRead = 0L;

                        @Override
                        public long read(Buffer sink, long byteCount) throws IOException {
                            if (canceled) {
                                throw new IOException("Canceled");
                            }

                            long bytesRead = super.read(sink, byteCount);
                            totalBytesRead += bytesRead != -1 ? bytesRead : 0;
                            callProgress(totalBytesRead, body.contentLength());
                            return bytesRead;
                        }
                    };

                    try {
                        sink.writeAll(source);
                        callSuccess();
                    } catch (Throwable t) {
                        callFailure(t);
                    } finally {
                        closeQuietly(sink);
                        closeQuietly(source);
                    }
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable throwable) {
                callFailure(throwable);
                closeQuietly(to);
            }

            private void callSuccess() {
                callbackExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            callback.onSuccess(RealDownloadCall.this);
                        } catch (Throwable t) {
                            t.printStackTrace();
                        }
                    }
                });
            }

            private void callFailure(final Throwable throwable) {
                callbackExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            callback.onFailure(RealDownloadCall.this, throwable);
                        } catch (Throwable t) {
                            t.printStackTrace();
                        }
                    }
                });
            }

            private void callProgress(final long totalBytesDownloaded, final long totalBytes) {
                callbackExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            callback.onProgress(RealDownloadCall.this, totalBytesDownloaded, totalBytes);
                        } catch (Throwable t) {
                            t.printStackTrace();
                        }
                    }
                });
            }
        });
    }

    @Override
    public boolean isExecuted() {
        return call.isExecuted();
    }

    @Override
    public void cancel() {
        if (!canceled) {
            call.cancel();
        }
        canceled = true;
    }

    @Override
    public boolean isCanceled() {
        return canceled;
    }

    @Override
    public DownloadCall clone() {
        return new RealDownloadCall(call.clone(), callbackExecutor);
    }
}
