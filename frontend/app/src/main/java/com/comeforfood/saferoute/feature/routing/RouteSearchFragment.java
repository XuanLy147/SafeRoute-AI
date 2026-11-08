package com.comeforfood.saferoute.feature.routing;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import com.comeforfood.saferoute.MainActivity;
import com.comeforfood.saferoute.R;
import com.comeforfood.saferoute.databinding.FragmentRouteSearchBinding;
import com.comeforfood.saferoute.feature.shared.LocationAwareFragment;
import com.comeforfood.saferoute.feature.shared.MapsSupport;
import com.comeforfood.saferoute.feature.shared.Point;
import com.comeforfood.saferoute.feature.shared.MapRoutingViewModel;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

public class RouteSearchFragment extends LocationAwareFragment {
    private FragmentRouteSearchBinding binding;
    private MapRoutingViewModel model;
    private GoogleMap map;
    private Marker aMarker, bMarker;
    private String picking = "A";
    private boolean updatingFields;
    private int searchGeneration;
    private com.comeforfood.saferoute.feature.shared.MapRoutingDataSource.Request addressRequest;
    private androidx.appcompat.app.AlertDialog addressDialog;

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle saved) {
        binding = FragmentRouteSearchBinding.inflate(inflater, parent, false); return binding.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
        model = new ViewModelProvider(requireActivity()).get(MapRoutingViewModel.class);
        if (saved != null) picking = saved.getString("picking", "A");
        binding.origin.setText(model.originText()); binding.destination.setText(model.destinationText());
        TextWatcher watcher = new TextWatcher() {
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                if (updatingFields) return;
                cancelSearch();
                model.editEndpoints(binding.origin.getText().toString(), binding.destination.getText().toString());
                binding.originLayout.setError(null); binding.destinationLayout.setError(null);
                renderEndpoints();
            }
            public void afterTextChanged(Editable text) {}
        };
        binding.origin.addTextChangedListener(watcher); binding.destination.addTextChangedListener(watcher);

        // Bấm/focus vào Điểm xuất phát A -> Thả xuống dropbox với dòng "Vị trí của tôi"
        View.OnFocusChangeListener originFocus = binding.origin.getOnFocusChangeListener();
        binding.origin.setOnFocusChangeListener((v, hasFocus) -> {
            if (originFocus != null) originFocus.onFocusChange(v, hasFocus);
            if (hasFocus) showOriginDropdown();
        });
        binding.origin.setOnClickListener(v -> showOriginDropdown());
        binding.originLayout.setOnClickListener(v -> showOriginDropdown());

        // Bấm/focus vào Điểm đến B -> Thả xuống dropbox với dòng "Vị trí của tôi"
        View.OnFocusChangeListener destFocus = binding.destination.getOnFocusChangeListener();
        binding.destination.setOnFocusChangeListener((v, hasFocus) -> {
            if (destFocus != null) destFocus.onFocusChange(v, hasFocus);
            if (hasFocus) showDestinationDropdown();
        });
        binding.destination.setOnClickListener(v -> showDestinationDropdown());
        binding.destinationLayout.setOnClickListener(v -> showDestinationDropdown());

        // Chọn "Vị trí của tôi" ở dropbox Điểm xuất phát
        binding.location.setOnClickListener(v -> useLocation(location -> {
            Point point = new Point(location.getLatitude(), location.getLongitude());
            chooseEndpoint(true, getString(R.string.my_location), point);
            if (binding != null) binding.originDropdownCard.setVisibility(View.GONE);
            if (map != null) map.animateCamera(CameraUpdateFactory.newLatLngZoom(MapsSupport.latLng(point), 15f));
        }));

        // Chọn "Vị trí của tôi" ở dropbox Điểm đến
        binding.destinationLocation.setOnClickListener(v -> useLocation(location -> {
            Point point = new Point(location.getLatitude(), location.getLongitude());
            chooseEndpoint(false, getString(R.string.my_location), point);
            if (binding != null) binding.destinationDropdownCard.setVisibility(View.GONE);
            if (map != null) map.animateCamera(CameraUpdateFactory.newLatLngZoom(MapsSupport.latLng(point), 15f));
        }));

        // Nút đổi vị trí A và B
        binding.swap.setOnClickListener(v -> {
            cancelSearch(); model.swapEndpoints(); syncFields();
        });

        binding.compare.setOnClickListener(v -> submit());
        binding.destination.setOnEditorActionListener((text, action, event) -> {
            if (action == EditorInfo.IME_ACTION_DONE) { submit(); return true; } return false;
        });

        boolean configured = MapsSupport.configured(requireContext());
        binding.mapContainer.setVisibility(configured ? View.VISIBLE : View.GONE);
        if (configured) {
            MapsSupport.attach(this, R.id.map, ready -> {
                map = ready;
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(10.7554, 106.6782), 13f));
                map.setOnMapClickListener(point -> {
                    Point selected = new Point(point.latitude, point.longitude);
                    chooseEndpoint("A".equals(picking), getString(R.string.map_point_label, picking), selected);
                });
                renderEndpoints();
            });
        }

        if ("B".equals(picking)) {
            showDestinationDropdown();
        } else {
            showOriginDropdown();
        }
    }

    private void showOriginDropdown() {
        picking = "A";
        if (binding != null) {
            binding.originDropdownCard.setVisibility(View.VISIBLE);
            binding.destinationDropdownCard.setVisibility(View.GONE);
            binding.originLabel.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.primary));
            binding.destinationLabel.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.muted));
        }
    }

    private void showDestinationDropdown() {
        picking = "B";
        if (binding != null) {
            binding.destinationDropdownCard.setVisibility(View.VISIBLE);
            binding.originDropdownCard.setVisibility(View.GONE);
            binding.destinationLabel.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.primary));
            binding.originLabel.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.muted));
        }
    }

    private void renderEndpoints() {
        if (map == null) return;
        if (aMarker != null) aMarker.remove();
        if (bMarker != null) bMarker.remove();
        aMarker = null; bMarker = null;
        try { aMarker = map.addMarker(new MarkerOptions().position(MapsSupport.latLng(Point.parse(model.origin()))).title(getString(R.string.origin))); }
        catch (IllegalArgumentException ignored) {}
        try { bMarker = map.addMarker(new MarkerOptions().position(MapsSupport.latLng(Point.parse(model.destination()))).title(getString(R.string.destination))); }
        catch (IllegalArgumentException ignored) {}
    }

    private void submit() {
        binding.originLayout.setError(null); binding.destinationLayout.setError(null);
        String aText = model.originText().trim(), bText = model.destinationText().trim();
        boolean valid = validateText(aText, true) & validateText(bText, false);
        if (!valid) return;
        androidx.core.view.WindowInsetsControllerCompat controller =
                androidx.core.view.ViewCompat.getWindowInsetsController(requireView());
        if (controller != null) controller.hide(androidx.core.view.WindowInsetsCompat.Type.ime());
        Point a = parse(model.origin()), b = parse(model.destination());
        if (a == null) { resolve(true, aText); return; }
        if (b == null) { resolve(false, bText); return; }
        if (a.sameAs(b)) { binding.destinationLayout.setError(getString(R.string.same_endpoints)); return; }
        model.compare();
        ((MainActivity) requireActivity()).openResults();
    }

    private Point parse(String value) {
        try { return Point.parse(value); } catch (IllegalArgumentException ignored) { return null; }
    }

    private boolean validateText(String value, boolean first) {
        int error = 0;
        if (value.isEmpty()) error = R.string.address_required;
        else if (value.length() > 250 && parse(first ? model.origin() : model.destination()) == null) error = R.string.address_too_long;
        else if (value.matches("[-+0-9.]+\\s*,\\s*[-+0-9.]+") && parse(value) == null)
            error = R.string.invalid_coordinate;
        if (error != 0) (first ? binding.originLayout : binding.destinationLayout).setError(getString(error));
        return error == 0;
    }

    private void syncFields() {
        updatingFields = true;
        binding.origin.setText(model.originText());
        binding.destination.setText(model.destinationText());
        updatingFields = false;
        renderEndpoints();
    }

    private void chooseEndpoint(boolean first, String label, Point point) {
        cancelSearch();
        model.selectEndpoint(first, label, point);
        syncFields();
        if (map != null) map.animateCamera(CameraUpdateFactory.newLatLngZoom(MapsSupport.latLng(point), 15f));
    }

    private void cancelSearch() {
        searchGeneration++;
        if (addressRequest != null) addressRequest.cancel();
        addressRequest = null;
        if (addressDialog != null) { addressDialog.dismiss(); addressDialog = null; }
        if (binding != null) {
            binding.addressProgress.setVisibility(View.GONE);
            binding.addressStatus.setVisibility(View.GONE);
            binding.compare.setEnabled(true);
        }
    }

    private void resolve(boolean first, String query) {
        cancelSearch();
        final int generation = searchGeneration;
        binding.addressProgress.setVisibility(View.VISIBLE);
        binding.addressStatus.setVisibility(View.VISIBLE);
        binding.addressStatus.setText(getString(R.string.address_searching, first ? "A" : "B"));
        binding.compare.setEnabled(false);
        AddressSearch search = ((com.comeforfood.saferoute.SafeRouteApp) requireActivity().getApplication()).getAddressSearch();
        addressRequest = search.search(query, new AddressSearch.Callback() {
            @Override public void success(java.util.List<AddressSearch.Place> places) {
                if (binding == null || generation != searchGeneration) return;
                binding.addressProgress.setVisibility(View.GONE);
                binding.addressStatus.setVisibility(View.GONE);
                binding.compare.setEnabled(true);
                if (places.isEmpty()) { showError(R.string.address_not_found); return; }
                String[] labels = new String[places.size()];
                for (int i = 0; i < places.size(); i++) labels[i] = places.get(i).label;
                addressDialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle(getString(R.string.address_choose, first ? "A" : "B"))
                        .setItems(labels, (dialog, which) -> {
                            if (binding == null || generation != searchGeneration) return;
                            AddressSearch.Place place = places.get(which);
                            String label = place.label.equalsIgnoreCase(query) ? place.label
                                    : getString(R.string.address_selected_label, query, place.label);
                            chooseEndpoint(first, label, place.point);
                            submit();
                        })
                        .setNegativeButton(R.string.address_cancel, (dialog, which) -> cancelSearch())
                        .create();
                addressDialog.show();
            }
            @Override public void failure(AddressSearch.Failure error) {
                if (binding == null || generation != searchGeneration) return;
                binding.addressProgress.setVisibility(View.GONE);
                binding.addressStatus.setVisibility(View.GONE);
                binding.compare.setEnabled(true);
                showError(error == AddressSearch.Failure.UNAVAILABLE ? R.string.address_unavailable : R.string.address_error);
            }
            private void showError(int message) {
                (first ? binding.originLayout : binding.destinationLayout).setError(getString(message));
            }
        });
    }

    @Override protected void onLocating(boolean loading) {
        if (binding != null) {
            binding.location.setEnabled(!loading);
            binding.destinationLocation.setEnabled(!loading);
            String text = getString(loading ? R.string.locating : R.string.my_location);
            binding.originLocationText.setText(text);
            binding.destinationLocationText.setText(text);
        }
    }

    @Override public void onSaveInstanceState(@NonNull Bundle out) { super.onSaveInstanceState(out); out.putString("picking", picking); }

    @Override public void onDestroyView() {
        cancelSearch();
        if (aMarker != null) aMarker.remove();
        if (bMarker != null) bMarker.remove();
        aMarker = null; bMarker = null; map = null; binding = null; super.onDestroyView();
    }
}
