package com.comeforfood.saferoute.feature.admin;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import com.comeforfood.saferoute.*;
import com.comeforfood.saferoute.core.model.AdminDto;
import com.comeforfood.saferoute.core.network.*;
import com.comeforfood.saferoute.core.session.SessionManager;
import com.comeforfood.saferoute.databinding.FragmentFlowPageBinding;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Real ADMIN-only users and role history; no local role assignment. */
public class AdminUsersFragment extends Fragment {
    public static AdminUsersFragment forHistory(){
        AdminUsersFragment f=new AdminUsersFragment();Bundle args=new Bundle();args.putBoolean("history",true);f.setArguments(args);return f;
    }
    private FragmentFlowPageBinding b;
    private ApiService api;
    private EditText search;
    private LinearLayout rows;
    private MaterialButton previous,next,find;
    private int offset;
    private boolean history,loading;
    private String query="";
    private androidx.appcompat.app.AlertDialog dialog;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        history=getArguments()!=null&&getArguments().getBoolean("history");
    }
    @Override public View onCreateView(@NonNull LayoutInflater inflater,ViewGroup parent,Bundle saved){
        b=FragmentFlowPageBinding.inflate(inflater,parent,false);return b.getRoot();
    }
    @Override public void onViewCreated(@NonNull View view,Bundle saved) {
        api=((SafeRouteApp)requireActivity().getApplication()).api().service;
        if(saved!=null){history=saved.getBoolean("history");offset=saved.getInt("offset");query=saved.getString("query","");}
        b.flowBack.setOnClickListener(v->((MainActivity)requireActivity()).navigateBack());
        if(!allowed()){deny();return;}
        b.flowTitle.setText("Quản lý quyền");
        search=new EditText(requireContext());search.setHint("Tìm theo email hoặc họ tên");
        search.setSingleLine();search.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        search.setText(query);search.setMinHeight(dp(52));
        search.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(200)});
        b.flowActions.addView(search);
        find=button("Tìm tài khoản",()->{history=false;offset=0;query=search.getText().toString().trim();load();});
        button("Lịch sử thay đổi quyền",()->{history=true;offset=0;load();});
        rows=new LinearLayout(requireContext());rows.setOrientation(LinearLayout.VERTICAL);b.flowActions.addView(rows);
        previous=button("Trang trước",()->{offset=Math.max(0,offset-30);load();});
        next=button("Trang sau",()->{offset+=30;load();});
        load();
    }
    private boolean allowed(){SessionManager s=new SessionManager(requireContext());return s.isVerified()&&"ADMIN".equals(s.getRole());}
    private void deny(){b.reviewerContent.setVisibility(View.GONE);b.accessDeniedContent.setVisibility(View.VISIBLE);}
    private void load() {
        if(loading)return;
        if(!allowed()){deny();return;}
        loading=true;rows.removeAllViews();find.setEnabled(false);previous.setEnabled(false);next.setEnabled(false);
        b.flowBody.setText(history?"Đang tải lịch sử quyền…":"Đang tải tài khoản…");
        if(history)ApiCalls.enqueueDetailed(getViewLifecycleOwner(),api.roleAudit(offset,30),list->{
            finish(list.size());b.flowBody.setText(list.isEmpty()?"Chưa có thay đổi quyền.":"Lịch sử quyền · Trang "+(offset/30+1));
            for(AdminDto.Audit a:list) {
                TextView text=new TextView(requireContext());text.setTextColor(requireContext().getColor(R.color.ink));text.setTextSize(16);
                text.setText("#"+a.id+" · "+a.createdAt+"\nTài khoản #"+a.userId+": "+a.fromRole+" → "+a.toRole+
                    "\nNgười thực hiện #"+a.actorId+"\nLý do: "+a.reason);
                text.setPadding(0,dp(16),0,dp(16));rows.addView(text);
            }
        },this::failure);
        else ApiCalls.enqueueDetailed(getViewLifecycleOwner(),api.users(query,offset,30),list->{
            finish(list.size());b.flowBody.setText(list.isEmpty()?"Không tìm thấy tài khoản.":"Chọn tài khoản để xem và đổi quyền · Trang "+(offset/30+1));
            for(AdminDto.User u:list) {
                MaterialButton row=(MaterialButton)getLayoutInflater().inflate(R.layout.item_navigation_action,rows,false);
                row.setText((u.fullName==null?"":u.fullName+"\n")+u.email+"\n"+u.role+" · #"+u.id);
                row.setAllCaps(false);row.setMinHeight(dp(72));rows.addView(row);
                row.setOnClickListener(v->edit(u));
            }
        },this::failure);
    }
    private void finish(int size){loading=false;find.setEnabled(true);previous.setEnabled(offset>0);next.setEnabled(size==30);}
    private void failure(int code,String message) {
        finish(0);b.flowBody.setText(message);
        if(code==403){deny();((MainActivity)requireActivity()).refreshSession();}
        else { MaterialButton retry=new MaterialButton(requireContext());retry.setText("Thử lại");rows.addView(retry);retry.setOnClickListener(v->load()); }
    }
    private void edit(AdminDto.User user) {
        if(!allowed()){deny();return;}
        if(user.email.equals(new SessionManager(requireContext()).getEmail())){
            new MaterialAlertDialogBuilder(requireContext()).setTitle("Tài khoản của bạn")
                .setMessage("Bạn không thể tự đổi quyền của mình. Một ADMIN khác có thể thực hiện việc này.")
                .setPositiveButton("Đã hiểu",null).show();return;
        }
        String[] roles={"USER","REVIEWER","ADMIN"};
        String[] labels={"USER · Báo cáo và đóng góp","REVIEWER · Xét duyệt báo cáo","ADMIN · Xét duyệt và quản lý quyền"};
        final int[] selected={java.util.Arrays.asList(roles).indexOf(user.role)};
        LinearLayout form=new LinearLayout(requireContext());form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(24),dp(8),dp(24),0);
        EditText reason=new EditText(requireContext());reason.setHint("Lý do thay đổi (3–500 ký tự)");reason.setMinLines(2);
        reason.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        reason.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(500)});form.addView(reason);
        TextView error=new TextView(requireContext());error.setTextColor(requireContext().getColor(R.color.error));error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);form.addView(error);
        dialog=new MaterialAlertDialogBuilder(requireContext()).setTitle("Đổi quyền: "+user.email)
            .setSingleChoiceItems(labels,selected[0],(d,index)->selected[0]=index).setView(form)
            .setNegativeButton("Hủy",null).setPositiveButton("Lưu thay đổi",null).create();
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{
            String text=reason.getText().toString().trim();
            if(text.codePointCount(0,text.length())<3){reason.setError("Nhập lý do ít nhất 3 ký tự");return;}
            if(selected[0]<0 || roles[selected[0]].equals(user.role)){error.setText("Chọn vai trò khác vai trò hiện tại.");return;}
            dialog.getButton(-1).setEnabled(false);dialog.getButton(-2).setEnabled(false);dialog.setCancelable(false);
            ApiCalls.enqueueDetailed(getViewLifecycleOwner(),api.changeRole(user.id,new AdminDto.Change(roles[selected[0]],user.role,text)),result->{
                dialog.dismiss();dialog=null;load();
            },(code,message)->{
                if(dialog==null)return;
                dialog.getButton(-1).setEnabled(true);dialog.getButton(-2).setEnabled(true);dialog.setCancelable(true);
                error.setText(code==409?"Quyền đã thay đổi hoặc thao tác không được phép. Đóng hộp thoại và tải lại.":message);
                if(code==403){dialog.dismiss();dialog=null;failure(code,message);}
            });
        }));
        dialog.show();
    }
    private MaterialButton button(String text,Runnable action){
        MaterialButton button=new MaterialButton(requireContext());button.setAllCaps(false);button.setText(text);button.setMinHeight(dp(48));
        b.flowActions.addView(button);button.setOnClickListener(v->{if(!loading)action.run();});return button;
    }
    private int dp(int value){return (int)(getResources().getDisplayMetrics().density*value);}
    @Override public void onSaveInstanceState(@NonNull Bundle out){super.onSaveInstanceState(out);out.putBoolean("history",history);out.putInt("offset",offset);out.putString("query",query);}
    @Override public void onDestroyView(){if(dialog!=null){dialog.dismiss();dialog=null;}loading=false;b=null;search=null;rows=null;super.onDestroyView();}
}
