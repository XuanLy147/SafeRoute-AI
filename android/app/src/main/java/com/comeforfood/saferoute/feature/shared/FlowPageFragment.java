package com.comeforfood.saferoute.feature.shared;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import com.comeforfood.saferoute.*;
import com.comeforfood.saferoute.core.session.SessionManager;
import com.comeforfood.saferoute.databinding.FragmentFlowPageBinding;
import com.comeforfood.saferoute.feature.auth.*;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

/** Native routes from the HTML atlas. Unconnected operations never report fabricated success. */
public class FlowPageFragment extends Fragment {
    public enum Page {
        REVIEW_HOME(R.string.role_review_home), REVIEW_QUEUE(R.string.role_review_queue), REVIEW_DETAIL(R.string.role_detail),
        REVIEW_APPROVE(R.string.role_approve), REVIEW_REJECT(R.string.role_reject), REVIEW_HISTORY(R.string.role_review_history),
        DUPLICATES(R.string.role_duplicates), ADMIN_HOME(R.string.role_admin_home), ADMIN_REPORTS(R.string.role_admin_reports),
        ADMIN_REVIEW(R.string.role_detail), ADMIN_AUDIT(R.string.role_admin_audit), ADMIN_USERS(R.string.role_admin_users),
        ADMIN_ROLES(R.string.role_admin_roles), ADMIN_METRICS(R.string.role_admin_metrics), ADMIN_SETTINGS(R.string.role_admin_settings),
        SAVED(R.string.flow_saved), NOTIFICATIONS(R.string.flow_notifications), SETTINGS(R.string.flow_settings), PRIVACY(R.string.flow_privacy);
        public final int title;
        Page(int title) {this.title=title;}
        public boolean admin() { return name().startsWith("ADMIN_"); }
        public boolean restricted() { return admin()||name().startsWith("REVIEW_")||this==DUPLICATES; }
    }
    private FragmentFlowPageBinding binding;
    private String roleStamp, reasonText="";
    public static FlowPageFragment at(Page page) {
        FlowPageFragment f=new FlowPageFragment();Bundle b=new Bundle();b.putString("page",page.name());f.setArguments(b);return f;
    }
    public Page page() {
        try {return Page.valueOf(getArguments()==null?"REVIEW_HOME":getArguments().getString("page","REVIEW_HOME"));}
        catch(IllegalArgumentException e) {return Page.REVIEW_HOME;}
    }
    public boolean isDecision() {return page()==Page.REVIEW_APPROVE||page()==Page.REVIEW_REJECT||page()==Page.ADMIN_ROLES;}
    @Override public View onCreateView(@NonNull LayoutInflater inflater,ViewGroup parent,Bundle saved) {
        binding=FragmentFlowPageBinding.inflate(inflater,parent,false);return binding.getRoot();
    }
    @Override public void onViewCreated(@NonNull View view,Bundle saved) {
        if(saved!=null)reasonText=saved.getString("reason","");
        binding.flowBack.setOnClickListener(v->((MainActivity)requireActivity()).navigateBack());render();
    }
    @Override public void onResume() {super.onResume();SessionManager s=new SessionManager(requireContext());if(binding!=null&&!((s.isLoggedIn()?s.getRole():"GUEST")).equals(roleStamp))render();}
    @Override public void onSaveInstanceState(@NonNull Bundle out) {super.onSaveInstanceState(out);out.putString("reason",reasonText);}
    private void render() {
        Page page=page();SessionManager session=new SessionManager(requireContext());
        roleStamp=session.isLoggedIn()?session.getRole():"GUEST";
        boolean allowed=!page.restricted()||(session.isReviewerOrAdmin()&&(!page.admin()||"ADMIN".equalsIgnoreCase(session.getRole())));
        binding.flowTitle.setText(page.title);binding.flowActions.removeAllViews();
        binding.accessDeniedContent.setVisibility(allowed?View.GONE:View.VISIBLE);
        binding.reviewerContent.setVisibility(allowed?View.VISIBLE:View.GONE);
        if(!allowed)return;
        binding.flowBody.setText(R.string.role_unavailable);
        switch(page) {
            case REVIEW_HOME: binding.flowBody.setText("Không gian xét duyệt: mở hàng đợi để kiểm tra và xác minh báo cáo.");link(Page.REVIEW_QUEUE);link(Page.REVIEW_HISTORY);link(Page.DUPLICATES);account();break;
            case ADMIN_HOME: binding.flowBody.setText("Quản lý báo cáo và quyền tài khoản. Mỗi lần cấp hoặc thu hồi quyền đều được ghi lịch sử.");link(Page.ADMIN_REPORTS);link(Page.ADMIN_AUDIT);link(Page.ADMIN_USERS);link(Page.ADMIN_METRICS);link(Page.ADMIN_SETTINGS);account();break;
            case REVIEW_QUEUE: case ADMIN_REPORTS: loadQueue();break;
            case DUPLICATES:
                EditText search=new EditText(requireContext());search.setHint(R.string.role_search);search.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
                search.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);binding.flowActions.addView(search);
                RadioGroup filters=new RadioGroup(requireContext());
                for(int label:new int[]{R.string.role_all,R.string.role_pending,R.string.role_verified,R.string.role_rejected}) {
                    RadioButton option=new RadioButton(requireContext());option.setId(View.generateViewId());option.setText(label);option.setMinHeight(dp(48));filters.addView(option);
                    if(label==R.string.role_pending)option.setChecked(true);
                }
                binding.flowActions.addView(filters);retry();break;
            case REVIEW_HISTORY: case ADMIN_AUDIT: retry();break;
            case REVIEW_DETAIL: case ADMIN_REVIEW:
                binding.flowBody.setText(R.string.role_no_record);
                link(Page.REVIEW_APPROVE).setEnabled(false);link(Page.REVIEW_REJECT).setEnabled(false);break;
            case REVIEW_APPROVE: case REVIEW_REJECT:
                binding.flowBody.setText(R.string.role_no_record);
                EditText reason=new EditText(requireContext());reason.setHint(R.string.role_reason);reason.setMinLines(3);
                reason.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                reason.setText(reasonText);reason.addTextChangedListener(new android.text.TextWatcher(){
                    public void beforeTextChanged(CharSequence s,int a,int c,int d){}
                    public void onTextChanged(CharSequence s,int a,int b,int c){reasonText=s.toString();}
                    public void afterTextChanged(android.text.Editable s){}
                });
                reason.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);binding.flowActions.addView(reason);
                action(page.title,"decision",()->{
                    if(reason.getText().toString().trim().isEmpty())reason.setError(getString(R.string.role_reason_required));
                    else Snackbar.make(binding.getRoot(),R.string.role_decision_unavailable,Snackbar.LENGTH_LONG).show();
                });break;
            case ADMIN_USERS: binding.flowBody.setText(R.string.role_proposal);link(Page.ADMIN_ROLES);break;
            case ADMIN_ROLES: binding.flowBody.setText(R.string.role_no_user);break;
            case ADMIN_METRICS: case ADMIN_SETTINGS: case SAVED: case NOTIFICATIONS:
                binding.flowBody.setText(R.string.role_proposal);break;
            case SETTINGS:
                binding.flowBody.setText(R.string.flow_settings_body);
                action(R.string.flow_android_settings,"permissions",()->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+requireContext().getPackageName()))));
                action(R.string.flow_replay_intro,"intro",()->((MainActivity)requireActivity()).openTv2Fragment(OnboardingFragment.at(0)));
                link(Page.PRIVACY);break;
            case PRIVACY: binding.flowBody.setText(R.string.flow_privacy_body);break;
        }
    }
    private void loadQueue() {
        binding.flowBody.setText("Đang tải hàng đợi…");binding.flowActions.removeAllViews();
        com.comeforfood.saferoute.core.network.ApiCalls.enqueue(getViewLifecycleOwner(),((SafeRouteApp)requireActivity().getApplication()).api().service.queue(),reports->{
            binding.flowBody.setText(reports.isEmpty()?"Không có báo cáo chờ duyệt":"Báo cáo chờ duyệt: "+reports.size());
            for(com.comeforfood.saferoute.core.model.ReportDto.ReportResponse r:reports) {
                MaterialButton row=action(R.string.role_detail,r.getId(),()->((MainActivity)requireActivity()).openTv2Fragment(com.comeforfood.saferoute.feature.reports.ReportDetailFragment.at(r.getId())));
                row.setText("#"+r.getId()+" · "+com.comeforfood.saferoute.core.model.HazardLabel.display(r.getCategoryUser())+" · "+r.getCreatedAt());
            }
            action(R.string.role_retry,"retry",this::loadQueue);
        },error->{binding.flowBody.setText(error);action(R.string.role_retry,"retry",this::loadQueue);});
    }
    private void account() {action(R.string.flow_account_home,"account",()->((MainActivity)requireActivity()).openTv2Fragment(new com.comeforfood.saferoute.feature.profile.ProfileFragment()));}
    private void retry() {action(R.string.role_retry,"retry",()->Snackbar.make(binding.getRoot(),R.string.role_unavailable,Snackbar.LENGTH_LONG).show());}
    private MaterialButton link(Page page) {return action(page.title,page.name(),()->((MainActivity)requireActivity()).openFlow(page));}
    private MaterialButton action(int label,String tag,Runnable run) {
        boolean primary="decision".equals(tag)||"retry".equals(tag);
        MaterialButton button=primary?new MaterialButton(requireContext()):(MaterialButton)getLayoutInflater().inflate(R.layout.item_navigation_action,binding.flowActions,false);
        button.setText(label);button.setTag(tag);button.setMinHeight(dp(primary?52:58));button.setAllCaps(false);
        if(!primary) {
            button.setIconResource(R.drawable.ic_chevron);button.setIconGravity(MaterialButton.ICON_GRAVITY_END);
            button.setGravity(android.view.Gravity.START|android.view.Gravity.CENTER_VERTICAL);
            button.setTextColor(requireContext().getColor(R.color.ink));
            button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(requireContext().getColor(R.color.input_surface)));
            button.setIconTint(android.content.res.ColorStateList.valueOf(requireContext().getColor(R.color.muted)));
            button.setStrokeWidth(0);button.setCornerRadius(dp(14));button.setStateListAnimator(null);button.setElevation(0);
        }
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=dp(6);
        binding.flowActions.addView(button,params);button.setOnClickListener(v->run.run());return button;
    }
    private int dp(int n) {return(int)(n*getResources().getDisplayMetrics().density);}
    @Override public void onDestroyView(){binding=null;super.onDestroyView();}
}
