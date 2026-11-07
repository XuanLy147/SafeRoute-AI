package com.comeforfood.saferoute.core.network;

import android.content.ContentResolver;
import android.net.Uri;
import java.io.*;
import okhttp3.*;
import okio.BufferedSink;

/** URI content is opened on OkHttp's worker thread; bounded streaming, never a sample asset. */
public final class ImageUpload {
    private ImageUpload() {}
    public static MultipartBody.Part part(ContentResolver resolver,Uri uri) {
        String type=resolver.getType(uri);
        if(!java.util.Arrays.asList("image/jpeg","image/png","image/webp").contains(type))throw new IllegalArgumentException("Chỉ hỗ trợ JPEG, PNG, WEBP.");
        String extension=type.equals("image/png")?"png":type.equals("image/webp")?"webp":"jpg";
        RequestBody body=new RequestBody() {
            @Override public MediaType contentType(){return MediaType.get(type);}
            @Override public boolean isOneShot(){return true;}
            @Override public void writeTo(BufferedSink sink) throws IOException {
                try(InputStream stream=resolver.openInputStream(uri)) {
                    if(stream==null)throw new IOException("Ảnh không đọc được");
                    byte[] buffer=new byte[8192];int count,total=0;
                    while((count=stream.read(buffer))!=-1) {
                        total+=count;if(total>5*1024*1024)throw new IOException("Ảnh vượt 5 MiB");
                        sink.write(buffer,0,count);
                    }
                    if(total==0)throw new IOException("Ảnh trống");
                }
            }
        };
        return MultipartBody.Part.createFormData("file","report."+extension,body);
    }
}
