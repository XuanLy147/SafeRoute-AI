package com.comeforfood.saferoute.feature.shared;

import android.Manifest;
import android.content.Intent;
import android.location.Location;
import android.net.Uri;
import android.provider.Settings;
import android.view.View;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;
import com.comeforfood.saferoute.R;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.material.snackbar.Snackbar;
import java.util.function.Consumer;

public abstract class LocationAwareFragment extends Fragment {
    private CancellationTokenSource locationRequest;
    private Consumer<Location> pendingResult;
    private final ActivityResultLauncher<String[]> permission = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (getView() == null || pendingResult == null) return;
                if (ForegroundLocation.permitted(requireContext())) locate(pendingResult);
                else {
                    onLocating(false);
                    Snackbar.make(requireView(), R.string.location_denied, Snackbar.LENGTH_LONG)
                            .setAction(R.string.location_settings, view -> startActivity(new Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:" + requireContext().getPackageName())))).show();
                    pendingResult = null;
                }
            });
    protected void useLocation(Consumer<Location> result) {
        pendingResult = result;
        if (ForegroundLocation.permitted(requireContext())) locate(result);
        else permission.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
    }
    private void locate(Consumer<Location> result) {
        if (locationRequest != null) locationRequest.cancel();
        View ownerView = requireView();
        onLocating(true);
        locationRequest = ForegroundLocation.request(requireContext(), location -> {
            if (getView() != ownerView) return;
            onLocating(false);
            pendingResult = null;
            if (location == null) {
                Snackbar.make(ownerView, R.string.location_unavailable, Snackbar.LENGTH_LONG).show();
                return;
            }
            result.accept(location);
            Snackbar.make(ownerView, getString(R.string.location_accuracy, (double) location.getAccuracy()), Snackbar.LENGTH_LONG).show();
        });
    }
    protected void onLocating(boolean loading) {}
    @Override public void onDestroyView() {
        if (locationRequest != null) locationRequest.cancel();
        locationRequest = null; pendingResult = null;
        super.onDestroyView();
    }
}
