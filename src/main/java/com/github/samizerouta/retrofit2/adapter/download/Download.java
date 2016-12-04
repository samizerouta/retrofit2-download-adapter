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

import okhttp3.Request;
import okhttp3.ResponseBody;
import okio.*;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.Executor;

public final class Download implements Call<ResponseBody> {
    public static final Executor CURRENT_THREAD_EXECUTOR = new Executor() {
        @Override
        public void execute(Runnable command) {
            command.run();
        }
    };

    final Call<ResponseBody> delegate;
    final Executor callbackExecutor;
    final ProgressListener progressListener;
    final Object tag;
    final File file;

    volatile boolean canceled;

    Download(Builder builder) {
        delegate = builder.delegate;
        callbackExecutor = builder.callbackExecutor;
        progressListener = builder.progressListener;
        tag = builder.tag;
        file = builder.file;
    }

    @Override
    public Response<ResponseBody> execute() throws IOException {
        Response<ResponseBody> response = delegate.execute();

        if (canceled) {
            throw new IOException("Canceled");
        }

        if (!response.isSuccessful()) {
            return response;
        }

        BufferedSink sink = null;
        Source source = null;

        try {
            sink = Okio.buffer(Okio.sink(file));
            source = source(response.body());

            sink.writeAll(source);

            return response;
        } finally {
            Util.closeQuietly(sink, source);
        }
    }

    @Override
    public void enqueue(final Callback<ResponseBody> callback) {
        delegate.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (canceled) {
                    callFailure(new IOException("Canceled"));
                } else if (!response.isSuccessful()) {
                    callResponse(response);
                } else {
                    BufferedSink sink = null;
                    Source source = null;

                    try {
                        sink = Okio.buffer(Okio.sink(file));
                        source = source(response.body());

                        sink.writeAll(source);
                        callResponse(response);
                    } catch (Throwable throwable) {
                        callFailure(throwable);
                    } finally {
                        Util.closeQuietly(sink, source);
                    }
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callFailure(t);
            }

            void callResponse(final Response<ResponseBody> response) {
                callbackExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        callback.onResponse(Download.this, response);
                    }
                });
            }

            void callFailure(final Throwable throwable) {
                callbackExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        callback.onFailure(Download.this, throwable);
                    }
                });
            }
        });
    }

    @Override
    public boolean isExecuted() {
        return delegate.isExecuted();
    }

    @Override
    public void cancel() {
        canceled = true;
        delegate.cancel();
    }

    @Override
    public boolean isCanceled() {
        return canceled;
    }

    @Override
    public Download clone() {
        return new Download(new Builder(this));
    }

    @Override
    public Request request() {
        return delegate.request();
    }

    public Builder newBuilder() {
        return new Builder(this);
    }

    public Object tag() {
        return tag;
    }

    public File file() {
        return file;
    }

    private Source source(final ResponseBody body) {
        return new ForwardingSource(body.source()) {
            long totalBytesRead = 0;

            @Override
            public long read(Buffer sink, long byteCount) throws IOException {
                if (canceled) {
                    throw new IOException("Canceled");
                }

                long bytesRead = super.read(sink, byteCount);

                if (bytesRead != -1) {
                    totalBytesRead += bytesRead;
                    callProgress(bytesRead);
                }

                return bytesRead;
            }

            void callProgress(final long bytesRead) {
                callbackExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        progressListener.onProgress(Download.this, bytesRead, totalBytesRead, body.contentLength());
                    }
                });
            }
        };
    }

    public static final class Builder {
        Call<ResponseBody> delegate;
        Executor callbackExecutor;
        ProgressListener progressListener;
        Object tag;
        File file;

        Builder(Call<ResponseBody> delegate) {
            this.delegate = delegate;
            this.callbackExecutor = CURRENT_THREAD_EXECUTOR;
            this.progressListener = ProgressListener.NONE;
        }

        Builder(Download download) {
            this.delegate = download.delegate.clone();
            this.callbackExecutor = download.callbackExecutor;
            this.progressListener = download.progressListener;
            this.tag = download.tag;
            this.file = download.file;
        }

        public Builder callbackExecutor(Executor callbackExecutor) {
            this.callbackExecutor = Util.checkNotNull(callbackExecutor, "callbackExecutor == null");
            return this;
        }

        public Builder onProgress(ProgressListener progressListener) {
            this.progressListener = Util.checkNotNull(progressListener, "progressListener == null");
            return this;
        }

        public Builder tag(Objects tag) {
            this.tag = tag;
            return this;
        }

        public Download to(File file) {
            this.file = Util.checkNotNull(file, "file == null");
            return new Download(this);
        }
    }
}
