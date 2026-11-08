package com.comeforfood.saferoute.feature.map;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import com.comeforfood.saferoute.MainActivity;
import com.comeforfood.saferoute.R;
import com.comeforfood.saferoute.databinding.FragmentMapBinding;
import com.comeforfood.saferoute.databinding.ItemHazardBinding;
import com.comeforfood.saferoute.feature.shared.ForegroundLocation;
import com.comeforfood.saferoute.feature.shared.LocationAwareFragment;
import com.comeforfood.saferoute.feature.shared.MapsSupport;
import com.comeforfood.saferoute.feature.shared.Point;
import com.comeforfood.saferoute.feature.shared.MapRoutingDataSource;
import com.comeforfood.saferoute.feature.shared.MapRoutingViewModel;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.Collections;
import java.util.List;

public class MapFragment extends LocationAwareFragment {
    private FragmentMapBinding binding;
    private MapRoutingViewModel model;
    private GoogleMap map;
    private final HazardMarkerRenderer markers = new HazardMarkerRenderer();
    private List<Hazard> visibleReports = Collections.emptyList();
    private boolean hasUnreadNotification = true;
    private boolean panelExpanded = true;
    private float panelTouchY;
    private static final String PANEL_EXPANDED = "map.hazardPanelExpanded";

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle saved) {
        binding = FragmentMapBinding.inflate(inflater, parent, false); return binding.getRoot();
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
        if (saved != null) panelExpanded = saved.getBoolean(PANEL_EXPANDED, true);
        configureHazardPanel();
        model = new ViewModelProvider(requireActivity()).get(MapRoutingViewModel.class);
        binding.verified.setChecked(model.verifiedOnly());
        binding.verified.setOnCheckedChangeListener((button, checked) -> updateFilters());
        binding.refresh.setOnClickListener(v -> model.refreshReports());
        binding.badgeNotification.setVisibility(hasUnreadNotification ? View.VISIBLE : View.GONE);
        binding.btnNotification.setOnClickListener(v -> {
            hasUnreadNotification = false;
            binding.badgeNotification.setVisibility(View.GONE);
            ((MainActivity) requireActivity()).openFlow(com.comeforfood.saferoute.feature.shared.FlowPageFragment.Page.NOTIFICATIONS);
        });
        binding.routes.setOnClickListener(v -> ((MainActivity) requireActivity()).openRoutes(null));
        binding.location.setOnClickListener(v -> useLocation(location -> {
            Point point = new Point(location.getLatitude(), location.getLongitude());
            model.selectEndpoint(true, getString(R.string.my_location), point);
            if (map == null) {
                binding.mapStatus.setVisibility(View.VISIBLE);
                binding.mapStatus.setText(getString(R.string.location_origin_saved, point.format()));
            }
            if (map != null) {
                enableLocation();
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(), location.getLongitude()), 15f));
            }
        }));
        binding.searchArea.setOnClickListener(v -> ((MainActivity)requireActivity()).openFeatureFragment(MapFlowFragment.at("search",null)));
        binding.openFilters.setOnClickListener(v -> ((MainActivity)requireActivity()).openFeatureFragment(MapFlowFragment.at("filters",null)));
        binding.openNearby.setOnClickListener(v -> ((MainActivity)requireActivity()).openFeatureFragment(MapFlowFragment.at("nearby",null)));
        model.reports().observe(getViewLifecycleOwner(), this::render);
        if (MapsSupport.configured(requireContext())) {
            binding.mapPlaceholder.setVisibility(View.GONE);
            MapsSupport.attach(this, R.id.map, ready -> {
                map = ready;
                map.getUiSettings().setMyLocationButtonEnabled(false);
                float density = getResources().getDisplayMetrics().density;
                map.setPadding(0, Math.round(180 * density), 0, Math.round(130 * density));
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(10.7554, 106.6782), 13f));
                if (getArguments()!=null && getArguments().containsKey("center.lat")) map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(getArguments().getDouble("center.lat"),getArguments().getDouble("center.lon")),16f));
                map.setOnMapLoadedCallback(() -> { if (binding != null) binding.mapStatus.setText(R.string.map_ready); });
                map.setOnMarkerClickListener(marker -> {
                    if (marker.getTag() instanceof Hazard) {
                        map.animateCamera(CameraUpdateFactory.newLatLng(marker.getPosition()), 300, null);
                        preview((Hazard) marker.getTag());
                        return true;
                    }
                    return false;
                });
                enableLocation();
                markers.render(requireContext(), map, visibleReports);
            });
        }
    }
    @SuppressLint("ClickableViewAccessibility")
    private void configureHazardPanel() {
        binding.hazardPanelToggle.setOnClickListener(v -> setPanelExpanded(!panelExpanded));
        binding.hazardPanelToggle.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    panelTouchY = event.getRawY();
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                case android.view.MotionEvent.ACTION_MOVE:
                    return true;
                case android.view.MotionEvent.ACTION_UP:
                    float delta = event.getRawY() - panelTouchY;
                    float threshold = android.view.ViewConfiguration.get(v.getContext()).getScaledTouchSlop();
                    if (Math.abs(delta) > threshold) setPanelExpanded(delta < 0);
                    else v.performClick();
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    return true;
                case android.view.MotionEvent.ACTION_CANCEL:
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    return true;
                default: return false;
            }
        });
        setPanelExpanded(panelExpanded);
    }
    private void setPanelExpanded(boolean expanded) {
        panelExpanded = expanded;
        binding.hazardPanelContent.setVisibility(expanded ? View.VISIBLE : View.GONE);
        binding.hazardPanelToggle.setRotation(expanded ? 0f : 180f);
        binding.hazardPanelToggle.setContentDescription(getString(expanded
                ? R.string.hazard_panel_collapse : R.string.hazard_panel_expand));
    }
    @Override public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean(PANEL_EXPANDED, panelExpanded);
    }
    @Override public void onResume() { super.onResume(); model.refreshReports(); }
    @SuppressLint("MissingPermission")
    private void enableLocation() {
        if (map != null && ForegroundLocation.permitted(requireContext())) {
            try { map.setMyLocationEnabled(true); } catch (SecurityException ignored) {}
        }
    }
    private void updateFilters() {
        model.filters(model.category(), binding.verified.isChecked());
        render(model.reports().getValue());
    }
    private void render(MapRoutingViewModel.State<List<Hazard>> state) {
        if (binding == null || state == null) return;
        binding.loading.setVisibility(state.loading ? View.VISIBLE : View.GONE);
        binding.refresh.setEnabled(!state.loading);
        visibleReports = state.data == null ? Collections.emptyList() : HazardFilter.apply(state.data, model.category(), model.verifiedOnly());
        if (state.loading) binding.status.setText(R.string.reports_loading);
        else if (state.error != null) binding.status.setText(state.error == MapRoutingDataSource.Failure.NOT_CONNECTED ? R.string.reports_disconnected : R.string.reports_error);
        else binding.status.setText(visibleReports.isEmpty() ? getString(R.string.reports_empty) : getString(R.string.reports_count, visibleReports.size()));
        binding.reportList.removeAllViews();
        binding.reportScroll.setVisibility(visibleReports.isEmpty() ? View.GONE : View.VISIBLE);
        for (Hazard report : visibleReports) {
            ItemHazardBinding item = ItemHazardBinding.inflate(getLayoutInflater(), binding.reportList, false);
            item.title.setText(report.title.isEmpty() ? report.type.label : report.title);
            item.type.setText(report.type.label);
            boolean verified = report.status == Hazard.Status.VERIFIED;
            item.status.setText(verified ? R.string.verified : R.string.pending);
            item.status.setTextColor(ContextCompat.getColor(requireContext(), verified ? R.color.verified : R.color.warning));
            item.getRoot().setOnClickListener(v -> preview(report));
            binding.reportList.addView(item.getRoot());
        }
        if (map != null) markers.render(requireContext(), map, visibleReports);
    }
    private void preview(Hazard hazard) {
        boolean verified = hazard.status == Hazard.Status.VERIFIED;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(hazard.title.isEmpty() ? getString(R.string.hazard_preview) : hazard.title)
                .setMessage(hazard.type.label + "\n" + getString(verified ? R.string.verified : R.string.pending)
                        + "\n\n" + hazard.description + "\n\n" + hazard.point.format()
                        + "\n\n" + getString(verified ? R.string.verified_note : R.string.pending_note))
                .setPositiveButton(R.string.go_here, (dialog, which) -> ((MainActivity) requireActivity()).openRoutes(hazard.point))
                .setNeutralButton(R.string.flow_alert_detail, (dialog, which) -> ((MainActivity)requireActivity()).openFeatureFragment(com.comeforfood.saferoute.feature.reports.ReportDetailFragment.at(hazard.id)))
                .setNegativeButton(R.string.close, null).show();
    }
    @Override protected void onLocating(boolean loading) {
        if (binding != null) { binding.location.setEnabled(!loading); binding.location.setText(loading ? R.string.locating : R.string.my_location); }
    }
    @Override public void onDestroyView() {
        markers.clear(); map = null; binding = null; super.onDestroyView();
    }
}
