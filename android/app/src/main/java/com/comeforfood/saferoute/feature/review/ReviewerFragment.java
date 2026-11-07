package com.comeforfood.saferoute.feature.review;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.comeforfood.saferoute.feature.shared.Tv2Navigation;
import com.comeforfood.saferoute.core.session.SessionManager;
import com.comeforfood.saferoute.databinding.FragmentReviewerBinding;

public class ReviewerFragment extends Fragment {

    private FragmentReviewerBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentReviewerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        binding.btnBack.setOnClickListener(v -> Tv2Navigation.back(this));
        if (!new SessionManager(requireContext()).isReviewerOrAdmin()) {
            binding.reviewerContent.setVisibility(View.GONE);
            binding.accessDeniedContent.setVisibility(View.VISIBLE);
            return;
        }

        binding.btnOpenQueue.setOnClickListener(v -> showPage(binding.queueContent, "Hàng chờ xét duyệt"));
        binding.btnQueueItem.setOnClickListener(v -> showPage(binding.detailContent, "Hồ sơ xét duyệt"));
        binding.btnOpenDecision.setOnClickListener(v -> showPage(binding.decisionContent, "Quyết định"));
        binding.btnOpenHistory.setOnClickListener(v -> showPage(binding.historyContent, "Lịch sử xét duyệt"));
        binding.btnDetailBack.setOnClickListener(v -> showPage(binding.queueContent, "Hàng chờ xét duyệt"));
        binding.btnDecisionBack.setOnClickListener(v -> showPage(binding.detailContent, "Hồ sơ xét duyệt"));
        binding.btnHistoryBack.setOnClickListener(v -> showPage(binding.homeContent, "Tổng quan reviewer"));
        binding.btnApprove.setOnClickListener(v -> reportUnavailable());
        binding.btnReject.setOnClickListener(v -> {
            if (TextUtils.isEmpty(binding.etRejectReason.getText())) {
                binding.etRejectReason.setError("Vui lòng nhập lý do từ chối");
                return;
            }
            reportUnavailable();
        });
        showPage(binding.homeContent, "Tổng quan reviewer");
    }

    private void showPage(@NonNull View page, @NonNull String title) {
        if (binding == null) return;
        binding.homeContent.setVisibility(page == binding.homeContent ? View.VISIBLE : View.GONE);
        binding.queueContent.setVisibility(page == binding.queueContent ? View.VISIBLE : View.GONE);
        binding.detailContent.setVisibility(page == binding.detailContent ? View.VISIBLE : View.GONE);
        binding.decisionContent.setVisibility(page == binding.decisionContent ? View.VISIBLE : View.GONE);
        binding.historyContent.setVisibility(page == binding.historyContent ? View.VISIBLE : View.GONE);
        binding.tvTitle.setText(title);
    }

    private void reportUnavailable() {
        Toast.makeText(requireContext(),
                "Chưa ghi nhận quyết định: API review chưa được tích hợp.",
                Toast.LENGTH_LONG).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
