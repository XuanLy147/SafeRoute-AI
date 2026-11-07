package com.comeforfood.saferoute.feature.auth;

import android.os.Bundle;
import android.util.Patterns;
import android.view.*;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.comeforfood.saferoute.*;
import com.comeforfood.saferoute.databinding.FragmentLoginBinding;
import com.comeforfood.saferoute.feature.shared.Tv2Navigation;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class LoginFragment extends Fragment {
    private FragmentLoginBinding binding;
    private AuthViewModel model;
    @Override public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent, Bundle saved) {
        binding=FragmentLoginBinding.inflate(inflater,parent,false);return binding.getRoot();
    }
    @Override public void onViewCreated(@NonNull View view,Bundle saved) {
        model=new ViewModelProvider(this).get(AuthViewModel.class);
        binding.btnAuthBack.setOnClickListener(v->Tv2Navigation.back(this));
        binding.tvGoToRegister.setOnClickListener(v->Tv2Navigation.navigate(this,R.id.action_login_to_register));
        binding.btnGuest.setOnClickListener(v->((MainActivity)requireActivity()).openMap());
        binding.tvForgotPassword.setOnClickListener(v->new MaterialAlertDialogBuilder(requireContext())
            .setTitle("Quên mật khẩu").setMessage("Khôi phục mật khẩu qua email chưa được cấu hình. Liên hệ quản trị viên để được hỗ trợ; không gửi mật khẩu hoặc mã xác thực cho người khác.")
            .setPositiveButton("Đã hiểu",null).show());
        binding.btnLogin.setOnClickListener(v->submit());
        binding.etPassword.setOnEditorActionListener((v,id,event)->{
            if(id==android.view.inputmethod.EditorInfo.IME_ACTION_DONE){submit();return true;}return false;
        });
        model.state.observe(getViewLifecycleOwner(),s->{
            binding.progressBar.setVisibility(s.loading?View.VISIBLE:View.GONE);
            binding.btnLogin.setEnabled(!s.loading);
            binding.btnLogin.setText(s.loading?"Đang đăng nhập…":getString(R.string.btn_login));
            binding.etEmail.setEnabled(!s.loading);binding.etPassword.setEnabled(!s.loading);
            binding.btnAuthBack.setEnabled(!s.loading);
            binding.tvGoToRegister.setEnabled(!s.loading);binding.btnGuest.setEnabled(!s.loading);binding.tvForgotPassword.setEnabled(!s.loading);
            binding.authMessage.setText(s.message);
            binding.authMessage.setVisibility(s.message.isEmpty()?View.GONE:View.VISIBLE);
            if(s.complete){model.consumeSuccess();binding.etPassword.setText("");((MainActivity)requireActivity()).openSessionHome();}
        });
        if(getArguments()!=null && getArguments().getBoolean("expired")) {
            binding.authMessage.setText("Phiên đã hết hạn. Vui lòng đăng nhập lại để tiếp tục.");
            binding.authMessage.setVisibility(View.VISIBLE);
        }
    }
    private void submit() {
        if(model.state.getValue().loading)return;
        String email=binding.etEmail.getText().toString().trim();
        String password=binding.etPassword.getText().toString();
        binding.etEmail.setError(null);binding.etPassword.setError(null);

        if(email.length()>254 || !Patterns.EMAIL_ADDRESS.matcher(email).matches()){
            binding.etEmail.setError(getString(R.string.err_invalid_email));binding.etEmail.requestFocus();return;
        }
        if(password.codePointCount(0,password.length())<6 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72){
            binding.etPassword.setError("Dùng ít nhất 6 ký tự và không quá 72 byte. Chữ có dấu chiếm nhiều byte hơn.");
            binding.etPassword.requestFocus();return;
        }

        model.submit(null,email,password);
    }
    @Override public void onDestroyView(){binding=null;super.onDestroyView();}
}
