package com.comeforfood.saferoute.feature.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.comeforfood.saferoute.feature.shared.Tv2Navigation;
import com.comeforfood.saferoute.R;
import com.comeforfood.saferoute.core.session.SessionManager;
import com.comeforfood.saferoute.databinding.FragmentProfileBinding;

public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        binding.btnBack.setOnClickListener(v -> Tv2Navigation.back(this));
        SessionManager session = new SessionManager(requireContext());
        binding.btnAccount.setOnClickListener(v -> {
            if (session.isLoggedIn()) session.clearSession();
            ((com.comeforfood.saferoute.MainActivity) requireActivity()).openTv2Fragment(new com.comeforfood.saferoute.feature.auth.LoginFragment());
        });
        binding.tvRole.setVisibility(session.isLoggedIn() ? View.VISIBLE : View.GONE);
        String name = session.getFullName();
        String email = session.getEmail();
        binding.tvFullName.setText(name.isEmpty() ? "Người dùng SafeRoute" : name);
        binding.tvEmail.setText(email.isEmpty() ? "Chưa đăng nhập" : email);
        binding.tvRole.setText(session.isLoggedIn() ? session.getRole() : getString(R.string.account_guest));
        binding.btnMyReports.setOnClickListener(v ->
                Tv2Navigation.navigate(this, R.id.action_profile_to_myReports));
        binding.btnFeedback.setOnClickListener(v ->
                Tv2Navigation.navigate(this, R.id.action_profile_to_feedback));
        binding.btnReviewer.setVisibility(session.isReviewerOrAdmin() ? View.VISIBLE : View.GONE);
        binding.btnReviewer.setOnClickListener(v ->
                Tv2Navigation.navigate(this, R.id.action_profile_to_reviewer));
        binding.btnSettings.setOnClickListener(v -> showSection(binding.settingsContent));
        binding.btnPrivacy.setOnClickListener(v -> showSection(binding.privacyContent));
        binding.btnSectionBack.setOnClickListener(v -> showSection(null));
    }

    private void showSection(@Nullable View section) {
        if (binding == null) return;
        binding.profileContent.setVisibility(section == null ? View.VISIBLE : View.GONE);
        binding.settingsContent.setVisibility(section == binding.settingsContent ? View.VISIBLE : View.GONE);
        binding.privacyContent.setVisibility(section == binding.privacyContent ? View.VISIBLE : View.GONE);
        binding.sectionBackBar.setVisibility(section == null ? View.GONE : View.VISIBLE);
        binding.tvTitle.setText(section == binding.settingsContent ? "Cài đặt"
                : section == binding.privacyContent ? "Quyền riêng tư" : "Hồ sơ cá nhân");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
