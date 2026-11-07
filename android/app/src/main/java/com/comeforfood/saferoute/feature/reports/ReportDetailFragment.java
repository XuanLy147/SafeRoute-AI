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
import com.comeforfood.saferoute.feature.shared.DisplayText;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

public class ReportDetailFragment extends Fragment {
    private FragmentFlowPageBinding binding;
    private boolean pending;
    private LinearLayout votes;
    private String reasonText="";
    public static ReportDetailFragment at(String id) {
        ReportDetailFragment f=new ReportDetailFragment();Bundle args=new Bundle();args.putString("id",id);f.setArguments(args);return f;
    }
    @Override public View onCreateView(@NonNull LayoutInflater inflater,ViewGroup parent,Bundle saved) {
        binding=FragmentFlowPageBinding.inflate(inflater,parent,false);return binding.getRoot();
    }
    @Override public void onViewCreated(@NonNull View view,Bundle saved) {
        if(saved!=null)reasonText=saved.getString("reason","");
        binding.flowBack.setOnClickListener(v->((MainActivity)requireActivity()).navigateBack());load();
    }
    private ApiClient client(){return ((SafeRouteApp)requireActivity().getApplication()).api();}
    private String id(){return requireArguments().getString("id");}
    private void load() {
        binding.flowTitle.setText("Chi tiết báo cáo #"+id());binding.flowBody.setText("Đang tải…");binding.flowActions.removeAllViews();votes=null;
        ApiCalls.enqueue(getViewLifecycleOwner(),client().service.detail(id()),this::render,error->{binding.flowBody.setText(error);button("Tải lại",this::load);});
    }
    private void render(ReportDto.ReportResponse r) {
        binding.flowActions.removeAllViews();
        StringBuilder text=new StringBuilder(r.getStatusDisplay()+"\n"+com.comeforfood.saferoute.core.model.HazardLabel.display(r.getCategoryUser())+" · "+DisplayText.severity(r.getSeverity())+"\n"+r.getLatitude()+", "+r.getLongitude()+"\n"+DisplayText.time(r.getCreatedAt())+"\n"+(r.getDescription()==null?"":r.getDescription())
            +"\nĐồng tình: "+r.getSupportVotes()+" · Phản đối: "+r.getOpposeVotes()+" · Chưa rõ: "+r.getUnsureVotes());
        if(r.getModelVersion()!=null)text.append("\nAI: ").append(r.getModelVersion());
        if(r.getAiDetections()!=null)for(DetectionDto d:r.getAiDetections())text.append("\n").append(d.getDisplayName()).append(" · ").append(Math.round(d.getConfidence()*100)).append("% (độ tin cậy AI)");
        for(ReportDto.AuditLogEntry a:r.getAuditLogs())text.append("\n").append(DisplayText.time(a.getTimestamp())).append(" · ").append(DisplayText.status(a.getNewStatus())).append(" · ").append(a.getReason());
        binding.flowBody.setText(text);
        if(r.getImageUrl()!=null) {
            ImageView image=new ImageView(requireContext());image.setContentDescription("Ảnh báo cáo");image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setImageResource(R.drawable.ic_camera);binding.flowActions.addView(image,new LinearLayout.LayoutParams(-1,dp(240)));
            TextView imageStatus=label(binding.flowActions,"Đang tải ảnh…");
            PrivateImage.load(getViewLifecycleOwner(),client(),r.getImageUrl(),image,loaded->{
                image.setVisibility(loaded?View.VISIBLE:View.GONE);
                imageStatus.setText(loaded?"":"Ảnh không khả dụng hoặc bạn không có quyền xem. Bạn vẫn có thể đọc báo cáo và bình chọn.");
                imageStatus.setVisibility(loaded?View.GONE:View.VISIBLE);
            });
        }
        SessionManager session=new SessionManager(requireContext());
        if(session.isLoggedIn()) {
            votes=new LinearLayout(requireContext());votes.setOrientation(LinearLayout.VERTICAL);binding.flowActions.addView(votes);loadVote();
        }else button("Đăng nhập để bình chọn",()->((MainActivity)requireActivity()).openTv2Fragment(new com.comeforfood.saferoute.feature.auth.LoginFragment()));
        if(session.isReviewerOrAdmin() && ReportDto.STATUS_PENDING.equals(r.getStatus())) {
            EditText reason=new EditText(requireContext());reason.setHint("Lý do xác minh hoặc từ chối (ít nhất 5 ký tự)");
            reason.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            reason.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(1000)});reason.setText(reasonText);
            reason.addTextChangedListener(new android.text.TextWatcher(){
                public void beforeTextChanged(CharSequence s,int start,int count,int after){}
                public void onTextChanged(CharSequence s,int start,int before,int count){reasonText=s.toString();}
                public void afterTextChanged(android.text.Editable s){}
            });
            binding.flowActions.addView(reason);
            button("Xác minh",()->review("VERIFIED",reason));button("Từ chối",()->review("REJECTED",reason));
        }
        button("Tải lại",this::load);
    }
    private void loadVote() {
        LinearLayout target=votes;target.removeAllViews();label(target,"Đang kiểm tra bình chọn của bạn…");
        ApiCalls.enqueueDetailed(getViewLifecycleOwner(),client().service.myVote(id()),state->{
            if(votes!=target)return;
            target.removeAllViews();
            if(state.vote!=null)label(target,"Bạn đã bình chọn: "+voteLabel(state.vote)+". Mỗi tài khoản được bình chọn một lần.");
            else {
                voteButton(target,"Đồng tình","SUPPORT");voteButton(target,"Phản đối","OPPOSE");voteButton(target,"Chưa rõ","UNSURE");
            }
            setActions(!pending);
        },(code,error)->{
            if(votes!=target)return;
            target.removeAllViews();label(target,"Chưa tải được bình chọn của bạn. "+error);
            button(target,"Kiểm tra lại bình chọn",this::loadVote);
        });
    }
    private String voteLabel(String value){return "SUPPORT".equals(value)?"Đồng tình":"OPPOSE".equals(value)?"Phản đối":"Chưa rõ";}
    private void voteButton(LinearLayout parent,String text,String type){button(parent,text,()->vote(type));}
    private void vote(String type) {
        if(pending)return;pending=true;setActions(false);
        ApiCalls.enqueueDetailed(getViewLifecycleOwner(),client().service.vote(id(),new ReportDto.VoteRequest(type,null)),r->{pending=false;load();},(code,error)->{
            pending=false;setActions(true);
            if(code==409){
                votes.removeAllViews();label(votes,"Bạn đã bình chọn cho báo cáo này. Bình chọn không được gửi thêm.");
                button(votes,"Xem bình chọn đã gửi",this::loadVote);
            } else {
                Snackbar.make(binding.getRoot(),error,Snackbar.LENGTH_LONG).show();
                loadVote(); // A failed write may have committed. Read state before another submission.
            }
        });
    }
    private void review(String status,EditText reason) {
        String value=reason.getText().toString().trim();if(value.length()<5){reason.setError("Nhập ít nhất 5 ký tự");return;}
        if(pending)return;pending=true;setActions(false);
        ApiCalls.enqueueDetailed(getViewLifecycleOwner(),client().service.review(id(),new ReportDto.ReviewDecisionRequest(status,value,null)),r->{
            pending=false;reasonText="";getParentFragmentManager().setFragmentResult(MainActivity.REPORTS_CHANGED,new Bundle());load();
        },(code,error)->{
            pending=false;
            // Preserve the reason and prohibit another write until the current server state is read.
            Snackbar.make(binding.getRoot(),code==409?"Báo cáo đã được xử lý. Tải lại để xem quyết định mới nhất.":error,Snackbar.LENGTH_LONG).show();
            load();
        });
    }
    private void setActions(boolean enabled){setEnabled(binding.flowActions,enabled);}
    private void setEnabled(View view,boolean enabled){
        view.setEnabled(enabled);if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)setEnabled(((ViewGroup)view).getChildAt(i),enabled);
    }
    private TextView label(LinearLayout parent,String text){
        TextView v=new TextView(requireContext());v.setText(text);v.setTextSize(16);v.setTextColor(requireContext().getColor(R.color.muted));v.setPadding(0,dp(8),0,dp(8));parent.addView(v);return v;
    }
    private void button(String text,Runnable run){button(binding.flowActions,text,run);}
    private void button(LinearLayout parent,String text,Runnable run){
        MaterialButton b=new MaterialButton(requireContext());b.setText(text);b.setAllCaps(false);b.setMinHeight(dp(48));parent.addView(b);b.setOnClickListener(v->{if(!pending)run.run();});
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onSaveInstanceState(@NonNull Bundle out){super.onSaveInstanceState(out);out.putString("reason",reasonText);}
    @Override public void onDestroyView(){binding=null;votes=null;pending=false;super.onDestroyView();}
}
