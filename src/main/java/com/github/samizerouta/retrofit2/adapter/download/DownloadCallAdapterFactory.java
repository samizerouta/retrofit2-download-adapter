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
import retrofit2.Call;
import retrofit2.CallAdapter;
import retrofit2.Retrofit;
import retrofit2.http.Streaming;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.concurrent.Executor;

public final class DownloadCallAdapterFactory extends CallAdapter.Factory {
    public static DownloadCallAdapterFactory create() {
        return new DownloadCallAdapterFactory();
    }

    private DownloadCallAdapterFactory() {
    }

    @Override
    public CallAdapter<?> get(Type returnType, Annotation[] annotations, final Retrofit retrofit) {
        if (returnType != Download.Builder.class) {
            return null;
        }

        if (!Util.isAnnotationPresent(annotations, Streaming.class)) {
            throw new IllegalArgumentException("Download.Builder requires @Streaming.");
        }

        return new CallAdapter<Download.Builder>() {
            @Override
            public Type responseType() {
                return ResponseBody.class;
            }

            @SuppressWarnings("unchecked")
            @Override
            public <R> Download.Builder adapt(Call<R> call) {
                Download.Builder builder = new Download.Builder((Call<ResponseBody>) call);

                Executor callbackExecutor = retrofit.callbackExecutor();

                if (callbackExecutor != null) {
                    builder.callbackExecutor(callbackExecutor);
                }

                return builder;
            }
        };
    }
}
