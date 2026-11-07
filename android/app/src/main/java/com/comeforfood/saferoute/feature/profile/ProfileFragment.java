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
            if (session.isLoggedIn()) { ((com.comeforfood.saferoute.MainActivity)requireActivity()).logout(); return; }
            ((com.comeforfood.saferoute.MainActivity) requireActivity()).openTv2Fragment(new com.comeforfood.saferoute.feature.auth.LoginFragment());
        });
        binding.btnAccount.setText(session.isLoggedIn() ? R.string.flow_logout : R.string.profile_sign_in);
        binding.btnSaved.setOnClickListener(v -> ((com.comeforfood.saferoute.MainActivity)requireActivity()).openFlow(com.comeforfood.saferoute.feature.shared.FlowPageFragment.Page.SAVED));
        binding.btnNotifications.setOnClickListener(v -> ((com.comeforfood.saferoute.MainActivity)requireActivity()).openFlow(com.comeforfood.saferoute.feature.shared.FlowPageFragment.Page.NOTIFICATIONS));
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
        binding.btnSettings.setOnClickListener(v -> ((com.comeforfood.saferoute.MainActivity)requireActivity()).openFlow(com.comeforfood.saferoute.feature.shared.FlowPageFragment.Page.SETTINGS));
        binding.btnPrivacy.setOnClickListener(v -> ((com.comeforfood.saferoute.MainActivity)requireActivity()).openFlow(com.comeforfood.saferoute.feature.shared.FlowPageFragment.Page.PRIVACY));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
