package com.comeforfood.saferoute.feature.auth;

import android.os.Bundle;
import android.util.Patterns;
import android.view.*;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.comeforfood.saferoute.*;
import com.comeforfood.saferoute.databinding.FragmentRegisterBinding;
import com.comeforfood.saferoute.feature.shared.Tv2Navigation;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class RegisterFragment extends Fragment {
    private FragmentRegisterBinding binding;
    private AuthViewModel model;
    @Override public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent, Bundle saved) {
        binding=FragmentRegisterBinding.inflate(inflater,parent,false);return binding.getRoot();
    }
    @Override public void onViewCreated(@NonNull View view,Bundle saved) {
        model=new ViewModelProvider(this).get(AuthViewModel.class);
        binding.btnAuthBack.setOnClickListener(v->Tv2Navigation.back(this));
        binding.tvGoToLogin.setOnClickListener(v->Tv2Navigation.back(this));
        binding.etFullName.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(200)});
        binding.btnRegister.setOnClickListener(v->submit());
        binding.etConfirmPassword.setOnEditorActionListener((v,id,event)->{
            if(id==android.view.inputmethod.EditorInfo.IME_ACTION_DONE){submit();return true;}return false;
        });
        model.state.observe(getViewLifecycleOwner(),s->{
            binding.progressBar.setVisibility(s.loading?View.VISIBLE:View.GONE);
            binding.btnRegister.setEnabled(!s.loading);
            binding.btnRegister.setText(s.loading?"Đang tạo tài khoản…":getString(R.string.btn_register));
            binding.etEmail.setEnabled(!s.loading);binding.etPassword.setEnabled(!s.loading);
            binding.btnAuthBack.setEnabled(!s.loading);
            binding.etFullName.setEnabled(!s.loading);binding.etConfirmPassword.setEnabled(!s.loading);binding.tvGoToLogin.setEnabled(!s.loading);
            binding.authMessage.setText(s.message);
            binding.authMessage.setVisibility(s.message.isEmpty()?View.GONE:View.VISIBLE);
            if(s.complete){model.consumeSuccess();binding.etPassword.setText("");binding.etConfirmPassword.setText("");((MainActivity)requireActivity()).openSessionHome();}
        });

    }
    private void submit() {
        if(model.state.getValue().loading)return;
        String email=binding.etEmail.getText().toString().trim();
        String password=binding.etPassword.getText().toString();
        binding.etEmail.setError(null);binding.etPassword.setError(null);
        String name=binding.etFullName.getText().toString().trim();
        binding.etFullName.setError(null);binding.etConfirmPassword.setError(null);
        if(name.isEmpty()){binding.etFullName.setError("Nhập họ và tên");binding.etFullName.requestFocus();return;}
        if(email.length()>254 || !Patterns.EMAIL_ADDRESS.matcher(email).matches()){
            binding.etEmail.setError(getString(R.string.err_invalid_email));binding.etEmail.requestFocus();return;
        }
        if(password.codePointCount(0,password.length())<6 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72){
            binding.etPassword.setError("Dùng ít nhất 6 ký tự và không quá 72 byte. Chữ có dấu chiếm nhiều byte hơn.");
            binding.etPassword.requestFocus();return;
        }
        if(!password.equals(binding.etConfirmPassword.getText().toString())){
            binding.etConfirmPassword.setError(getString(R.string.err_password_mismatch));binding.etConfirmPassword.requestFocus();return;
        }
        model.submit(name,email,password);
    }
    @Override public void onDestroyView(){binding=null;super.onDestroyView();}
}
