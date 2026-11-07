package com.comeforfood.saferoute.core.network;

import android.graphics.*;
import android.widget.ImageView;
import androidx.lifecycle.*;
import java.io.*;
import java.util.concurrent.*;
import retrofit2.Call;
import okhttp3.ResponseBody;

/** Bounded private image decode off the UI thread. No disk cache or token in URL. */
public final class PrivateImage {
    private static final ExecutorService WORKER=Executors.newFixedThreadPool(2);
    private PrivateImage(){}
    public static void load(LifecycleOwner owner,ApiClient client,String url,ImageView image) {
        load(owner,client,url,image,loaded->{});
    }
    public static void load(LifecycleOwner owner,ApiClient client,String url,ImageView image,java.util.function.Consumer<Boolean> completion) {
        final Call<ResponseBody> call;
        try {call=client.service.image(client.imageUrl(url));}catch(IllegalArgumentException e){completion.accept(false);return;}
        Lifecycle lifecycle=owner.getLifecycle();
        java.lang.ref.WeakReference<ImageView> target=new java.lang.ref.WeakReference<>(image);
        DefaultLifecycleObserver observer=new DefaultLifecycleObserver(){@Override public void onDestroy(LifecycleOwner o){call.cancel();target.clear();}};
        lifecycle.addObserver(observer);
        WORKER.execute(()->{
            Bitmap bitmap=null;
            try {
                retrofit2.Response<ResponseBody> response=call.execute();
                if(response.errorBody()!=null)response.errorBody().close();
                if(response.isSuccessful()&&response.body()!=null)try(ResponseBody body=response.body();InputStream in=body.byteStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                    byte[] bytes=new byte[8192];int count,total=0;
                    while((count=in.read(bytes))!=-1){total+=count;if(total>5*1024*1024)throw new IOException("Image limit");out.write(bytes,0,count);}
                    byte[] data=out.toByteArray();BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
                    BitmapFactory.decodeByteArray(data,0,data.length,options);
                    options.inSampleSize=Math.max(1,Math.max(options.outWidth,options.outHeight)/1024);options.inJustDecodeBounds=false;
                    bitmap=BitmapFactory.decodeByteArray(data,0,data.length,options);
                }
            }catch(IOException|RuntimeException ignored){}
            Bitmap decoded=bitmap;
            new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{
                lifecycle.removeObserver(observer);ImageView view=target.get();
                if(view!=null&&!call.isCanceled()&&lifecycle.getCurrentState()!=Lifecycle.State.DESTROYED){
                    completion.accept(decoded!=null);
                    if(decoded!=null)view.setImageBitmap(decoded);else view.setContentDescription("Ảnh không khả dụng hoặc không có quyền truy cập");
                }
            });
        });
    }
}
