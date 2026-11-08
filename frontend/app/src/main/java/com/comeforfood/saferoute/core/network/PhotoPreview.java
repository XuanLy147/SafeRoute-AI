package com.comeforfood.saferoute.core.network;

import android.content.ContentResolver;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import androidx.lifecycle.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Validate and downsample selected content before allocating preview pixels. */
public final class PhotoPreview {
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor();
    private PhotoPreview(){}
    public static void load(LifecycleOwner owner,ContentResolver resolver,Uri uri,Consumer<Bitmap> done) {
        Lifecycle lifecycle=owner.getLifecycle();java.util.concurrent.atomic.AtomicBoolean canceled=new java.util.concurrent.atomic.AtomicBoolean();
        DefaultLifecycleObserver observer=new DefaultLifecycleObserver(){@Override public void onDestroy(LifecycleOwner o){canceled.set(true);}};
        lifecycle.addObserver(observer);
        WORKER.execute(()->{
            Bitmap bitmap=null;
            try(InputStream input=resolver.openInputStream(uri);ByteArrayOutputStream output=new ByteArrayOutputStream()) {
                if(input==null)throw new IOException("Unreadable image");
                byte[] chunk=new byte[8192];int count,total=0;
                while((count=input.read(chunk))!=-1){if(canceled.get())return;total+=count;if(total>5*1024*1024)throw new IOException("Image byte limit");output.write(chunk,0,count);}
                byte[] bytes=output.toByteArray();BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
                BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
                if(options.outWidth<=0||options.outHeight<=0||(long)options.outWidth*options.outHeight>25_000_000)throw new IOException("Image pixel limit");
                options.inSampleSize=Math.max(1,Math.max(options.outWidth,options.outHeight)/1024);options.inJustDecodeBounds=false;
                bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
            }catch(IOException|RuntimeException ignored){}
            Bitmap result=bitmap;
            new Handler(Looper.getMainLooper()).post(()->{lifecycle.removeObserver(observer);if(!canceled.get()&&lifecycle.getCurrentState()!=Lifecycle.State.DESTROYED)done.accept(result);});
        });
    }
}
