package com.comeforfood.saferoute.feature.report;

import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.RadioButton;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;
import com.comeforfood.saferoute.MainActivity;
import com.comeforfood.saferoute.R;
import com.comeforfood.saferoute.databinding.FragmentReportWizardBinding;
import com.comeforfood.saferoute.feature.shared.*;
import com.comeforfood.saferoute.feature.reports.MyReportsFragment;
import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.*;
import com.google.android.material.snackbar.Snackbar;
import java.io.File;

public class ReportWizardFragment extends LocationAwareFragment {
    private FragmentReportWizardBinding binding;
    private ReportDraft draft;
    private GoogleMap map;
    private Marker pin;
    private String[] categories, severities;
    private final ActivityResultLauncher<String[]> gallery = registerForActivityResult(
        new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri == null) return;
            try { requireContext().getContentResolver().takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION); }
            catch (SecurityException ignored) {}
            acceptImage(uri);
        });
    private final ActivityResultLauncher<Uri> camera = registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
        if (Boolean.TRUE.equals(ok) && draft != null && draft.camera() != null) acceptImage(Uri.parse(draft.camera()));
    });
    @Override public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent, Bundle saved) {
        binding=FragmentReportWizardBinding.inflate(inflater,parent,false); return binding.getRoot();
    }
    @Override public void onViewCreated(@NonNull View view, Bundle saved) {
        draft=new ViewModelProvider(requireActivity()).get(ReportDraft.class);
        categories=getResources().getStringArray(R.array.report_categories);
        severities=getResources().getStringArray(R.array.report_severities);
        for(int i=0;i<categories.length;i++) {
            RadioButton radio=new RadioButton(requireContext()); radio.setId(View.generateViewId()); radio.setText(categories[i]); radio.setMinHeight(dp(48));
            binding.categoryOptions.addView(radio); if(i==draft.category()) radio.setChecked(true);
        }
        for(int i=0;i<severities.length;i++) {
            RadioButton radio=new RadioButton(requireContext()); radio.setId(View.generateViewId()); radio.setText(severities[i]); radio.setMinHeight(dp(48));
            binding.severityOptions.addView(radio); if(i==draft.severity()) radio.setChecked(true);
        }
        binding.categoryOptions.setOnCheckedChangeListener((group,id)->draft.category(group.indexOfChild(group.findViewById(id))));
        binding.severityOptions.setOnCheckedChangeListener((group,id)->draft.severity(group.indexOfChild(group.findViewById(id))));
        binding.etDescription.setText(draft.description());
        binding.etDescription.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int a,int c,int d) {}
            public void onTextChanged(CharSequence s,int a,int b,int c) { draft.description(s.toString()); }
            public void afterTextChanged(android.text.Editable s) {}
        });
        binding.btnBack.setOnClickListener(v->back());
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { back(); }
        });
        binding.btnStartReport.setOnClickListener(v->go(1));
        binding.btnOpenReports.setOnClickListener(v->((MainActivity)requireActivity()).openTv2Fragment(new MyReportsFragment()));
        binding.btnPickGallery.setOnClickListener(v->gallery.launch(new String[]{"image/jpeg","image/png"}));
        binding.btnCamera.setOnClickListener(v->takePhoto());
        binding.btnUseImage.setOnClickListener(v->{ if(draft.image()==null) message(R.string.report_missing_image); else go(3); });
        binding.btnAdjustPin.setOnClickListener(v->useLocation(location->{
            draft.point(new Point(location.getLatitude(),location.getLongitude())); renderPoint();
            if(map!=null) map.animateCamera(CameraUpdateFactory.newLatLngZoom(MapsSupport.latLng(draft.point()),17));
        }));
        binding.btnSimulateAi.setOnClickListener(v->{
            if(draft.point()==null) message(R.string.report_location_missing);
            else go(4); // Disconnected AI: show honest fallback immediately, never fake loading/detection.
        });
        binding.btnManual.setOnClickListener(v->go(5));
        binding.btnReview.setOnClickListener(v->{
            if(draft.category()<0) message(R.string.report_missing_category); else go(6);
        });
        binding.confirmReport.setOnCheckedChangeListener((button,checked)->draft.confirmed(checked));
        binding.btnSubmitReport.setOnClickListener(v->submit());
        binding.btnRetryReport.setOnClickListener(v->submit());
        binding.btnEditReport.setOnClickListener(v->go(5));
        binding.btnEditFailed.setOnClickListener(v->go(6));
        if(MapsSupport.configured(requireContext())) MapsSupport.attach(this,R.id.reportMap,ready->{
            map=ready;
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(10.7554,106.6782),13));
            map.setOnMapClickListener(p->{draft.point(new Point(p.latitude,p.longitude));renderPoint();});
            map.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {
                public void onMarkerDragStart(Marker m) {}
                public void onMarkerDrag(Marker m) {}
                public void onMarkerDragEnd(Marker m) { draft.point(new Point(m.getPosition().latitude,m.getPosition().longitude));renderPoint(); }
            });
            renderPoint();
            if(draft.point()!=null) map.moveCamera(CameraUpdateFactory.newLatLngZoom(MapsSupport.latLng(draft.point()),16));
        });
        else { binding.reportMap.setVisibility(View.GONE);binding.mapHint.setText(R.string.report_no_map); }
        render();
    }
    private int dp(int value) { return (int)(value*getResources().getDisplayMetrics().density); }
    private void acceptImage(Uri uri) {
        if(draft==null) draft=new ViewModelProvider(requireActivity()).get(ReportDraft.class);
        draft.image(uri.toString()); go(2);
    }
    private void takePhoto() {
        try {
            File dir=new File(requireContext().getCacheDir(),"report-photos");
            if(!dir.isDirectory()&&!dir.mkdirs()) throw new java.io.IOException();
            File file=File.createTempFile("report-",".jpg",dir);
            Uri uri=FileProvider.getUriForFile(requireContext(),requireContext().getPackageName()+".photos",file);
            draft.camera(uri.toString()); camera.launch(uri);
        } catch(java.io.IOException|RuntimeException error) { message(R.string.report_camera_unavailable); }
    }
    private void message(int string) { if(binding!=null) Snackbar.make(binding.getRoot(),string,Snackbar.LENGTH_LONG).show(); }
    private void go(int step) { draft.step(step); if(binding!=null) { render();binding.wizardScroll.smoothScrollTo(0,0); } }
    private void back() {
        int step=draft.step();
        if(step==0) ((MainActivity)requireActivity()).openMap();
        else go(step==7?6:step-1);
    }
    private void renderPoint() {
        if(binding==null) return;
        binding.tvCoordinates.setText(draft.point()==null?getString(R.string.report_location_missing):draft.point().format());
        if(map!=null&&draft.point()!=null) {
            if(pin!=null)pin.remove();
            pin=map.addMarker(new MarkerOptions().position(MapsSupport.latLng(draft.point())).draggable(true).title(getString(R.string.report_pin)));
        }
    }
    private void render() {
        if(binding==null)return;
        int step=draft.step();
        View[] groups={binding.stepStart,binding.stepPhoto,binding.stepGps,binding.cardAiResult,binding.stepType,binding.stepReview,binding.stepFailure};
        for(View group:groups) group.setVisibility(View.GONE);
        (step==0?binding.stepStart:step<=2?binding.stepPhoto:step==3?binding.stepGps:step==4?binding.cardAiResult:step==5?binding.stepType:step==6?binding.stepReview:binding.stepFailure).setVisibility(View.VISIBLE);
        int[] titles={R.string.report_intro_title,R.string.report_image_title,R.string.report_preview_title,R.string.report_gps_title,R.string.report_ai_title,R.string.report_type_title,R.string.report_review_title,R.string.report_failure_title};
        binding.tvReportTitle.setText(titles[step]);
        binding.stepProgress.setText(getString(R.string.report_step,Math.max(1,Math.min(6,step))));
        binding.stepProgress.setVisibility(step==0||step==7?View.GONE:View.VISIBLE);
        binding.btnUseImage.setVisibility(step==2?View.VISIBLE:View.GONE);
        binding.btnCamera.setVisibility(step==1?View.VISIBLE:View.GONE);
        binding.btnPickGallery.setText(step==2?R.string.report_change_image:R.string.report_gallery);
        if(draft.image()!=null) {
            try { binding.imgPreview.setImageURI(Uri.parse(draft.image())); binding.reviewImage.setImageDrawable(binding.imgPreview.getDrawable()); }
            catch(RuntimeException error) { draft.image(null);message(R.string.image_read_error); }
        } else binding.imgPreview.setImageResource(R.drawable.ic_camera);
        binding.confirmReport.setChecked(draft.confirmed());
        if(draft.category()>=0&&draft.point()!=null) binding.reviewSummary.setText(getString(R.string.report_summary,categories[draft.category()],severities[draft.severity()],draft.point().format(),draft.description()));
        renderPoint();
    }
    private void submit() {
        if(draft.image()==null) {message(R.string.report_missing_image);go(1);return;}
        if(draft.point()==null) {message(R.string.report_location_missing);go(3);return;}
        if(draft.category()<0) {message(R.string.report_missing_category);go(5);return;}
        if(!draft.confirmed()) {message(R.string.report_missing_consent);return;}
        go(7); // No service connected. Keep draft; do not emit REPORTS_CHANGED or show success.
    }
    @Override protected void onLocating(boolean locating) { if(binding!=null)binding.btnAdjustPin.setEnabled(!locating); }
    @Override public void onDestroyView() { pin=null;map=null;binding=null;super.onDestroyView(); }
}
