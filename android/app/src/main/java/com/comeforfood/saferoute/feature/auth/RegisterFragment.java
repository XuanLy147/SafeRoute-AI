package com.comeforfood.saferoute.feature.auth;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import com.comeforfood.saferoute.R;
import com.comeforfood.saferoute.core.session.SessionManager;
import com.comeforfood.saferoute.databinding.FragmentRegisterBinding;

/**
 * Screen 3: Register Screen (Owned by TV2)
 * Features Full Name, Email, Password, Confirm Password,
 * "ĐĂNG KÝ" CTA, and return link to Login.
 */
public class RegisterFragment extends Fragment {

    private FragmentRegisterBinding binding;
    private SessionManager sessionManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentRegisterBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        sessionManager = new SessionManager(requireContext());

        // Navigate back to Login
        binding.tvGoToLogin.setOnClickListener(v -> {
            Navigation.findNavController(v).navigateUp();
        });

        // Submit Register
        binding.btnRegister.setOnClickListener(v -> attemptRegister());

        // Social triggers
        binding.btnGoogle.setOnClickListener(v -> {
            Toast.makeText(requireContext(), "Đăng ký bằng Google đang được tích hợp", Toast.LENGTH_SHORT).show();
        });

        binding.btnFacebook.setOnClickListener(v -> {
            Toast.makeText(requireContext(), "Đăng ký bằng Facebook đang được tích hợp", Toast.LENGTH_SHORT).show();
        });
    }

    private void attemptRegister() {
        String fullName = binding.etFullName.getText() != null ? binding.etFullName.getText().toString().trim() : "";
        String email = binding.etEmail.getText() != null ? binding.etEmail.getText().toString().trim() : "";
        String password = binding.etPassword.getText() != null ? binding.etPassword.getText().toString().trim() : "";
        String confirmPassword = binding.etConfirmPassword.getText() != null ? binding.etConfirmPassword.getText().toString().trim() : "";

        // Client-side validations
        if (TextUtils.isEmpty(fullName)) {
            binding.etFullName.setError(getString(R.string.err_empty_fields));
            binding.etFullName.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(email)) {
            binding.etEmail.setError(getString(R.string.err_empty_fields));
            binding.etEmail.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.setError(getString(R.string.err_invalid_email));
            binding.etEmail.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            binding.etPassword.setError(getString(R.string.err_empty_fields));
            binding.etPassword.requestFocus();
            return;
        }

        if (password.length() < 6) {
            binding.etPassword.setError(getString(R.string.err_password_short));
            binding.etPassword.requestFocus();
            return;
        }

        if (!password.equals(confirmPassword)) {
            binding.etConfirmPassword.setError(getString(R.string.err_password_mismatch));
            binding.etConfirmPassword.requestFocus();
            return;
        }

        setLoading(true);

        // Save registered user state for seamless login
        sessionManager.saveSession("token_registered", email, "USER", fullName);

        binding.getRoot().postDelayed(() -> {
            if (isAdded() && binding != null) {
                setLoading(false);
                Toast.makeText(requireContext(), getString(R.string.msg_register_success), Toast.LENGTH_SHORT).show();
                // Navigate back to Login so user can log in
                Navigation.findNavController(binding.getRoot()).navigateUp();
            }
        }, 600);
    }

    private void setLoading(boolean isLoading) {
        if (binding == null) return;
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.btnRegister.setEnabled(!isLoading);
        binding.btnRegister.setText(isLoading ? "" : getString(R.string.btn_register));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

