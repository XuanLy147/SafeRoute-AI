package com.comeforfood.saferoute.feature.reports;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import com.comeforfood.saferoute.R;
import com.comeforfood.saferoute.databinding.FragmentMyReportsBinding;

public class MyReportsFragment extends Fragment {

    private FragmentMyReportsBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentMyReportsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        binding.btnBack.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());
        binding.btnReportDetail.setOnClickListener(v -> showDetail());
        binding.btnDetailBack.setOnClickListener(v -> showList());
        binding.btnOpenProfile.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.action_myReports_to_profile));
        binding.chipGroupFilter.setOnCheckedStateChangeListener((group, checkedIds) -> {
            int filterId = checkedIds.isEmpty() ? R.id.chipFilterAll : checkedIds.get(0);
            binding.reportPending.setVisibility(filterId == R.id.chipFilterAll
                    || filterId == R.id.chipFilterPending ? View.VISIBLE : View.GONE);
            binding.reportVerified.setVisibility(filterId == R.id.chipFilterAll
                    || filterId == R.id.chipFilterVerified ? View.VISIBLE : View.GONE);
            binding.reportRejected.setVisibility(filterId == R.id.chipFilterAll
                    || filterId == R.id.chipFilterRejected ? View.VISIBLE : View.GONE);
        });
        showList();
    }

    private void showList() {
        if (binding == null) return;
        binding.listContent.setVisibility(View.VISIBLE);
        binding.detailContent.setVisibility(View.GONE);
        binding.tvTitle.setText("Báo cáo của tôi");
    }

    private void showDetail() {
        if (binding == null) return;
        binding.listContent.setVisibility(View.GONE);
        binding.detailContent.setVisibility(View.VISIBLE);
        binding.tvTitle.setText("Theo dõi báo cáo");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
