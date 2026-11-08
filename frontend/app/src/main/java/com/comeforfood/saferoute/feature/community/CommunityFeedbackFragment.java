package com.comeforfood.saferoute.feature.community;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.comeforfood.saferoute.feature.shared.AppNavigation;
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
        if (getArguments()!=null) binding.feedbackReportTitle.setText(getArguments().getString("report.title",getString(com.comeforfood.saferoute.R.string.flow_alert_detail)));
        binding.btnBack.setOnClickListener(v -> AppNavigation.back(this));
        binding.btnSendFeedback.setOnClickListener(v -> {
            String id=getArguments()==null?null:getArguments().getString("report.id");
            if(id==null){Toast.makeText(requireContext(),"Chọn một báo cáo trên bản đồ để bình chọn.",Toast.LENGTH_LONG).show();return;}
            int selected=binding.chipGroupFeedback.getCheckedChipId();
            String type=selected==com.comeforfood.saferoute.R.id.chipSupport?"SUPPORT":selected==com.comeforfood.saferoute.R.id.chipOppose?"OPPOSE":selected==com.comeforfood.saferoute.R.id.chipUnsure?"UNSURE":null;
            if(type==null){Toast.makeText(requireContext(),"Chọn một ý kiến trước khi gửi.",Toast.LENGTH_LONG).show();return;}
            binding.btnSendFeedback.setEnabled(false);
            com.comeforfood.saferoute.core.network.ApiCalls.enqueue(getViewLifecycleOwner(),((com.comeforfood.saferoute.SafeRouteApp)requireActivity().getApplication()).api().service.vote(id,new com.comeforfood.saferoute.core.model.ReportDto.VoteRequest(type,binding.etFeedback.getText().toString())),response->{
                Toast.makeText(requireContext(),"Đã ghi nhận bình chọn.",Toast.LENGTH_LONG).show();
                ((com.comeforfood.saferoute.MainActivity)requireActivity()).openFeatureFragment(com.comeforfood.saferoute.feature.reports.ReportDetailFragment.at(id));
            },error->{binding.btnSendFeedback.setEnabled(true);Toast.makeText(requireContext(),error,Toast.LENGTH_LONG).show();});
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
