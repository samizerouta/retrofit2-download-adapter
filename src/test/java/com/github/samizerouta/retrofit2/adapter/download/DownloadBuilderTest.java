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
import org.junit.Before;
import org.junit.Test;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public final class DownloadBuilderTest {
    private final static Call<ResponseBody> CALL = new Call<ResponseBody>() {
        @Override
        public Response<ResponseBody> execute() throws IOException {
            return null;
        }

        @Override
        public void enqueue(Callback<ResponseBody> callback) {
        }

        @Override
        public boolean isExecuted() {
            return false;
        }

        @Override
        public void cancel() {
        }

        @Override
        public boolean isCanceled() {
            return false;
        }

        @Override
        public Call<ResponseBody> clone() {
            return null;
        }

        @Override
        public Request request() {
            return null;
        }
    };

    private static final File FILE = new File("");

    private Download.Builder builder;

    @Before
    public void setUp() {
        builder = new Download.Builder(CALL);
    }

    @Test
    public void defaultValues() {
        assertSame(CALL, builder.delegate);
        assertSame(Download.CURRENT_THREAD_EXECUTOR, builder.callbackExecutor);
        assertSame(ProgressListener.NONE, builder.progressListener);
        assertNull(builder.tag);
        assertNull(builder.file);
    }

    @Test(expected = NullPointerException.class)
    public void nullFile() {
        builder.to(null);
    }
}