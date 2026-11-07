package com.comeforfood.saferoute.feature.auth;

import android.os.Bundle;
import android.view.*;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.comeforfood.saferoute.MainActivity;
import com.comeforfood.saferoute.databinding.FragmentSessionGateBinding;

/** Retryable cold-start validation. Network failure must not delete saved credentials. */
public class SessionGateFragment extends Fragment {
    @Override public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent, Bundle saved) {
        return FragmentSessionGateBinding.inflate(inflater,parent,false).getRoot();
    }
    @Override public void onViewCreated(@NonNull View view, Bundle saved) {
        FragmentSessionGateBinding b=FragmentSessionGateBinding.bind(view);
        AuthViewModel model=new ViewModelProvider(this).get(AuthViewModel.class);
        b.retry.setOnClickListener(v->model.restore());
        b.signIn.setOnClickListener(v->((MainActivity)requireActivity()).logout());
        b.guest.setOnClickListener(v->((MainActivity)requireActivity()).openMap());
        model.state.observe(getViewLifecycleOwner(),s->{
            b.progress.setVisibility(s.loading?View.VISIBLE:View.GONE);
            b.message.setText(s.message.isEmpty()?"Đang xác minh tài khoản và quyền truy cập…":s.message);
            b.retry.setEnabled(!s.loading);
            if(s.complete){model.consumeSuccess();((MainActivity)requireActivity()).openSessionHome();}
        });
        if(!model.state.getValue().loading && !model.state.getValue().complete && model.state.getValue().message.isEmpty())model.restore();
    }
}
