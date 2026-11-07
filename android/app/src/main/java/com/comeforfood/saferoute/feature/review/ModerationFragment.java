package com.comeforfood.saferoute.feature.review;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import com.comeforfood.saferoute.*;
import com.comeforfood.saferoute.core.network.*;
import com.comeforfood.saferoute.core.session.SessionManager;
import com.comeforfood.saferoute.databinding.FragmentFlowPageBinding;
import com.comeforfood.saferoute.feature.shared.DisplayText;
import com.comeforfood.saferoute.feature.shared.FlowPageFragment.Page;
import com.comeforfood.saferoute.feature.reports.ReportDetailFragment;
import com.google.android.material.button.MaterialButton;

/** Paginated, server-authorized moderation reads; never changes a report implicitly. */
public class ModerationFragment extends Fragment {
    private FragmentFlowPageBinding b;
    private ApiService api;
    private LinearLayout rows;
    private EditText search;
    private Spinner filter;
    private MaterialButton refresh,previous,next;
    private int offset,statusIndex;
    private String query="";
    private boolean loading;
    public static boolean supports(Page page) {
        return page==Page.REVIEW_HISTORY||page==Page.ADMIN_AUDIT||page==Page.ADMIN_REPORTS
            ||page==Page.DUPLICATES||page==Page.ADMIN_METRICS||page==Page.ADMIN_SETTINGS;
    }
    public static ModerationFragment at(Page page) {
        ModerationFragment f=new ModerationFragment();Bundle args=new Bundle();args.putString("page",page.name());f.setArguments(args);return f;
    }
    public Page page(){return Page.valueOf(requireArguments().getString("page"));}
    public boolean allowed(SessionManager s){return s.isReviewerOrAdmin()&&(!page().admin()||"ADMIN".equals(s.getRole()));}
    @Override public View onCreateView(@NonNull LayoutInflater inflater,ViewGroup parent,Bundle saved){
        b=FragmentFlowPageBinding.inflate(inflater,parent,false);return b.getRoot();
    }
    @Override public void onViewCreated(@NonNull View view,Bundle saved) {
        if(saved!=null){offset=saved.getInt("offset");statusIndex=saved.getInt("status");query=saved.getString("query","");}
        api=((SafeRouteApp)requireActivity().getApplication()).api().service;
        b.flowBack.setOnClickListener(v->((MainActivity)requireActivity()).navigateBack());
        b.flowTitle.setText(page().title);
        if(!allowed(new SessionManager(requireContext()))){deny();return;}
        boolean searchable=page()==Page.ADMIN_REPORTS||page()==Page.DUPLICATES;
        if(searchable) {
            search=new EditText(requireContext());search.setHint("Tìm theo mã báo cáo hoặc nội dung");
            search.setSingleLine();search.setText(query);search.setMinHeight(dp(48));
            search.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(200)});
            b.flowActions.addView(search);
        }
        if(page()==Page.ADMIN_REPORTS) {
            label(b.flowActions,"Trạng thái báo cáo");
            filter=new Spinner(requireContext());filter.setContentDescription("Trạng thái báo cáo");
            ArrayAdapter<String> adapter=new ArrayAdapter<>(requireContext(),android.R.layout.simple_spinner_item,
                new String[]{"Tất cả trạng thái","Chờ xác minh","Đã xác minh","Từ chối"});
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            filter.setAdapter(adapter);filter.setSelection(statusIndex);b.flowActions.addView(filter,new LinearLayout.LayoutParams(-1,dp(48)));
        }
        refresh=button(b.flowActions,searchable?"Tìm báo cáo":"Tải lại",()->{
            if(search!=null)query=search.getText().toString().trim();
            if(filter!=null)statusIndex=filter.getSelectedItemPosition();
            offset=0;load();
        });
        rows=new LinearLayout(requireContext());rows.setOrientation(LinearLayout.VERTICAL);b.flowActions.addView(rows);
        previous=button(b.flowActions,"Trang trước",()->{offset=Math.max(0,offset-30);load();});
        next=button(b.flowActions,"Trang sau",()->{offset+=30;load();});
        if(page()==Page.ADMIN_METRICS||page()==Page.ADMIN_SETTINGS){previous.setVisibility(View.GONE);next.setVisibility(View.GONE);}
        load();
    }
    private void deny(){b.reviewerContent.setVisibility(View.GONE);b.accessDeniedContent.setVisibility(View.VISIBLE);}
    private void load() {
        if(loading)return;
        if(!allowed(new SessionManager(requireContext()))){deny();return;}
        loading=true;rows.removeAllViews();refresh.setEnabled(false);previous.setEnabled(false);next.setEnabled(false);
        if(search!=null)search.setEnabled(false);if(filter!=null)filter.setEnabled(false);
        b.flowBody.setText("Đang tải dữ liệu…");
        switch(page()) {
            case REVIEW_HISTORY: case ADMIN_AUDIT:
                ApiCalls.enqueueDetailed(getViewLifecycleOwner(),api.reviewHistory(offset,30),list->{
                    finish(list.size(),"Chưa có quyết định xét duyệt.","Lịch sử xét duyệt");
                    for(com.comeforfood.saferoute.core.model.ModerationDto.History h:list)
                        reportButton(h.reportId,"#"+h.reportId+" · "+DisplayText.status(h.status)+"\n"+DisplayText.time(h.createdAt)
                            +" · Người duyệt #"+h.actorId+"\n"+h.reason);
                    if(page()==Page.ADMIN_AUDIT)button(rows,"Lịch sử quyền tài khoản",()->((MainActivity)requireActivity()).openTv2Fragment(com.comeforfood.saferoute.feature.admin.AdminUsersFragment.forHistory()));
                },this::failure);break;
            case ADMIN_REPORTS:
                String[] statuses={null,"PENDING_VERIFICATION","VERIFIED","REJECTED"};
                ApiCalls.enqueueDetailed(getViewLifecycleOwner(),api.adminReports(query,statuses[statusIndex],offset,30),list->{
                    finish(list.size(),"Không có báo cáo phù hợp.","Báo cáo");
                    for(com.comeforfood.saferoute.core.model.ReportDto.ReportResponse r:list)
                        reportButton(Long.parseLong(r.getId()),"#"+r.getId()+" · "+r.getCategoryUser()+" · "+r.getStatusDisplay()
                            +"\n"+DisplayText.time(r.getCreatedAt())+"\n"+(r.getDescription()==null?"":r.getDescription()));
                },this::failure);break;
            case DUPLICATES:
                ApiCalls.enqueueDetailed(getViewLifecycleOwner(),api.duplicates(query,offset,30),list->{
                    finish(list.size(),"Không tìm thấy cặp báo cáo theo tiêu chí.","Cặp cần kiểm tra");
                    b.flowBody.append("\nGợi ý: cùng loại, cách nhau tối đa 30 m, đều trong 7 ngày gần nhất và còn ít nhất một báo cáo chờ duyệt. Cần xem bằng chứng; không tự gộp báo cáo.");
                    for(com.comeforfood.saferoute.core.model.ModerationDto.Pair p:list) {
                        label(rows,p.category+" · Cách nhau "+String.format(java.util.Locale.forLanguageTag("vi-VN"),"%.1f",p.distance)+" m");
                        reportButton(p.reportId,"Xem báo cáo #"+p.reportId);
                        reportButton(p.candidateId,"Đối chiếu báo cáo #"+p.candidateId);
                    }
                },this::failure);break;
            default:
                ApiCalls.enqueueDetailed(getViewLifecycleOwner(),api.overview(),o->{
                    finish(0,"","");
                    if(page()==Page.ADMIN_METRICS) {
                        b.flowBody.setText("Số liệu hiện tại từ hệ thống");
                        label(rows,"Báo cáo\nChờ xác minh: "+o.reports.get("PENDING_VERIFICATION")
                            +"\nĐã xác minh: "+o.reports.get("VERIFIED")+"\nTừ chối: "+o.reports.get("REJECTED")
                            +"\nQuyết định xét duyệt: "+o.reviews);
                        label(rows,"Tài khoản\nUSER: "+o.users.get("USER")+" · REVIEWER: "+o.users.get("REVIEWER")+" · ADMIN: "+o.users.get("ADMIN"));
                        label(rows,"Mô hình: "+o.model+"\nAI: "+(o.aiReady?"Sẵn sàng":"Chưa sẵn sàng")
                            +"\nĐánh giá nguy cơ: "+(o.riskReady?"Có chính sách được duyệt":"Chưa có chính sách được duyệt")
                            +"\nChưa có số liệu độ chính xác hoặc benchmark trong màn này.");
                    } else {
                        b.flowBody.setText("Cấu hình đang áp dụng · Chỉ xem\nThay đổi cấu hình dịch vụ cần được thực hiện ở backend.");
                        label(rows,"Ảnh tối đa: "+o.maxImageMb+" MiB\nGợi ý nghi trùng: "+o.duplicateRadius+" m / "+o.duplicateDays+" ngày"
                            +"\nChính sách nguy cơ: "+(o.policy==null?"Chưa cấu hình":o.policy)
                            +"\nTình trạng: "+(o.riskReady?"Đã được duyệt":"Chưa sẵn sàng để chấm điểm"));
                        button(rows,"Quản lý quyền tài khoản",()->((MainActivity)requireActivity()).openFlow(Page.ADMIN_USERS));
                    }
                },this::failure);break;
        }
    }
    private void finish(int size,String empty,String title){
        loading=false;refresh.setEnabled(true);previous.setEnabled(offset>0);next.setEnabled(size==30);
        previous.setVisibility(offset>0?View.VISIBLE:View.GONE);next.setVisibility(size==30?View.VISIBLE:View.GONE);
        if(search!=null)search.setEnabled(true);if(filter!=null)filter.setEnabled(true);
        b.flowBody.setText(size==0?empty:title+" · Trang "+(offset/30+1));
    }
    private void failure(int code,String message){finish(0,message,"");if(code==403)((MainActivity)requireActivity()).refreshSession();}
    private void reportButton(long id,String text){button(rows,text,()->((MainActivity)requireActivity()).openTv2Fragment(ReportDetailFragment.at(String.valueOf(id))));}
    private MaterialButton button(LinearLayout parent,String text,Runnable action){
        MaterialButton v=(MaterialButton)getLayoutInflater().inflate(R.layout.item_navigation_action,parent,false);
        v.setText(text);v.setAllCaps(false);v.setMinHeight(dp(52));parent.addView(v);v.setOnClickListener(w->{if(!loading)action.run();});return v;
    }
    private void label(LinearLayout parent,String text){
        TextView v=new TextView(requireContext());v.setText(text);v.setTextSize(16);v.setTextColor(requireContext().getColor(R.color.ink));
        v.setPadding(0,dp(12),0,dp(12));parent.addView(v);
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onSaveInstanceState(@NonNull Bundle out){
        super.onSaveInstanceState(out);out.putInt("offset",offset);out.putInt("status",statusIndex);out.putString("query",query);
    }
    @Override public void onDestroyView(){b=null;loading=false;super.onDestroyView();}
}
