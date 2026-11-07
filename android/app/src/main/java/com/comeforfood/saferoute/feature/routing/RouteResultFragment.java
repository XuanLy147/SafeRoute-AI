package com.comeforfood.saferoute.feature.routing;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.comeforfood.saferoute.R;
import com.comeforfood.saferoute.databinding.FragmentRouteResultBinding;
import com.comeforfood.saferoute.databinding.ItemRouteBinding;
import com.comeforfood.saferoute.feature.shared.MapsSupport;
import com.comeforfood.saferoute.feature.shared.Point;
import com.comeforfood.saferoute.feature.shared.Tv1DataSource;
import com.comeforfood.saferoute.feature.shared.Tv1ViewModel;
import com.google.android.gms.maps.GoogleMap;

public class RouteResultFragment extends Fragment {
    private FragmentRouteResultBinding binding;
    private Tv1ViewModel model;
    private GoogleMap map;
    private final RoutePolylineRenderer renderer = new RoutePolylineRenderer();
    private RouteComparison comparison;
    private Route selected;
    private boolean mapAttached;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle saved) {
        binding = FragmentRouteResultBinding.inflate(inflater, parent, false); return binding.getRoot();
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
        model = new ViewModelProvider(requireActivity()).get(Tv1ViewModel.class);
        binding.toolbar.setNavigationOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        binding.edit.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        binding.retry.setOnClickListener(v -> {
            try { model.compare(); }
            catch (IllegalArgumentException error) { requireActivity().getOnBackPressedDispatcher().onBackPressed(); }
        });
        binding.endpoints.setText(getString(R.string.route_endpoints, model.originText(), model.destinationText()));
        model.routes().observe(getViewLifecycleOwner(), this::render);
    }
    private void render(Tv1ViewModel.State<RouteComparison> state) {
        if (binding == null) return;
        renderer.clear();
        selected = null;
        binding.retry.setText(R.string.retry);
        binding.routeList.removeAllViews();
        binding.loading.setVisibility(state.loading ? View.VISIBLE : View.GONE);
        binding.retry.setVisibility(state.loading ? View.GONE : View.VISIBLE);
        binding.map.setVisibility(View.GONE); binding.mapStatus.setVisibility(View.GONE);
        binding.scoreDescription.setVisibility(View.GONE);
        comparison = state.data;
        if (state.loading) { binding.status.setText(R.string.route_loading); return; }
        if (state.requiresRefresh) {
            binding.status.setText(R.string.route_reports_changed);
            binding.retry.setText(R.string.refresh);
            return;
        }
        if (state.error != null) { binding.status.setText(errorText(state.error)); return; }
        if (comparison == null) { binding.status.setText(R.string.route_restore); return; }
        if (comparison.routes.isEmpty()) { binding.status.setText(R.string.route_empty); return; }
        binding.status.setText(comparison.routes.size() == 1 ? getString(R.string.single_route)
                : getString(R.string.multiple_routes, comparison.routes.size()));
        binding.retry.setText(R.string.refresh);
        binding.scoreDescription.setVisibility(View.VISIBLE);
        binding.scoreDescription.setText(comparison.scoreDescription.isEmpty() ? getString(R.string.risk_scale_missing) : comparison.scoreDescription);
        selected = comparison.initialSelection();
        for (Route route : comparison.routes) if (route.id.equals(model.selectedRoute())) selected = route;
        model.selectRoute(selected.id);
        renderCards();
        binding.mapStatus.setVisibility(View.VISIBLE);
        if (MapsSupport.configured(requireContext())) {
            binding.map.setVisibility(View.VISIBLE);
            binding.mapStatus.setText(R.string.map_connecting);
            if (!mapAttached) {
                mapAttached = true;
                MapsSupport.attach(this, R.id.map, ready -> {
                    map = ready;
                    map.setOnMapLoadedCallback(() -> { if (binding != null) binding.mapStatus.setVisibility(View.GONE); });
                    drawRoute();
                });
            } else drawRoute();
        }
    }
    private void renderCards() {
        binding.routeList.removeAllViews();
        int index = 0;
        for (Route route : comparison.routes) {
            ItemRouteBinding item = ItemRouteBinding.inflate(getLayoutInflater(), binding.routeList, false);
            index++;
            item.name.setText(route.name.isEmpty() ? getString(R.string.route_name, index) : route.name);
            item.recommended.setVisibility(route.id.equals(comparison.recommendedId) ? View.VISIBLE : View.GONE);
            item.metrics.setText(getString(R.string.route_metrics, (int) Math.ceil(route.durationSeconds / 60.0), route.distanceMeters / 1000.0));
            item.risk.setText(route.riskScore == null ? getString(R.string.risk_missing) : getString(R.string.risk_value, route.riskScore));
            item.hazards.setText(route.verifiedCount + route.pendingCount == 0 ? getString(R.string.no_hazards)
                    : getString(R.string.hazard_counts, route.verifiedCount, route.pendingCount));
            item.reason.setText(route.reason.isEmpty() ? getString(R.string.no_reason) : route.reason);
            boolean active = route.id.equals(selected.id);
            item.getRoot().setStrokeColor(ContextCompat.getColor(requireContext(), active ? R.color.primary : R.color.outline));
            item.getRoot().setStrokeWidth(Math.round((active ? 2 : 1) * getResources().getDisplayMetrics().density));
            item.select.setText(active ? R.string.selected : R.string.view_route);
            item.select.setEnabled(!active);
            item.select.setOnClickListener(v -> select(route.id));
            binding.routeList.addView(item.getRoot());
        }
    }
    private void select(String id) {
        for (Route route : comparison.routes) if (route.id.equals(id)) { selected = route; model.selectRoute(id); break; }
        renderCards(); drawRoute();
    }
    private void drawRoute() {
        if (binding == null || map == null || comparison == null || comparison.routes.isEmpty()) return;
        Point a = null, b = null;
        try { a = Point.parse(model.origin()); b = Point.parse(model.destination()); } catch (IllegalArgumentException ignored) {}
        renderer.render(requireContext(), map, comparison, selected.id, a, b, this::select);
        View mapView = binding.map;
        mapView.post(() -> {
            if (binding != null && binding.map == mapView && map != null && selected != null
                    && mapView.getWidth() > 0 && mapView.getHeight() > 0)
                renderer.fit(requireContext(), map, selected);
        });
    }
    private int errorText(Tv1DataSource.Failure error) {
        switch (error) {
            case NOT_CONNECTED: return R.string.route_disconnected;
            case NETWORK: return R.string.route_network;
            case UNAUTHORIZED: return R.string.route_auth;
            case FORBIDDEN: return R.string.route_forbidden;
            case RATE_LIMITED: return R.string.route_limit;
            case INVALID_DATA: return R.string.route_invalid;
            default: return R.string.route_server;
        }
    }
    @Override public void onDestroyView() {
        renderer.clear(); map = null; selected = null; comparison = null; mapAttached = false;
        binding = null; super.onDestroyView();
    }
}
