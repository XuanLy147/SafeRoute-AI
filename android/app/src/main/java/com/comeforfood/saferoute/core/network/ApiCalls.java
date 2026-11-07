package com.comeforfood.saferoute.core.network;

import androidx.lifecycle.*;
import retrofit2.*;
import java.util.function.Consumer;

/** Cancel on view destruction and suppress late callbacks. Never retry writes automatically. */
public final class ApiCalls {
    private ApiCalls() {}
    public static <T> void enqueue(LifecycleOwner owner, Call<T> call, Consumer<T> success, Consumer<String> failure) {
        enqueueDetailed(owner,call,success,(code,message)->failure.accept(message));
    }
    public static <T> void enqueueDetailed(LifecycleOwner owner,Call<T> call,Consumer<T> success,java.util.function.BiConsumer<Integer,String> failure) {
        Lifecycle lifecycle=owner.getLifecycle();
        DefaultLifecycleObserver observer=new DefaultLifecycleObserver() {
            @Override public void onDestroy(LifecycleOwner owner) { call.cancel(); }
        };
        lifecycle.addObserver(observer);
        call.enqueue(new Callback<T>() {
            private boolean active() { lifecycle.removeObserver(observer);return !call.isCanceled() && lifecycle.getCurrentState()!=Lifecycle.State.DESTROYED; }
            @Override public void onResponse(Call<T> c, Response<T> r) {
                if(!active())return;
                if(r.isSuccessful() && r.body()!=null) success.accept(r.body());
                else failure.accept(r.code(),message(r.code()));
                if(r.errorBody()!=null)r.errorBody().close();
            }
            @Override public void onFailure(Call<T> c, Throwable t) {
                if(active()) failure.accept(0,"Không nhận được phản hồi. Với thao tác gửi, hãy kiểm tra dữ liệu trước khi thử lại.");
            }
        });
    }
    public static String message(int code) {
        switch(code) {
            case 400: case 422: return "Dữ liệu không hợp lệ. Kiểm tra các trường đã nhập (HTTP " + code + ").";
            case 401: return "Phiên đăng nhập không hợp lệ. Vui lòng đăng nhập lại.";
            case 403: return "Bạn không có quyền truy cập dữ liệu này.";
            case 404: return "Không tìm thấy dữ liệu.";
            case 409: return "Dữ liệu đã tồn tại hoặc đã thay đổi. Hãy tải lại trước khi tiếp tục.";
            case 413: return "Ảnh vượt giới hạn 5 MiB hoặc 25 triệu điểm ảnh.";
            case 429: return "Quá nhiều yêu cầu. Vui lòng thử lại sau.";
            default: return "Dịch vụ chưa khả dụng (HTTP " + code + "). Vui lòng thử lại sau.";
        }
    }
}
