package com.comeforfood.saferoute.feature.reports;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.comeforfood.saferoute.feature.shared.Tv2Navigation;
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
        binding.btnBack.setOnClickListener(v -> Tv2Navigation.back(this));
        binding.btnReportDetail.setOnClickListener(v -> showDetail());
        binding.btnDetailBack.setOnClickListener(v -> showList());
        binding.btnOpenProfile.setOnClickListener(v ->
                Tv2Navigation.navigate(this, R.id.action_myReports_to_profile));
        binding.reportPending.setVisibility(View.GONE);
        binding.reportVerified.setVisibility(View.GONE);
        binding.reportRejected.setVisibility(View.GONE);
        binding.chipGroupFilter.setVisibility(View.GONE);
        showList();
        android.widget.LinearLayout rows=new android.widget.LinearLayout(requireContext());rows.setOrientation(android.widget.LinearLayout.VERTICAL);binding.listContent.addView(rows);
        com.google.android.material.button.MaterialButton retry=new com.google.android.material.button.MaterialButton(requireContext());retry.setText("Tải lại");binding.listContent.addView(retry);
        Runnable load=()->{
            retry.setEnabled(false);rows.removeAllViews();binding.reportsUnavailable.setText("Đang tải…");
            com.comeforfood.saferoute.core.network.ApiCalls.enqueue(getViewLifecycleOwner(),((com.comeforfood.saferoute.SafeRouteApp)requireActivity().getApplication()).api().service.mine(),reports->{
                retry.setEnabled(true);binding.reportsUnavailable.setText(reports.isEmpty()?"Chưa có báo cáo":"Báo cáo: "+reports.size());
                for(com.comeforfood.saferoute.core.model.ReportDto.ReportResponse r:reports){
                    com.google.android.material.button.MaterialButton row=new com.google.android.material.button.MaterialButton(requireContext());row.setAllCaps(false);
                    row.setText("#"+r.getId()+" · "+com.comeforfood.saferoute.core.model.HazardLabel.display(r.getCategoryUser())+" · "+r.getStatusDisplay()+"\n"+r.getCreatedAt());rows.addView(row);
                    row.setOnClickListener(v->((com.comeforfood.saferoute.MainActivity)requireActivity()).openTv2Fragment(ReportDetailFragment.at(r.getId())));
                }
            },error->{retry.setEnabled(true);binding.reportsUnavailable.setText(error);});
        };
        retry.setOnClickListener(v->load.run());load.run();
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
