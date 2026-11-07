package com.comeforfood.saferoute.feature.reports;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import com.comeforfood.saferoute.*;
import com.comeforfood.saferoute.core.model.*;
import com.comeforfood.saferoute.core.network.*;
import com.comeforfood.saferoute.core.session.SessionManager;
import com.comeforfood.saferoute.databinding.FragmentFlowPageBinding;
import com.google.android.material.button.MaterialButton;

public class ReportDetailFragment extends Fragment {
    private FragmentFlowPageBinding binding;
    private boolean pending;
    public static ReportDetailFragment at(String id) {
        ReportDetailFragment f=new ReportDetailFragment();Bundle args=new Bundle();args.putString("id",id);f.setArguments(args);return f;
    }
    @Override public View onCreateView(@NonNull LayoutInflater inflater,ViewGroup parent,Bundle saved) {
        binding=FragmentFlowPageBinding.inflate(inflater,parent,false);return binding.getRoot();
    }
    @Override public void onViewCreated(@NonNull View view,Bundle saved) {
        binding.flowBack.setOnClickListener(v->((MainActivity)requireActivity()).navigateBack());load();
    }
    private ApiClient client(){return ((SafeRouteApp)requireActivity().getApplication()).api();}
    private String id(){return requireArguments().getString("id");}
    private void load() {
        binding.flowTitle.setText("Chi tiết báo cáo #"+id());binding.flowBody.setText("Đang tải…");binding.flowActions.removeAllViews();
        ApiCalls.enqueue(getViewLifecycleOwner(),client().service.detail(id()),this::render,error->{binding.flowBody.setText(error);button("Tải lại",this::load);});
    }
    private void render(ReportDto.ReportResponse r) {
        binding.flowActions.removeAllViews();
        StringBuilder text=new StringBuilder(r.getStatusDisplay()+"\n"+r.getCategoryUser()+" · "+r.getSeverity()+"\n"+r.getLatitude()+", "+r.getLongitude()+"\n"+r.getCreatedAt()+"\n"+r.getDescription()
            +"\nĐồng tình: "+r.getSupportVotes()+" · Phản đối: "+r.getOpposeVotes()+" · Chưa rõ: "+r.getUnsureVotes());
        if(r.getModelVersion()!=null)text.append("\nAI: ").append(r.getModelVersion());
        if(r.getAiDetections()!=null)for(DetectionDto d:r.getAiDetections())text.append("\n").append(d.getDisplayName()).append(" · ").append(Math.round(d.getConfidence()*100)).append("% (độ tin cậy AI)");
        for(ReportDto.AuditLogEntry a:r.getAuditLogs())text.append("\n").append(a.getTimestamp()).append(" · ").append(a.getNewStatus()).append(" · ").append(a.getReason());
        binding.flowBody.setText(text);
        if(r.getImageUrl()!=null) {
            ImageView image=new ImageView(requireContext());image.setContentDescription("Ảnh báo cáo");image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setImageResource(R.drawable.ic_camera);binding.flowActions.addView(image,new android.widget.LinearLayout.LayoutParams(-1,(int)(240*getResources().getDisplayMetrics().density)));
            PrivateImage.load(getViewLifecycleOwner(),client(),r.getImageUrl(),image);
        }
        SessionManager session=new SessionManager(requireContext());
        if(session.isLoggedIn()) {
            button("Đồng tình",()->vote("SUPPORT"));button("Phản đối",()->vote("OPPOSE"));button("Chưa rõ",()->vote("UNSURE"));
        }else button("Đăng nhập để bình chọn",()->((MainActivity)requireActivity()).openTv2Fragment(new com.comeforfood.saferoute.feature.auth.LoginFragment()));
        if(session.isReviewerOrAdmin() && ReportDto.STATUS_PENDING.equals(r.getStatus())) {
            EditText reason=new EditText(requireContext());reason.setHint("Lý do xác minh hoặc từ chối (ít nhất 5 ký tự)");reason.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);binding.flowActions.addView(reason);
            button("Xác minh",()->review("VERIFIED",reason));button("Từ chối",()->review("REJECTED",reason));
        }
        button("Tải lại",this::load);
    }
    private void vote(String type) {
        if(pending)return;pending=true;setActions(false);
        ApiCalls.enqueue(getViewLifecycleOwner(),client().service.vote(id(),new ReportDto.VoteRequest(type,null)),r->{pending=false;load();},this::failedWrite);
    }
    private void review(String status,EditText reason) {
        String value=reason.getText().toString().trim();if(value.length()<5){reason.setError("Nhập ít nhất 5 ký tự");return;}
        if(pending)return;pending=true;setActions(false);
        ApiCalls.enqueue(getViewLifecycleOwner(),client().service.review(id(),new ReportDto.ReviewDecisionRequest(status,value,null)),r->{pending=false;getParentFragmentManager().setFragmentResult(MainActivity.REPORTS_CHANGED,new Bundle());load();},this::failedWrite);
    }
    private void failedWrite(String error) {
        pending=false;binding.flowActions.removeAllViews();binding.flowBody.setText(error);button("Tải trạng thái hiện tại",this::load);
    }
    private void setActions(boolean enabled){for(int i=0;i<binding.flowActions.getChildCount();i++)binding.flowActions.getChildAt(i).setEnabled(enabled);}
    private void button(String text,Runnable run){MaterialButton b=new MaterialButton(requireContext());b.setText(text);b.setAllCaps(false);b.setMinHeight((int)(48*getResources().getDisplayMetrics().density));binding.flowActions.addView(b);b.setOnClickListener(v->run.run());}
    @Override public void onDestroyView(){binding=null;pending=false;super.onDestroyView();}
}
