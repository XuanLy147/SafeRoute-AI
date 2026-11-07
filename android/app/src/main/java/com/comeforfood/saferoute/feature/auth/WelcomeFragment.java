package com.comeforfood.saferoute.feature.auth;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import com.comeforfood.saferoute.R;
import com.comeforfood.saferoute.databinding.FragmentWelcomeBinding;

/**
 * Screen 1: Splash / Welcome Screen (Owned by TV2)
 * Features SafeRoute Logo, Slogan "Cùng bạn về nhà an toàn", and "Bắt đầu" CTA
 */
public class WelcomeFragment extends Fragment {

    private FragmentWelcomeBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentWelcomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Click "Bắt đầu" -> Go to Login Screen
        binding.btnStart.setOnClickListener(v -> {
            Navigation.findNavController(v).navigate(R.id.action_welcome_to_login);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

