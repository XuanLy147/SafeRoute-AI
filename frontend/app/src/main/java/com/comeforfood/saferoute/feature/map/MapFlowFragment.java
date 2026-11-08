package com.comeforfood.saferoute.feature.map;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.comeforfood.saferoute.MainActivity;
import com.comeforfood.saferoute.R;
import com.comeforfood.saferoute.SafeRouteApp;
import com.comeforfood.saferoute.databinding.FragmentFlowPageBinding;
import com.comeforfood.saferoute.feature.routing.AddressSearch;
import com.comeforfood.saferoute.feature.shared.*;
import com.comeforfood.saferoute.feature.community.CommunityFeedbackFragment;
import com.google.android.material.button.MaterialButton;
import java.util.List;

public class MapFlowFragment extends Fragment {
    private FragmentFlowPageBinding binding;
    private MapRoutingViewModel model;
    private MapRoutingDataSource.Request search;
    private long generation;
    public static MapFlowFragment at(String page,String id) {
        MapFlowFragment f=new MapFlowFragment();Bundle b=new Bundle();b.putString("page",page);b.putString("id",id);f.setArguments(b);return f;
    }
    @Override public View onCreateView(@NonNull LayoutInflater inflater,ViewGroup parent,Bundle saved) {
        binding=FragmentFlowPageBinding.inflate(inflater,parent,false);return binding.getRoot();
    }
    @Override public void onViewCreated(@NonNull View view,Bundle saved) {
        model=new ViewModelProvider(requireActivity()).get(MapRoutingViewModel.class);
        binding.flowBack.setOnClickListener(v->host().navigateBack());
        String page=requireArguments().getString("page","nearby");
        if("search".equals(page)) searchPage();
        else if("filters".equals(page)) filterPage();
        else if("gallery".equals(page)) {
            binding.flowTitle.setText(R.string.flow_gallery);binding.flowBody.setText(R.string.flow_gallery_empty);
        } else {
            binding.flowTitle.setText("detail".equals(page)?R.string.flow_alert_detail:R.string.flow_nearby);
            model.reports().observe(getViewLifecycleOwner(),state-> {
                binding.flowActions.removeAllViews();
                if("detail".equals(page)) {detail(state);return;}
                if(state==null||state.loading) {binding.flowBody.setText(R.string.reports_loading);return;}
                if(state.error!=null) {
                    binding.flowBody.setText(state.error==MapRoutingDataSource.Failure.NOT_CONNECTED?R.string.reports_disconnected:R.string.reports_error);
                    action(R.string.refresh,"refresh",()->model.refreshReports());return;
                }
                List<Hazard> reports=HazardFilter.apply(state.data==null?java.util.Collections.emptyList():state.data,model.category(),model.verifiedOnly());
                binding.flowBody.setText(reports.isEmpty()?getString(R.string.reports_empty):getString(R.string.reports_count,reports.size()));
                for(Hazard h:reports) action(h.title.isEmpty()?h.type.label:h.title,h.id,()->host().openFeatureFragment(com.comeforfood.saferoute.feature.reports.ReportDetailFragment.at(h.id)));
            });
            model.refreshReports();
        }
    }
    private void searchPage() {
        binding.flowTitle.setText(R.string.flow_search_area);
        binding.flowBody.setText(R.string.flow_search_body);

        EditText input = new EditText(requireContext());
        input.setId(R.id.areaQuery);
        input.setHint(R.string.flow_search_hint);
        input.setBackgroundResource(R.drawable.bg_soft_input);
        int padH = (int) (18 * getResources().getDisplayMetrics().density);
        int padV = (int) (14 * getResources().getDisplayMetrics().density);
        input.setPadding(padH, padV, padH, padV);
        input.setTextColor(getResources().getColor(R.color.ink));
        input.setHintTextColor(getResources().getColor(R.color.muted));
        input.setTextSize(15f);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        binding.flowActions.addView(input);

        LinearLayout results = new LinearLayout(requireContext());
        results.setOrientation(LinearLayout.VERTICAL);

        MaterialButton button = (MaterialButton) getLayoutInflater().inflate(R.layout.item_primary_button, binding.flowActions, false);
        button.setText(R.string.flow_search_action);
        button.setTag("search");
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = (int) (16 * getResources().getDisplayMetrics().density);
        p.gravity = Gravity.CENTER_HORIZONTAL;
        binding.flowActions.addView(button, p);

        button.setOnClickListener(v -> {
            String query = input.getText().toString().trim();
            if (query.isEmpty()) { input.setError(getString(R.string.flow_search_hint)); return; }
            if (search != null) search.cancel();
            long request = ++generation;
            results.removeAllViews();
            binding.flowBody.setText(R.string.flow_search_loading);
            search = ((SafeRouteApp) requireActivity().getApplication()).getAddressSearch().search(query, new AddressSearch.Callback() {
                public void success(List<AddressSearch.Place> places) {
                    if (binding == null || request != generation) return;
                    binding.flowBody.setText(places.isEmpty() ? R.string.flow_search_empty : R.string.flow_search_body);
                    for (AddressSearch.Place place : places) {
                        MaterialButton choice = (MaterialButton) getLayoutInflater().inflate(R.layout.item_navigation_action, results, false);
                        choice.setText(place.label);
                        choice.setAllCaps(false);
                        results.addView(choice, new LinearLayout.LayoutParams(-1, -2));
                        choice.setOnClickListener(v2 -> host().openMapAt(place.point));
                    }
                }
                public void failure(AddressSearch.Failure failure) {
                    if (binding != null && request == generation) binding.flowBody.setText(R.string.flow_search_error);
                }
            });
        });
        binding.flowActions.addView(results);
    }
    private void filterPage() {
        binding.flowTitle.setText(R.string.filter_label);binding.flowBody.setText(R.string.pending_note);
        RadioGroup group=new RadioGroup(requireContext());HazardFilter.Category[] values=HazardFilter.Category.values();
        int[] labels={R.string.filter_all,R.string.filter_pothole,R.string.filter_cracks};
        for(int i=0;i<values.length;i++) {
            RadioButton b=new RadioButton(requireContext());b.setId(View.generateViewId());b.setText(labels[i]);group.addView(b);
            if(model.category()==values[i])b.setChecked(true);
        }
        binding.flowActions.addView(group);
        CheckBox verified=new CheckBox(requireContext());verified.setText(R.string.filter_verified);verified.setChecked(model.verifiedOnly());binding.flowActions.addView(verified);
        action(R.string.flow_continue,"apply",()->{
            int index=group.indexOfChild(group.findViewById(group.getCheckedRadioButtonId()));
            model.filters(values[Math.max(0,index)],verified.isChecked());host().navigateBack();
        });
    }
    private void detail(MapRoutingViewModel.State<List<Hazard>> state) {
        Hazard found=null;
        if(state!=null&&state.data!=null)for(Hazard h:state.data)if(h.id.equals(requireArguments().getString("id"))&&h.status!=Hazard.Status.REJECTED)found=h;
        if(found==null){binding.flowBody.setText(R.string.flow_detail_missing);return;}
        Hazard h=found;
        binding.flowBody.setText(h.title+"\n\n"+h.type.label+"\n"+getString(h.status==Hazard.Status.VERIFIED?R.string.verified:R.string.pending)+"\n\n"+h.description+"\n"+h.point.format());
        action(R.string.flow_gallery,"gallery",()->host().openFeatureFragment(at("gallery",h.id)));
        action(R.string.flow_feedback,"feedback",()->{
            CommunityFeedbackFragment fragment=new CommunityFeedbackFragment();Bundle args=new Bundle();args.putString("report.id",h.id);args.putString("report.title",h.title);fragment.setArguments(args);host().openFeatureFragment(fragment);
        });
        action(R.string.go_here,"route",()->host().openRoutes(h.point));
    }
    private MainActivity host(){return(MainActivity)requireActivity();}
    private MaterialButton action(int text,String tag,Runnable run){return action(getString(text),tag,run);}
    private MaterialButton action(String text,String tag,Runnable run) {
        MaterialButton b=(MaterialButton)getLayoutInflater().inflate(R.layout.item_navigation_action,binding.flowActions,false);b.setText(text);b.setTag(tag);b.setAllCaps(false);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=(int)(12*getResources().getDisplayMetrics().density);
        binding.flowActions.addView(b,p);b.setOnClickListener(v->run.run());return b;
    }
    @Override public void onDestroyView(){generation++;if(search!=null)search.cancel();search=null;binding=null;super.onDestroyView();}
}
