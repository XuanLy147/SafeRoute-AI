package com.comeforfood.saferoute.feature.community;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import com.comeforfood.saferoute.databinding.FragmentCommunityFeedbackBinding;

public class CommunityFeedbackFragment extends Fragment {

    private FragmentCommunityFeedbackBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCommunityFeedbackBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        binding.btnBack.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());
        binding.btnSendFeedback.setOnClickListener(v ->
                Toast.makeText(requireContext(),
                        "Chưa gửi góp ý: API cộng đồng chưa được tích hợp.",
                        Toast.LENGTH_LONG).show());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
