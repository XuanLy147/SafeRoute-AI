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
    private static final String ARG_SKIP_INTRODUCTION = "report.skip_introduction";
    private FragmentReportWizardBinding binding;
    private ReportDraft draft;
    private boolean busy;
    private String previewUri;
    private retrofit2.Call<com.comeforfood.saferoute.core.model.DetectionDto.DetectionResponse> detectionCall;
    private GoogleMap map;
    private Marker pin;
    private String[] categories, severities;
    private com.comeforfood.saferoute.feature.shared.MapRoutingDataSource.Request manualAddressRequest;
    private androidx.appcompat.app.AlertDialog manualAddressDialog;
    private int manualAddressGeneration;
    private boolean resolvingManualAddress;
    private com.comeforfood.saferoute.feature.shared.MapRoutingDataSource.Request locationAddressRequest;
    private com.comeforfood.saferoute.feature.shared.Point locationLookupPoint;
    private int locationAddressGeneration;
    private boolean resolvingLocationAddress;
    public static ReportWizardFragment forNavigation() {
        ReportWizardFragment fragment = new ReportWizardFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_SKIP_INTRODUCTION, true);
        fragment.setArguments(args);
        return fragment;
    }

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
        binding.tvAiDisclaimer.setVisibility(View.GONE);
        draft=new ViewModelProvider(requireActivity()).get(ReportDraft.class);
        if (getArguments() != null && getArguments().getBoolean(ARG_SKIP_INTRODUCTION) && draft.step() == 0) draft.step(1);
        categories=getResources().getStringArray(R.array.report_categories);
        severities=getResources().getStringArray(R.array.report_severities);
        for(int i=0;i<categories.length;i++) {
            RadioButton radio=new RadioButton(requireContext()); radio.setId(View.generateViewId()); radio.setText(categories[i]); radio.setMinHeight(dp(48));radio.setBackgroundResource(R.drawable.bg_option);radio.setPadding(dp(12),dp(6),dp(12),dp(6));
            android.widget.RadioGroup.LayoutParams optionParams=new android.widget.RadioGroup.LayoutParams(-1,-2);optionParams.bottomMargin=dp(4);
            binding.categoryOptions.addView(radio,optionParams); if(i==draft.category()) radio.setChecked(true);
        }
        for(int i=0;i<severities.length;i++) {
            RadioButton radio=new RadioButton(requireContext()); radio.setId(View.generateViewId()); radio.setText(severities[i]); radio.setMinHeight(dp(48));radio.setBackgroundResource(R.drawable.bg_option);radio.setPadding(dp(12),dp(6),dp(12),dp(6));
            android.widget.RadioGroup.LayoutParams optionParams=new android.widget.RadioGroup.LayoutParams(-1,-2);optionParams.bottomMargin=dp(4);
            binding.severityOptions.addView(radio,optionParams); if(i==draft.severity()) radio.setChecked(true);
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
        binding.btnOpenReports.setOnClickListener(v->((MainActivity)requireActivity()).openFeatureFragment(new MyReportsFragment()));
        binding.btnPickGallery.setOnClickListener(v->gallery.launch(new String[]{"image/jpeg","image/png"}));
        binding.btnCamera.setOnClickListener(v->takePhoto());
        binding.btnUseImage.setOnClickListener(v->{ if(draft.image()==null) message(R.string.report_missing_image); else go(3); });
        binding.mapHint.setVisibility(View.GONE);
        binding.tvCoordinates.setVisibility(View.VISIBLE);
        binding.btnAdjustPin.setText(R.string.my_location);
        android.widget.EditText manualPoint=new android.widget.EditText(requireContext());
        manualPoint.setId(R.id.manualLocation);
        manualPoint.setHint(R.string.report_manual_location_hint);
        manualPoint.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS);
        manualPoint.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        manualPoint.setSingleLine(true);
        manualPoint.setGravity(android.view.Gravity.CENTER);
        manualPoint.setMinHeight(dp(56));
        manualPoint.setPadding(dp(18),0,dp(18),0);
        manualPoint.setBackgroundResource(R.drawable.bg_soft_input);
        android.widget.TextView orLabel=new android.widget.TextView(requireContext());
        orLabel.setText(R.string.report_location_or);
        orLabel.setGravity(android.view.Gravity.CENTER);
        orLabel.setTextSize(14);
        orLabel.setTextColor(requireContext().getColor(R.color.muted));
        int manualInputIndex=binding.stepGps.indexOfChild(binding.btnAdjustPin)+1;
        android.widget.LinearLayout.LayoutParams orParams=new android.widget.LinearLayout.LayoutParams(-1,-2);
        orParams.topMargin=dp(8);
        orParams.bottomMargin=dp(8);
        binding.stepGps.addView(orLabel,manualInputIndex,orParams);
        android.widget.LinearLayout.LayoutParams inputParams=new android.widget.LinearLayout.LayoutParams(-1,-2);
        inputParams.bottomMargin=dp(12);
        binding.stepGps.addView(manualPoint,manualInputIndex+1,inputParams);
        binding.btnAdjustPin.setOnClickListener(v->useLocation(location->{
            draft.point(new Point(location.getLatitude(),location.getLongitude())); renderPoint();
            if(map!=null) map.animateCamera(CameraUpdateFactory.newLatLngZoom(MapsSupport.latLng(draft.point()),17));
        }));
        binding.btnSimulateAi.setOnClickListener(v->{
            String manualAddress=manualPoint.getText().toString().trim();
            if(!manualAddress.isEmpty()) {
                if(manualAddress.length()>250) {
                    manualPoint.setError(getString(R.string.address_too_long));
                    return;
                }
                manualPoint.setError(null);
                resolveManualAddress(manualAddress,manualPoint);
                return;
            }
            if(draft.point()==null) message(R.string.report_location_missing);
            else analyze();
        });
        binding.btnManual.setOnClickListener(v->{if(detectionCall!=null)detectionCall.cancel();busy=false;go(5);});
        binding.btnReview.setOnClickListener(v->{
            if(draft.category()<0) message(R.string.report_missing_category); else go(6);
        });
        binding.confirmReport.setOnCheckedChangeListener((button,checked)->draft.confirmed(checked));
        binding.btnSubmitReport.setOnClickListener(v->submit());
        binding.btnRetryReport.setOnClickListener(v->submit());
        binding.btnEditReport.setOnClickListener(v->go(5));
        binding.btnEditFailed.setOnClickListener(v->go(6));
        com.google.android.material.button.MaterialButton inspect=new com.google.android.material.button.MaterialButton(requireContext());inspect.setText("Kiểm tra Báo cáo của tôi");binding.stepFailure.addView(inspect);inspect.setOnClickListener(v->((MainActivity)requireActivity()).openFeatureFragment(new MyReportsFragment()));
        com.google.android.material.button.MaterialButton login=new com.google.android.material.button.MaterialButton(requireContext());login.setText("Đăng nhập / đổi tài khoản");binding.stepFailure.addView(login);login.setOnClickListener(v->((MainActivity)requireActivity()).openFeatureFragment(new com.comeforfood.saferoute.feature.auth.LoginFragment()));
        com.google.android.material.button.MaterialButton discard=new com.google.android.material.button.MaterialButton(requireContext());discard.setText("Bỏ bản nháp để tạo báo cáo mới");binding.stepFailure.addView(discard);discard.setOnClickListener(v->new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext()).setTitle("Bỏ bản nháp?").setMessage("Nếu lần gửi trước chưa rõ kết quả, hãy kiểm tra Báo cáo của tôi để tránh gửi trùng.").setNegativeButton("Giữ bản nháp",null).setPositiveButton("Đã kiểm tra, bỏ bản nháp",(dialog,which)->{cancelManualAddressSearch();cancelLocationAddressLookup();manualPoint.setText("");draft.clear();go(1);}).show());
        if(MapsSupport.configured(requireContext())) MapsSupport.attach(this,R.id.reportMap,ready->{
            map=ready;
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(10.7554,106.6782),13));
            map.setOnMapClickListener(p->{draft.point(new Point(p.latitude,p.longitude));renderPoint();});
            map.setOnPoiClickListener(poi->{
                Point point=new Point(poi.latLng.latitude,poi.latLng.longitude);
                draft.point(point);
                String placeName=poi.name==null?null:poi.name.trim();
                if(placeName!=null&&placeName.isEmpty())placeName=null;
                draft.locationLabel(placeName);
                renderPoint();
                resolveLocationAddress(point,placeName);
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(poi.latLng,17));
            });
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
    private void resolveManualAddress(String query,android.widget.EditText field) {
        cancelManualAddressSearch();
        final int generation=manualAddressGeneration;
        resolvingManualAddress=true;
        binding.btnSimulateAi.setEnabled(false);
        binding.btnSimulateAi.setText(R.string.report_address_searching);
        com.comeforfood.saferoute.feature.routing.AddressSearch search=
                ((com.comeforfood.saferoute.SafeRouteApp)requireActivity().getApplication()).getAddressSearch();
        try {
            com.comeforfood.saferoute.feature.shared.MapRoutingDataSource.Request request=search.search(query,
                    new com.comeforfood.saferoute.feature.routing.AddressSearch.Callback() {
                @Override public void success(java.util.List<com.comeforfood.saferoute.feature.routing.AddressSearch.Place> places) {
                    dispatchAddressResult(()->{
                        if(binding==null||generation!=manualAddressGeneration)return;
                        manualAddressRequest=null;
                        finishManualAddressSearch();
                        if(places==null||places.isEmpty()) {
                            field.setError(getString(R.string.address_not_found));
                        } else if(places.size()==1) {
                            selectManualAddress(places.get(0),field);
                        } else {
                            String[] labels=new String[places.size()];
                            for(int i=0;i<places.size();i++)labels[i]=places.get(i).label;
                            manualAddressDialog=new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                                    .setTitle(R.string.report_address_choose)
                                    .setItems(labels,(dialog,which)->{
                                        manualAddressDialog=null;
                                        if(binding!=null&&generation==manualAddressGeneration)
                                            selectManualAddress(places.get(which),field);
                                    })
                                    .setNegativeButton(R.string.address_cancel,(dialog,which)->manualAddressDialog=null)
                                    .setOnCancelListener(dialog->manualAddressDialog=null)
                                    .create();
                            manualAddressDialog.show();
                        }
                    });
                }
                @Override public void failure(com.comeforfood.saferoute.feature.routing.AddressSearch.Failure error) {
                    dispatchAddressResult(()->{
                        if(binding==null||generation!=manualAddressGeneration)return;
                        manualAddressRequest=null;
                        finishManualAddressSearch();
                        field.setError(getString(error==com.comeforfood.saferoute.feature.routing.AddressSearch.Failure.UNAVAILABLE
                                ?R.string.address_unavailable:R.string.address_error));
                    });
                }
            });
            if(generation==manualAddressGeneration&&resolvingManualAddress)manualAddressRequest=request;
            else request.cancel();
        } catch(RuntimeException error) {
            if(generation==manualAddressGeneration) {
                finishManualAddressSearch();
                field.setError(getString(R.string.address_error));
            }
        }
    }
    private void dispatchAddressResult(Runnable result) {
        androidx.fragment.app.FragmentActivity activity=getActivity();
        if(activity!=null)activity.runOnUiThread(result);
    }
    private void selectManualAddress(com.comeforfood.saferoute.feature.routing.AddressSearch.Place place,
                                     android.widget.EditText field) {
        field.setError(null);
        field.setText(place.label);
        draft.point(place.point);
        draft.locationLabel(place.label);
        renderPoint();
        if(map!=null)map.animateCamera(CameraUpdateFactory.newLatLngZoom(MapsSupport.latLng(place.point),17));
        analyze();
    }
    private void finishManualAddressSearch() {
        resolvingManualAddress=false;
        if(binding!=null) {
            binding.btnSimulateAi.setEnabled(true);
            binding.btnSimulateAi.setText(R.string.report_analyze);
        }
    }
    private void cancelManualAddressSearch() {
        manualAddressGeneration++;
        if(manualAddressRequest!=null)manualAddressRequest.cancel();
        manualAddressRequest=null;
        if(manualAddressDialog!=null)manualAddressDialog.dismiss();
        manualAddressDialog=null;
        if(resolvingManualAddress)finishManualAddressSearch();
    }
    private void updateReviewSummary() {
        Point point=draft.point();
        if(draft.category()<0||point==null)return;
        String address=draft.locationLabel();
        if(address==null||address.trim().isEmpty()) {
            binding.reviewSummary.setText(getString(R.string.report_summary,categories[draft.category()],
                    severities[draft.severity()],getString(R.string.report_address_loading),draft.description()));
            resolveLocationAddress(point);
            return;
        }
        binding.reviewSummary.setText(getString(R.string.report_summary,categories[draft.category()],
                severities[draft.severity()],address,draft.description()));
    }
    private void resolveLocationAddress(Point point) {
        resolveLocationAddress(point,null);
    }
    private void resolveLocationAddress(Point point,String fallbackLabel) {
        if(resolvingLocationAddress&&locationLookupPoint!=null&&locationLookupPoint.sameAs(point))return;
        cancelLocationAddressLookup();
        final int generation=locationAddressGeneration;
        locationLookupPoint=point;
        resolvingLocationAddress=true;
        draft.locationLabel(fallbackLabel==null?getString(R.string.report_address_loading):fallbackLabel);
        if(binding!=null)binding.tvCoordinates.setText(getString(R.string.report_location_address,draft.locationLabel()));
        com.comeforfood.saferoute.feature.routing.AddressSearch search=
                ((com.comeforfood.saferoute.SafeRouteApp)requireActivity().getApplication()).getAddressSearch();
        try {
            com.comeforfood.saferoute.feature.shared.MapRoutingDataSource.Request request=search.reverse(point,
                    new com.comeforfood.saferoute.feature.routing.AddressSearch.Callback() {
                @Override public void success(java.util.List<com.comeforfood.saferoute.feature.routing.AddressSearch.Place> places) {
                    dispatchAddressResult(()->{
                        if(!isCurrentLocationLookup(generation,point))return;
                        locationAddressRequest=null;
                        resolvingLocationAddress=false;
                        locationLookupPoint=null;
                        com.comeforfood.saferoute.feature.routing.AddressSearch.Place result=
                                places==null||places.isEmpty()?null:places.get(0);
                        String label=preferredLocationLabel(fallbackLabel,result);
                        draft.locationLabel(label==null||label.trim().isEmpty()
                                ?fallbackLabel==null?getString(R.string.report_address_unavailable):fallbackLabel:label);
                        binding.tvCoordinates.setText(getString(R.string.report_location_address,draft.locationLabel()));
                        updateReviewSummary();
                    });
                }
                @Override public void failure(com.comeforfood.saferoute.feature.routing.AddressSearch.Failure error) {
                    dispatchAddressResult(()->{
                        if(!isCurrentLocationLookup(generation,point))return;
                        locationAddressRequest=null;
                        resolvingLocationAddress=false;
                        locationLookupPoint=null;
                        draft.locationLabel(fallbackLabel==null
                                ?getString(R.string.report_address_unavailable):fallbackLabel);
                        binding.tvCoordinates.setText(getString(R.string.report_location_address,draft.locationLabel()));
                        updateReviewSummary();
                    });
                }
            });
            if(isCurrentLocationLookup(generation,point)&&resolvingLocationAddress)locationAddressRequest=request;
            else request.cancel();
        } catch(RuntimeException error) {
            if(isCurrentLocationLookup(generation,point)) {
                resolvingLocationAddress=false;
                locationLookupPoint=null;
                draft.locationLabel(fallbackLabel==null
                        ?getString(R.string.report_address_unavailable):fallbackLabel);
                binding.tvCoordinates.setText(getString(R.string.report_location_address,draft.locationLabel()));
                updateReviewSummary();
            }
        }
    }
    public static String preferredLocationLabel(String fallbackLabel,
            com.comeforfood.saferoute.feature.routing.AddressSearch.Place result) {
        if(fallbackLabel!=null&&(result==null||!result.streetAddress))return fallbackLabel;
        if(result!=null&&!result.label.trim().isEmpty())return result.label;
        return fallbackLabel;
    }
    private boolean isCurrentLocationLookup(int generation,Point point) {
        return binding!=null&&generation==locationAddressGeneration&&draft.point()!=null
                &&draft.point().sameAs(point);
    }
    private void cancelLocationAddressLookup() {
        locationAddressGeneration++;
        if(locationAddressRequest!=null)locationAddressRequest.cancel();
        locationAddressRequest=null;
        locationLookupPoint=null;
        resolvingLocationAddress=false;
        if(draft!=null&&getString(R.string.report_address_loading).equals(draft.locationLabel()))
            draft.locationLabel(null);
    }
    private void go(int step) { draft.step(step); if(binding!=null) { render();binding.wizardScroll.smoothScrollTo(0,0); } }
    private void back() {
        if(busy){notice("Đang xử lý. Vui lòng đợi hoặc quay lại sau khi hoàn tất.");return;}
        int step=draft.step();
        boolean skipIntroduction=getArguments()!=null&&getArguments().getBoolean(ARG_SKIP_INTRODUCTION);
        if(step==0||(step==1&&skipIntroduction)) ((MainActivity)requireActivity()).openMap();
        else go(step==7?6:step-1);
    }
    private void renderPoint() {
        if(binding==null) return;
        Point point=draft.point();
        if(point==null)binding.tvCoordinates.setText(getString(R.string.report_location_missing));
        else {
            String address=draft.locationLabel();
            if(address==null||address.trim().isEmpty())resolveLocationAddress(point);
            else binding.tvCoordinates.setText(getString(R.string.report_location_address,address));
        }
        if(map!=null&&draft.point()!=null) {
            if(pin!=null)pin.remove();
            pin=map.addMarker(new MarkerOptions().position(MapsSupport.latLng(draft.point())).draggable(true).title(getString(R.string.report_pin)));
        }
    }
    private void render() {
        if(binding==null)return;
        // Draft is the source of truth after discard, restore or image replacement.
        syncChoice(binding.categoryOptions,draft.category());
        syncChoice(binding.severityOptions,draft.severity());
        if(!binding.etDescription.getText().toString().equals(draft.description()))
            binding.etDescription.setText(draft.description());
        int step=draft.step();
        View[] groups={binding.stepStart,binding.stepPhoto,binding.stepGps,binding.cardAiResult,binding.stepType,binding.stepReview,binding.stepFailure};
        for(View group:groups) group.setVisibility(View.GONE);
        (step==0?binding.stepStart:step<=2?binding.stepPhoto:step==3?binding.stepGps:step==4?binding.cardAiResult:step==5?binding.stepType:step==6?binding.stepReview:binding.stepFailure).setVisibility(View.VISIBLE);
        int[] titles={R.string.report_intro_title,R.string.report_image_title,R.string.report_preview_title,R.string.report_gps_title,R.string.report_ai_title,R.string.report_type_title,R.string.report_review_title,R.string.report_failure_title};
        binding.tvReportTitle.setText(titles[step]);
        binding.stepProgress.setText(getString(R.string.report_step,Math.max(1,Math.min(6,step))));
        binding.stepProgress.setVisibility(step==0||step==7?View.GONE:View.VISIBLE);
        binding.wizardProgress.setVisibility(step==0||step==7?View.GONE:View.VISIBLE);binding.wizardProgress.setProgress(Math.max(0,Math.min(6,step)));
        int imagePadding=draft.image()==null?dp(64):0;binding.imgPreview.setPadding(imagePadding,imagePadding,imagePadding,imagePadding);
        binding.btnUseImage.setVisibility(step==2?View.VISIBLE:View.GONE);
        binding.btnCamera.setVisibility(step==1?View.VISIBLE:View.GONE);
        binding.btnPickGallery.setText(step==2?R.string.report_change_image:R.string.report_gallery);
        if(draft.image()!=null && !draft.image().equals(previewUri)) {
            previewUri=draft.image();String requested=previewUri;Uri uri=Uri.parse(requested);
            if("android.resource".equals(uri.getScheme())) {
                // Local resource previews never become upload content; ImageUpload rejects unsupported MIME.
                binding.imgPreview.setImageURI(uri);copyPreview();
            } else com.comeforfood.saferoute.core.network.PhotoPreview.load(getViewLifecycleOwner(),requireContext().getContentResolver(),uri,bitmap->{
                if(binding==null||!requested.equals(draft.image()))return;
                if(bitmap==null){draft.image(null);previewUri=null;render();message(R.string.image_read_error);return;}
                binding.imgPreview.setImageBitmap(bitmap);copyPreview();
            });
        } else if(draft.image()==null) {
            previewUri=null;
            binding.imgPreview.setImageResource(R.drawable.ic_camera);
            copyPreview();
        }
        binding.aiBoxes.setDetections(draft.detections());
        binding.confirmReport.setChecked(draft.confirmed());
        if(step==6)updateReviewSummary();
        else if(draft.category()<0||draft.point()==null)binding.reviewSummary.setText("");
        renderPoint();renderDetections();
    }
    private void syncChoice(android.widget.RadioGroup group,int index) {
        int target=index>=0&&index<group.getChildCount()?group.getChildAt(index).getId():View.NO_ID;
        if(group.getCheckedRadioButtonId()!=target) {
            if(target==View.NO_ID)group.clearCheck();else group.check(target);
        }
    }
    private void copyPreview(){
        binding.reviewImage.setImageDrawable(binding.imgPreview.getDrawable());binding.aiImage.setImageDrawable(binding.imgPreview.getDrawable());
        if(binding.imgPreview.getDrawable()!=null)binding.aiBoxes.setImageSize(binding.imgPreview.getDrawable().getIntrinsicWidth(),binding.imgPreview.getDrawable().getIntrinsicHeight());
        binding.aiBoxes.setDetections(draft.detections());
    }
    private void submit() {
        if(draft.image()==null) {message(R.string.report_missing_image);go(1);return;}
        if(draft.point()==null) {message(R.string.report_location_missing);go(3);return;}
        if(draft.category()<0) {message(R.string.report_missing_category);go(5);return;}
        if(!draft.confirmed()) {message(R.string.report_missing_consent);return;}
        if(busy)return;
        if(draft.uncertain()) {go(7);notice("Kết quả gửi chưa rõ. Mở Báo cáo của tôi để kiểm tra trước khi gửi bản mới.");return;}
        if(!new com.comeforfood.saferoute.core.session.SessionManager(requireContext()).isLoggedIn()) {go(7);notice("Đăng nhập trước khi gửi báo cáo. Bản nháp được giữ lại.");return;}
        busy=true;enable(binding.getRoot(),false);
        if(draft.uploadedId()!=null){createReport();return;}
        try {
            com.comeforfood.saferoute.core.network.ApiCalls.enqueue(getViewLifecycleOwner(),api().upload(com.comeforfood.saferoute.core.network.ImageUpload.part(requireContext().getContentResolver(),Uri.parse(draft.image()))),response->{
                if(!response.has("image_id")){failed("Server thiếu mã ảnh");return;}
                draft.uploadedId(response.get("image_id").getAsString());createReport();
            },this::failed);
        }catch(RuntimeException error){failed("Không đọc được ảnh JPEG/PNG/WEBP. Hãy chọn lại ảnh.");}
    }
    private com.comeforfood.saferoute.core.network.ApiService api(){return ((com.comeforfood.saferoute.SafeRouteApp)requireActivity().getApplication()).api().service;}
    private void notice(String message){if(binding!=null)Snackbar.make(binding.getRoot(),message,Snackbar.LENGTH_LONG).show();}
    private void enable(View v,boolean enabled){v.setEnabled(enabled);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)enable(((ViewGroup)v).getChildAt(i),enabled);}
    private void failed(String message){busy=false;if(binding!=null){enable(binding.getRoot(),true);go(7);notice(message);}}
    private void createReport(){
        String[] codes={"Nứt đường","D40","Ngập nước","Vật cản"};String[] levels={"LOW","MEDIUM","HIGH"};
        com.comeforfood.saferoute.core.model.ReportDto.CreateRequest request=new com.comeforfood.saferoute.core.model.ReportDto.CreateRequest(draft.uploadedId(),draft.point().latitude,draft.point().longitude,codes[draft.category()],levels[draft.severity()],draft.description(),draft.detections());
        request.setModelVersion(draft.modelVersion());
        draft.uncertain(true); // Persist before sending: rotation/process death must not blindly repeat a write.
        com.comeforfood.saferoute.core.network.ApiCalls.enqueueDetailed(getViewLifecycleOwner(),api().create(request),response->{
            if(response.getId()==null){failed("Chưa xác nhận mã báo cáo. Kiểm tra Báo cáo của tôi.");return;}
            busy=false;draft.clear();getParentFragmentManager().setFragmentResult(MainActivity.REPORTS_CHANGED,new Bundle());
            ((MainActivity)requireActivity()).openFeatureFragment(com.comeforfood.saferoute.feature.reports.ReportDetailFragment.at(response.getId()));
        },(code,error)->{if(code>=400&&code<500)draft.uncertain(false);failed(error);});
    }
    private void analyze(){
        if(busy)return;
        // Remove previous inference and consent before rendering a new attempt,
        // including synchronous URI/MIME failure and manual fallback.
        draft.detections(java.util.Collections.emptyList());draft.modelVersion(null);draft.confirmed(false);
        go(4);
        try {
            okhttp3.MultipartBody.Part file=com.comeforfood.saferoute.core.network.ImageUpload.part(requireContext().getContentResolver(),Uri.parse(draft.image()));
            busy=true;binding.tvAiTitle.setText("Đang phân tích ảnh…");binding.tvAiDetectionSummary.setText("Bạn có thể tiếp tục nhập nhãn thủ công.");
            detectionCall=api().detect(file);
            com.comeforfood.saferoute.core.network.ApiCalls.enqueue(getViewLifecycleOwner(),detectionCall,response->{
                busy=false;
                java.util.List<com.comeforfood.saferoute.core.model.DetectionDto> detections=response.getDetections();
                if(detections==null||detections.stream().anyMatch(d->d==null||!d.valid())){aiFailed("Dữ liệu AI không hợp lệ. Hãy nhập nhãn thủ công.");return;}
                draft.modelVersion(response.getModelVersion());draft.detections(detections);renderDetections();binding.aiBoxes.setDetections(detections);binding.tvAiTitle.setText(detections.isEmpty()?"Không phát hiện hư hỏng":"Đề xuất AI — cần bạn xác nhận");
                StringBuilder text=new StringBuilder();for(com.comeforfood.saferoute.core.model.DetectionDto d:detections)text.append(d.getDisplayName()).append(" · ").append(Math.round(d.getConfidence()*100)).append("%\n");
                binding.tvAiDetectionSummary.setText(text.length()==0?"Bạn vẫn có thể nhập nhãn thủ công.":text+"Độ tin cậy AI không phải mức độ nguy hiểm.");
            },this::aiFailed);
        }catch(RuntimeException e){aiFailed("Không đọc được ảnh. Hãy chọn lại ảnh hoặc nhập nhãn thủ công.");}
    }
    private android.widget.LinearLayout detectionEditors;
    private void renderDetections(){
        if(binding==null)return;
        if(detectionEditors==null){detectionEditors=new android.widget.LinearLayout(requireContext());detectionEditors.setOrientation(android.widget.LinearLayout.VERTICAL);binding.cardAiResult.addView(detectionEditors,2);}
        detectionEditors.removeAllViews();
        java.util.ArrayList<com.comeforfood.saferoute.core.model.DetectionDto> detections=draft.detections();
        for(int i=0;i<detections.size();i++){
            final int index=i;com.comeforfood.saferoute.core.model.DetectionDto detection=detections.get(i);
            android.widget.Spinner label=new android.widget.Spinner(requireContext());String[] codes={"D00","D10","D20","D40","Repair"};
            label.setContentDescription("Sửa nhãn phát hiện AI "+(i+1));label.setAdapter(new android.widget.ArrayAdapter<>(requireContext(),android.R.layout.simple_spinner_dropdown_item,java.util.Arrays.stream(codes).map(com.comeforfood.saferoute.core.model.HazardLabel::display).toArray(String[]::new)));
            label.setSelection(java.util.Arrays.asList(codes).indexOf(detection.getClassCode()));detectionEditors.addView(label);
            label.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
                public void onNothingSelected(android.widget.AdapterView<?> p){}
                public void onItemSelected(android.widget.AdapterView<?> p,View v,int position,long id){
                    java.util.ArrayList<com.comeforfood.saferoute.core.model.DetectionDto> current=draft.detections();if(index>=current.size())return;
                    com.comeforfood.saferoute.core.model.DetectionDto old=current.get(index);
                    if(!codes[position].equals(old.getClassCode())){current.set(index,new com.comeforfood.saferoute.core.model.DetectionDto(codes[position],old.getConfidence(),old.getBbox()));draft.detections(current);draft.confirmed(false);binding.aiBoxes.setDetections(current);}
                }
            });
        }
        if(!detections.isEmpty()){
            com.google.android.material.button.MaterialButton clear=new com.google.android.material.button.MaterialButton(requireContext());clear.setText("Bỏ đề xuất AI, nhập nhãn thủ công");detectionEditors.addView(clear);clear.setOnClickListener(v->{draft.detections(java.util.Collections.emptyList());binding.aiBoxes.clear();draft.confirmed(false);go(5);});
        }
    }
    private void aiFailed(String error){busy=false;if(binding!=null){binding.tvAiTitle.setText(R.string.ai_unavailable_title);binding.tvAiDetectionSummary.setText(error);}}
    @Override protected void onLocating(boolean locating) { if(binding!=null)binding.btnAdjustPin.setEnabled(!locating); }
    @Override public void onDestroyView() { cancelManualAddressSearch();cancelLocationAddressLookup();busy=false;previewUri=null;detectionEditors=null;pin=null;map=null;binding=null;super.onDestroyView(); }
}
