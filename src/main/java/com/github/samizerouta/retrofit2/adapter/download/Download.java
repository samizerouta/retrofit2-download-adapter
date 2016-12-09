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

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

public final class Download implements Call<ResponseBody> {
    static final Executor CURRENT_THREAD_EXECUTOR = new Executor() {
        @Override
        public void execute(Runnable command) {
            command.run();
        }
    };

    final Call<ResponseBody> delegate;
    final Executor callbackExecutor;
    final Checksum checksum;
    final ValidationCallback validationCallback;
    final ProgressListener progressListener;
    final List<Filter<?>> filters;
    final Object tag;
    final File file;

    volatile boolean canceled;

    Download(Builder builder) {
        delegate = builder.delegate.clone();
        callbackExecutor = builder.callbackExecutor;
        checksum = builder.checksum;
        validationCallback = builder.validationCallback;
        progressListener = builder.progressListener;
        filters = Collections.unmodifiableList(new ArrayList<>(builder.filters));
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

        copyToFile(response.body());

        return response;
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
                    try {
                        copyToFile(response.body());
                        callResponse(response);
                    } catch (Throwable throwable) {
                        callFailure(throwable);
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

    public Executor callbackExecutor() {
        return callbackExecutor;
    }

    public Checksum checksum() {
        return checksum;
    }

    public List<Filter<?>> filters() {
        return filters;
    }

    public Object tag() {
        return tag;
    }

    public File file() {
        return file;
    }

    private void copyToFile(ResponseBody body) throws IOException {
        Source input = null;
        Closeable output = null;

        try {
            output = new FileOutputStream(file);

            for (int i = filters.size(); i > 0; i--) {
                Filter filter = filters.get(i - 1);

                if (filter instanceof OutputStreamFilter) {
                    if (output instanceof Sink) {
                        output = Okio.buffer((Sink) output).outputStream();
                    }
                    output = ((OutputStreamFilter) filter).create(this, (OutputStream) output);
                } else {
                    if (output instanceof OutputStream) {
                        output = Okio.sink((OutputStream) output);
                    }
                    output = ((SinkFilter) filter).create(this, (Sink) output);
                }
            }

            if (output instanceof OutputStream) {
                output = Okio.sink((OutputStream) output);
            }

            HashingSink hashingSink = null;

            switch (checksum) {
                case MD5:
                    output = hashingSink = HashingSink.md5((Sink) output);
                    break;
                case SHA1:
                    output = hashingSink = HashingSink.sha1((Sink) output);
                    break;
                case SHA256:
                    output = hashingSink = HashingSink.sha256((Sink) output);
                    break;
            }

            BufferedSink bufferedSink = Okio.buffer((Sink) output);
            output = bufferedSink;

            input = source(body);
            bufferedSink.writeAll(input);
            bufferedSink.flush();

            final String hash = hashingSink == null ? null : hashingSink.hash().hex();

            validationCallback.validate(Download.this, hash);
        } finally {
            Util.closeQuietly(input, output);
        }
    }

    private Source source(final ResponseBody body) {
        return new ForwardingSource(body.source()) {
            long totalBytesRead = 0;

            @Override
            public long read(Buffer sink, long byteCount) throws IOException {
                if (canceled) {
                    throw new IOException("Canceled");
                }

                final long bytesRead = super.read(sink, byteCount);

                if (bytesRead != -1) {
                    totalBytesRead += bytesRead;
                    callbackExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            progressListener.onProgress(Download.this, bytesRead, totalBytesRead, body.contentLength());
                        }
                    });
                }

                return bytesRead;
            }
        };
    }

    /**
     * Build a new {@link Download}.
     */
    public static final class Builder {
        final Call<ResponseBody> delegate;
        Executor callbackExecutor;
        Checksum checksum;
        ValidationCallback validationCallback;
        ProgressListener progressListener;
        final List<Filter<?>> filters = new ArrayList<>();
        Object tag;
        File file;

        Builder(Call<ResponseBody> delegate) {
            this.delegate = delegate;
            this.callbackExecutor = CURRENT_THREAD_EXECUTOR;
            this.checksum = Checksum.NONE;
            this.validationCallback = ValidationCallback.NONE;
            this.progressListener = ProgressListener.NONE;
        }

        Builder(Download download) {
            delegate = download;
            callbackExecutor = download.callbackExecutor;
            checksum = download.checksum;
            validationCallback = download.validationCallback;
            progressListener = download.progressListener;
            filters.addAll(download.filters);
            tag = download.tag;
            file = download.file;
        }

        /**
         * The executor on which {@link Callback} and {@link ProgressListener} methods are invoked.
         * By default the {@link  retrofit2.Retrofit} instance callback executor is used.
         */
        public Builder callbackExecutor(Executor callbackExecutor) {
            this.callbackExecutor = Util.checkNotNull(callbackExecutor, "callbackExecutor == null");
            return this;
        }

        /**
         * Set the {@link ValidationCallback} for the {@link Download}.
         */
        public Builder validate(Checksum checksum, ValidationCallback validationCallback) {
            this.checksum = Util.checkNotNull(checksum, "checksum == null");
            this.validationCallback = Util.checkNotNull(validationCallback, "validationCallback == null");
            return this;
        }

        /**
         * Set the {@link ProgressListener} for the {@link Download}.
         */
        public Builder progress(ProgressListener progressListener) {
            this.progressListener = Util.checkNotNull(progressListener, "progressListener == null");
            return this;
        }

        /**
         * Add filter for stream modification.
         */
        public Builder addFilter(OutputStreamFilter filter) {
            this.filters.add(Util.checkNotNull(filter, "filter == null"));
            return this;
        }

        /**
         * Add filter for stream modification.
         */
        public Builder addFilter(SinkFilter filter) {
            this.filters.add(Util.checkNotNull(filter, "filter == null"));
            return this;
        }

        /**
         * The {@link Download} tag.
         */
        public Builder tag(Objects tag) {
            this.tag = tag;
            return this;
        }

        /**
         * Create the {@link Download} to the {@code file} using the configured values.
         */
        public Download to(File file) {
            this.file = Util.checkNotNull(file, "file == null");
            return new Download(this);
        }
    }
}
