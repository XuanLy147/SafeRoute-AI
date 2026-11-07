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
import com.comeforfood.saferoute.feature.shared.Tv2Navigation;
import com.comeforfood.saferoute.R;
import com.comeforfood.saferoute.core.session.SessionManager;
import com.comeforfood.saferoute.databinding.FragmentLoginBinding;

/**
 * Screen 2: Login Screen (Owned by TV2)
 * Features email/password input, "Quên mật khẩu?", "ĐĂNG NHẬP" CTA,
 * navigation to Register, and Google/Facebook social triggers.
 */
public class LoginFragment extends Fragment {

    private FragmentLoginBinding binding;
    private SessionManager sessionManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentLoginBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        sessionManager = new SessionManager(requireContext());
        binding.btnGuest.setOnClickListener(v -> ((com.comeforfood.saferoute.MainActivity)requireActivity()).openMap());
        binding.btnAuthBack.setOnClickListener(v -> Tv2Navigation.back(this));

        // Pre-fill email if previously registered or entered
        if (!TextUtils.isEmpty(sessionManager.getEmail())) {
            binding.etEmail.setText(sessionManager.getEmail());
        }

        // Navigate to Register screen
        binding.tvGoToRegister.setOnClickListener(v -> {
            Tv2Navigation.navigate(this, R.id.action_login_to_register);
        });

        // Forgot password click
        binding.tvForgotPassword.setOnClickListener(v -> {
            Toast.makeText(requireContext(), "Tính năng quên mật khẩu đang được xử lý", Toast.LENGTH_SHORT).show();
        });

        // Submit Login
        binding.btnLogin.setOnClickListener(v -> attemptLogin());

        // Social login handlers
        binding.btnGoogle.setOnClickListener(v -> {
            Toast.makeText(requireContext(), "Đăng nhập Google đang được tích hợp", Toast.LENGTH_SHORT).show();
        });

        binding.btnFacebook.setOnClickListener(v -> {
            Toast.makeText(requireContext(), "Đăng nhập Facebook đang được tích hợp", Toast.LENGTH_SHORT).show();
        });
    }

    private void attemptLogin() {
        String email = binding.etEmail.getText() != null ? binding.etEmail.getText().toString().trim() : "";
        String password = binding.etPassword.getText() != null ? binding.etPassword.getText().toString() : "";

        // Client-side validation
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

        Toast.makeText(requireContext(), R.string.auth_unavailable, Toast.LENGTH_LONG).show();
    }

    private void setLoading(boolean isLoading) {
        if (binding == null) return;
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.btnLogin.setEnabled(!isLoading);
        binding.btnLogin.setText(isLoading ? "" : getString(R.string.btn_login));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
