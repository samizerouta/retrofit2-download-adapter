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
    public static final Executor CURRENT_THREAD_EXECUTOR = new Executor() {
        @Override
        public void execute(Runnable command) {
            command.run();
        }
    };

    final Call<ResponseBody> delegate;
    final Executor callbackExecutor;
    final ChecksumAlgorithm checksumAlgorithm;
    final ChecksumValidationCallback checksumValidationCallback;
    final List<Filter> filters;
    final ProgressListener progressListener;
    final Object tag;
    final File file;

    volatile boolean canceled;

    Download(Builder builder) {
        delegate = builder.delegate;
        callbackExecutor = builder.callbackExecutor;
        checksumAlgorithm = builder.checksumAlgorithm;
        checksumValidationCallback = builder.checksumValidationCallback;
        filters = Collections.unmodifiableList(new ArrayList<>(builder.filters));
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

    public ChecksumAlgorithm checksumAlgorithm() {
        return checksumAlgorithm;
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
                output = filter.newFilter((OutputStream) output);
            }

            input = source(body);
            output = Okio.sink((OutputStream) output);

            HashingSink hashingSink = null;

            switch (checksumAlgorithm) {
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

            bufferedSink.writeAll(input);
            bufferedSink.flush();

            if (hashingSink != null
                    && !checksumValidationCallback.validate(this, hashingSink.hash().hex())) {
                throw new InvalidChecksumException();
            }
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
        ChecksumAlgorithm checksumAlgorithm;
        ChecksumValidationCallback checksumValidationCallback;
        List<Filter> filters = new ArrayList<>();
        ProgressListener progressListener;
        Object tag;
        File file;

        Builder(Call<ResponseBody> delegate) {
            this.delegate = delegate;
            this.callbackExecutor = CURRENT_THREAD_EXECUTOR;
            this.checksumAlgorithm = ChecksumAlgorithm.NONE;
            this.progressListener = ProgressListener.NONE;
        }

        Builder(Download download) {
            delegate = download.delegate.clone();
            callbackExecutor = download.callbackExecutor;
            checksumAlgorithm = download.checksumAlgorithm;
            progressListener = download.progressListener;
            checksumValidationCallback = download.checksumValidationCallback;
            filters.addAll(download.filters);
            tag = download.tag;
            file = download.file;
        }

        public Builder callbackExecutor(Executor callbackExecutor) {
            this.callbackExecutor = Util.checkNotNull(callbackExecutor, "callbackExecutor == null");
            return this;
        }

        public Builder checksum(ChecksumAlgorithm algorithm, ChecksumValidationCallback validationCallback) {
            this.checksumAlgorithm = Util.checkNotNull(algorithm, "algorithm == null");
            this.checksumValidationCallback = Util.checkNotNull(validationCallback, "validationCallback = null");
            return this;
        }

        public Builder addFilter(Filter filter) {
            this.filters.add(Util.checkNotNull(filter, "newFilter == null"));
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
