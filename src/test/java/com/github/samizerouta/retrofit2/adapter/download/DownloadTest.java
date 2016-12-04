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
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.BufferedSource;
import okio.Okio;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.http.GET;
import retrofit2.http.Streaming;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.*;

public final class DownloadTest {
    interface Service {
        @Streaming
        @GET("/")
        Download.Builder download();
    }

    @Rule
    public final MockWebServer server = new MockWebServer();

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Service service;
    private File file;

    @Before
    public void setUp() throws IOException {
        service = new Retrofit.Builder()
                .baseUrl(server.url("/"))
                .addCallAdapterFactory(DownloadCallAdapterFactory.create())
                .build()
                .create(Service.class);

        file = folder.newFile();
    }

    @Test
    public void response200Sync() throws IOException {
        server.enqueue(new MockResponse().setBody("Hi"));

        Response<?> response = service.download().to(file).execute();

        assertTrue(response.isSuccessful());
        assertEquals("Hi", readFile());
    }

    @Test
    public void response200Async() throws InterruptedException, IOException {
        server.enqueue(new MockResponse().setBody("Hi"));
        final AtomicReference<Response<ResponseBody>> responseRef = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);

        service.download().to(file).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                responseRef.set(response);
                latch.countDown();
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
            }
        });

        assertTrue(latch.await(10, SECONDS));
        assertTrue(responseRef.get().isSuccessful());
        assertEquals("Hi", readFile());
    }

    @Test
    public void response404Sync() throws IOException {
        server.enqueue(new MockResponse().setResponseCode(404));

        Response<?> response = service.download().to(file).execute();

        assertFalse(response.isSuccessful());
    }

    @Test
    public void response404Async() throws InterruptedException {
        server.enqueue(new MockResponse().setResponseCode(404));
        final AtomicReference<Response<ResponseBody>> responseRef = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);

        service.download().to(file).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                responseRef.set(response);
                latch.countDown();
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
            }
        });

        assertTrue(latch.await(10, SECONDS));
        assertFalse(responseRef.get().isSuccessful());
    }

    private String readFile() throws IOException {
        BufferedSource source = null;
        try {
            source = Okio.buffer(Okio.source(file));
            return source.readUtf8();
        } finally {
            Util.closeQuietly(source);
        }
    }
}