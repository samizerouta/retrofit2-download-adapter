Retrofit 2 Download Adapter
===========================

A file download specialized call for Retrofit 2.

Usage
-----

### Basics ###

Use `Download.Builder` as return type of the retrofit method, `@Streaming`annotation is required.

```java
interface Service {
  @Streaming
  @GET
  Download.Builder download(@Url String url);
}
```

Add `DownloadCallAdapterFactory` when setting up retrofit, and then create the service as usual.

```java
Retrofit retrofit = new Retrofit.Builder()
  ...
  .addCallAdapterFactory(DownloadCallAdapterFactory.create())
  ...
  .build();
  
Service service = retrofit.create(Service.class);
```

Specify the destination file with `.to`, a regular retrofit `Call<ReponseBody>` will be returned.
To start the download use `.execute` or `.enqueue`.

```java
File myFile = ...
service.download(someUrl)
  .to(myFile)
  .enqueue(new Callback<ResponseBody>() {
    @Override
    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
      ...
    }

    @Override
    public void onFailure(Call<ResponseBody> call, Throwable t) {
      ...
    }
  });
```

### Progress ###

Set a `ProgressListener`to be notified of the download advancement.

```java
service.download(someUrl)
  .progress(new ProgressListener() {
    @Override
    public void onProgress(Download download, long bytesRead, long totalBytesRead, long contentLength) {
      dialog.setProgress((int) (totalBytesRead * 100f / contentLength));
    }
  })
  .to(...)
  .enqueue(...)
```

### Tag ###

Extra data can be stored with `.tag`, it can be handy in some situations.

```java
MyFilePojo myFilePojo = ...
service.download(someUrl)
  .tag(myFilePojo)
  .to(...)
  .enqueue(new Callback<ResponseBody>() {
    @Override
    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
      Download download = (Download) call;
      MyFilePojo myFilePojo = (MyFilePojo) download.tag();
      openFile(download.file(), myFilePojo.mimeType());
    }

    @Override
    public void onFailure(Call<ResponseBody> call, Throwable t) {
      ...
    }
  });
```

### Validation ###

Validate the download when finished.

```java
final String expectedChecksum = ...
service.download(someUrl)
  .validate(Checksum.MD5, new ValidationCallback() {
    @Override
    public void validate(Download download, String checksum) throw IOException {
      if (!expectedChecksum.equals(checksum)) {
        throw new IOException("Invalid checksum");
      }
    }
  })
  .to(...)
  .enqueue(...);
```

### Filters ###

Stream can be modified before being written to the file with `Filter`s.

```java
service.download(someUrl)
  .addFilter(new OutputStreamFilter() {
    @Override
    public OutputStream create(Download download, OutputStream downstream) throws IOException {
      return new CipherOutputStream(downstream, new MyFancyCipher());
    }
  })
  .to(...)
  .enqueue(...);
```

Download
--------

[The latest JAR][1]

or maven
```xml
<dependency>
  <groupId>com.github.samizerouta.retrofit</groupId>
  <artifactId>retrofit2-download-adapter</artifactId>
  <version>1.0.0</version>
</dependency>
```

or gradle
```groovy
compile 'com.github.samizerouta.retrofit:retrofit2-download-adapter:1.0.0'
```

Snapshots of the development version are available in [Sonatype's `snapshots` repository][snap].

License
-------

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

[1]: https://search.maven.org/remote_content?g=com.github.samizerouta.retrofit&a=retrofit2-download-adapter&v=LATEST
[snap]: https://oss.sonatype.org/content/repositories/snapshots/
