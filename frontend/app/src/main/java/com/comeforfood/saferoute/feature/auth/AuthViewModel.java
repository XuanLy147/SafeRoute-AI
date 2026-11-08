package com.comeforfood.saferoute.feature.auth;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.*;
import com.comeforfood.saferoute.SafeRouteApp;
import com.comeforfood.saferoute.core.model.AuthDto;
import com.comeforfood.saferoute.core.network.*;
import com.comeforfood.saferoute.core.session.SessionManager;
import retrofit2.*;

/** A request survives rotation. A candidate token is not a session until /me succeeds. */
public class AuthViewModel extends AndroidViewModel {
    public static final class State {
        public final boolean loading, complete;
        public final String message;
        State(boolean loading, boolean complete, String message) {
            this.loading=loading; this.complete=complete; this.message=message;
        }
    }
    public final MutableLiveData<State> state = new MutableLiveData<>(new State(false,false,""));
    private Call<AuthDto.AuthResponse> call;
    private final SessionManager session;
    private final ApiService api;
    private boolean registered;
    private long attempt;

    public AuthViewModel(@NonNull Application app) {
        this(app,((SafeRouteApp)app).api().service);
    }
    public AuthViewModel(Application app, ApiService api) {
        super(app);session=new SessionManager(app);this.api=api;
    }
    public void submit(String name, String email, String password) {
        if (state.getValue().loading) return;
        registered=false; long id=++attempt, generation=session.generation();
        state.setValue(new State(true,false,""));
        call=name==null?api.login(new AuthDto.LoginRequest(email,password)):
            api.register(new AuthDto.RegisterRequest(name,email,password));
        call.enqueue(new Callback<AuthDto.AuthResponse>() {
            public void onResponse(Call<AuthDto.AuthResponse> c, Response<AuthDto.AuthResponse> r) {
                if (id!=attempt) return;
                if (!r.isSuccessful() || r.body()==null) {
                    if(r.errorBody()!=null)r.errorBody().close();
                    fail(authMessage(r.code(),name!=null));return;
                }
                registered=name!=null;
                AuthDto.AuthResponse body=r.body();
                if(body.getAccessToken()==null || body.getAccessToken().isEmpty() || !"bearer".equalsIgnoreCase(body.getTokenType())) {
                    fail("Máy chủ trả thông tin đăng nhập chưa hợp lệ. Vui lòng thử lại.");return;
                }
                verify(body.getAccessToken(),generation,id);
            }
            public void onFailure(Call<AuthDto.AuthResponse> c, Throwable t) {
                if(id==attempt)fail(name==null?"Không kết nối được máy chủ. Kiểm tra mạng rồi thử lại.":
                    "Chưa xác nhận được đăng ký. Hãy thử đăng nhập trước khi đăng ký lại.");
            }
        });
    }
    public void restore() {
        if(state.getValue().loading)return;
        String token=session.getToken();
        if(token==null){fail("Phiên đã hết hạn. Vui lòng đăng nhập lại.");return;}
        state.setValue(new State(true,false,""));verify(token,session.generation(),++attempt);
    }
    private void verify(String token,long generation,long id) {
        call=api.meWithToken("Bearer "+token);
        call.enqueue(new Callback<AuthDto.AuthResponse>() {
            public void onResponse(Call<AuthDto.AuthResponse> c,Response<AuthDto.AuthResponse> r) {
                if(id!=attempt)return;
                if(!r.isSuccessful() || r.body()==null) {
                    if(r.errorBody()!=null)r.errorBody().close();
                    fail((registered?"Tài khoản đã tạo. Hãy đăng nhập để tiếp tục. ":"")+ApiCalls.message(r.code()));return;
                }
                AuthDto.AuthResponse p=r.body();
                if(!SessionManager.knownRole(p.getRole()) || p.getEmail()==null) {
                    fail("Không xác minh được quyền tài khoản. Vui lòng thử lại.");return;
                }
                if(!session.saveIfUnchanged(generation,token,p.getEmail(),p.getRole(),p.getFullName())) {
                    fail("Phiên đã thay đổi. Vui lòng đăng nhập lại.");return;
                }
                state.setValue(new State(false,true,""));
            }
            public void onFailure(Call<AuthDto.AuthResponse> c,Throwable t) {
                if(id==attempt)fail(registered?"Tài khoản đã tạo nhưng chưa xác minh được phiên. Hãy đăng nhập lại.":
                    "Chưa kết nối được máy chủ để xác minh tài khoản. Kiểm tra mạng rồi thử lại.");
            }
        });
    }
    public void consumeSuccess(){state.setValue(new State(false,false,""));}
    private void fail(String message){state.setValue(new State(false,false,message));}
    private static String authMessage(int code,boolean register) {
        if(code==401)return "Email hoặc mật khẩu không đúng.";
        if(code==409 && register)return "Email đã được đăng ký. Hãy chuyển sang Đăng nhập.";
        return ApiCalls.message(code);
    }
    @Override protected void onCleared(){attempt++;if(call!=null)call.cancel();}
}
