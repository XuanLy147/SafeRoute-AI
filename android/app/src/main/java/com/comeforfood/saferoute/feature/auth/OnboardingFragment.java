package com.comeforfood.saferoute.feature.auth;

import android.os.Bundle;
import android.view.*;
import android.widget.LinearLayout;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import com.comeforfood.saferoute.MainActivity;
import com.comeforfood.saferoute.R;
import com.comeforfood.saferoute.databinding.FragmentOnboardingBinding;

/** Three introductions followed by contextual permission education; no permission is granted here. */
public class OnboardingFragment extends Fragment {
    private FragmentOnboardingBinding binding;
    public static OnboardingFragment at(int step) {
        OnboardingFragment fragment = new OnboardingFragment();
        Bundle args = new Bundle(); args.putInt("step", step); fragment.setArguments(args); return fragment;
    }
    @Override public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent, Bundle saved) {
        binding = FragmentOnboardingBinding.inflate(inflater, parent, false); return binding.getRoot();
    }
    @Override public void onViewCreated(@NonNull View view, Bundle saved) {
        int step = Math.max(0, Math.min(3, requireArguments().getInt("step")));
        int[] titles = {R.string.intro_map_title, R.string.intro_ai_title, R.string.intro_community_title, R.string.intro_permissions_title};
        int[] descriptions = {R.string.intro_map_body, R.string.intro_ai_body, R.string.intro_community_body, R.string.intro_permissions_body};
        int[] illustrations = {
                R.drawable.onboarding_nearby_hero,
                R.drawable.onboarding_camera_hero,
                R.drawable.onboarding_community_hero,
                R.drawable.onboarding_access_hero
        };
        binding.introTitle.setText(titles[step]); binding.introBody.setText(descriptions[step]);
        binding.introIcon.setImageResource(illustrations[step]);
        binding.introIcon.setImageTintList(null);
        binding.introIcon.setBackground(null);
        binding.introIcon.setPadding(0, 0, 0, 0);
        setIconSize(step < 2 ? 240 : 200, step < 2 ? 180 : 200, 24);
        binding.introProgress.setText(getString(R.string.intro_progress, step + 1));
        binding.introBack.setOnClickListener(v -> ((MainActivity)requireActivity()).navigateBack());
        binding.introNext.setOnClickListener(v -> {
            MainActivity host = (MainActivity)requireActivity();
            if (step < 3) host.openTv2Fragment(at(step + 1));
            else host.finishIntroduction();
        });
        binding.introSkip.setOnClickListener(v -> ((MainActivity)requireActivity()).finishIntroduction());
    }
    private void setIconSize(int width, int height, int topMargin) {
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) binding.introIcon.getLayoutParams();
        params.width = dp(width);
        params.height = dp(height);
        params.topMargin = dp(topMargin);
        binding.introIcon.setLayoutParams(params);
    }
    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
    @Override public void onDestroyView() { binding = null; super.onDestroyView(); }
}
