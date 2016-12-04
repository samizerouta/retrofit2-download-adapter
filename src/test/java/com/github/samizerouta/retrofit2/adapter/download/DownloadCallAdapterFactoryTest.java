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

import org.junit.Test;
import retrofit2.Retrofit;
import retrofit2.http.Streaming;

import java.lang.annotation.Annotation;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public final class DownloadCallAdapterFactoryTest {
    private static final DownloadCallAdapterFactory FACTORY = DownloadCallAdapterFactory.create();

    private static final Retrofit RETROFIT = new Retrofit.Builder()
            .baseUrl("http://localhost")
            .build();

    private static final Streaming STREAMING = new Streaming() {
        @Override
        public Class<? extends Annotation> annotationType() {
            return Streaming.class;
        }
    };


    @Test
    public void anotherReturnType() {
        assertNull(
                FACTORY.get(Void.class, new Annotation[]{STREAMING}, RETROFIT)
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void streamingMissing() {
        FACTORY.get(Download.Builder.class, new Annotation[0], RETROFIT);
    }

    @Test
    public void happyCase() {
        assertNotNull(
                FACTORY.get(Download.Builder.class, new Annotation[]{STREAMING}, RETROFIT)
        );
    }
}