package com.comeforfood.saferoute.feature.report;

import android.net.Uri;
import android.os.Bundle;
import com.comeforfood.saferoute.R;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.comeforfood.saferoute.feature.shared.Tv2Navigation;
import com.comeforfood.saferoute.core.model.DetectionDto;
import com.comeforfood.saferoute.core.session.SessionManager;
import com.comeforfood.saferoute.databinding.FragmentReportWizardBinding;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * ReportWizardFragment (Owned by TV2)
 * Core P0 flow: Image Pick/Capture -> GPS Confirm -> YOLO AI Preview -> User Label Adjustment -> Submit PENDING
 */
public class ReportWizardFragment extends com.comeforfood.saferoute.feature.shared.LocationAwareFragment {

    private FragmentReportWizardBinding binding;
    private Uri selectedImageUri;
    private List<DetectionDto> currentDetections = new ArrayList<>();
    private com.comeforfood.saferoute.feature.shared.Point selectedPoint;

    // Photo picker contract
    private final ActivityResultLauncher<String> selectImageLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    onImageSelected(uri);
                }
            }
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentReportWizardBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Back button
        binding.btnBack.setOnClickListener(v -> Tv2Navigation.back(this));
        binding.btnOpenReports.setOnClickListener(v ->
                Tv2Navigation.navigate(this, R.id.action_report_to_myReports));
        binding.btnOpenProfile.setOnClickListener(v ->
                Tv2Navigation.navigate(this, R.id.action_report_to_profile));
        binding.btnOpenReview.setVisibility(
                new SessionManager(requireContext()).isReviewerOrAdmin() ? View.VISIBLE : View.GONE);
        binding.btnOpenReview.setOnClickListener(v ->
                Tv2Navigation.navigate(this, R.id.action_report_to_reviewer));

        // Select photo
        binding.layoutPlaceholder.setOnClickListener(v -> selectImageLauncher.launch("image/*"));
        binding.btnPickGallery.setOnClickListener(v -> selectImageLauncher.launch("image/*"));

        // Local preview only; the AI API contract is not available yet.
        binding.btnSimulateAi.setOnClickListener(v -> runAiAnalysis());

        binding.btnAdjustPin.setOnClickListener(v -> useLocation(location -> {
            selectedPoint = new com.comeforfood.saferoute.feature.shared.Point(location.getLatitude(), location.getLongitude());
            binding.tvCoordinates.setText(selectedPoint.format());
        }));
        if (savedInstanceState != null) {
            String image = savedInstanceState.getString("report.image");
            if (image != null) onImageSelected(Uri.parse(image));
            if (savedInstanceState.containsKey("report.latitude")) {
                selectedPoint = new com.comeforfood.saferoute.feature.shared.Point(savedInstanceState.getDouble("report.latitude"), savedInstanceState.getDouble("report.longitude"));
                binding.tvCoordinates.setText(selectedPoint.format());
            }
        }
        binding.btnBack.setVisibility(View.GONE);
        binding.btnOpenProfile.setVisibility(View.GONE);

        // Submit report
        binding.btnSubmitReport.setOnClickListener(v -> submitReport());
    }

    private void onImageSelected(Uri uri) {
        selectedImageUri = uri;
        if (binding == null) return;
        binding.layoutPlaceholder.setVisibility(View.GONE);
        binding.imgPreview.setVisibility(View.VISIBLE);
        try { binding.imgPreview.setImageURI(uri); }
        catch (RuntimeException error) {
            selectedImageUri = null;
            binding.layoutPlaceholder.setVisibility(View.VISIBLE);
            binding.imgPreview.setVisibility(View.GONE);
            Toast.makeText(requireContext(), R.string.image_read_error, Toast.LENGTH_LONG).show();
            return;
        }

        // Run AI detection on newly selected image
        runAiAnalysis();
    }

    /**
     * Executes AI detection or Responsible AI manual fallback
     */
    private void runAiAnalysis() {
        if (selectedImageUri == null) {
            Toast.makeText(requireContext(), "Vui lòng chọn ảnh trước khi phân tích AI", Toast.LENGTH_SHORT).show();
            return;
        }

        currentDetections.clear();
        binding.bboxOverlay.clear();
        binding.tvAiTitle.setText(R.string.ai_unavailable_title);
        binding.tvAiDetectionSummary.setText(R.string.ai_unavailable_detail);
        binding.cardAiResult.setVisibility(View.VISIBLE);
    }

    /**
     * Fallback mechanism when AI returns empty detections: detections = []
     */
    public void triggerManualFallback() {
        currentDetections.clear();
        binding.bboxOverlay.clear();
        binding.tvAiTitle.setText("AI chưa phát hiện rõ");
        binding.tvAiDetectionSummary.setText("Không nhận diện được hư hỏng rõ ràng. Bạn có thể chọn loại nguy cơ thủ công bên dưới.");
        binding.tvAiDisclaimer.setText("⚠️ Không phát hiện hư hỏng không đồng nghĩa với việc mặt đường an toàn.");
        binding.cardAiResult.setVisibility(View.VISIBLE);
    }

    private void submitReport() {
        if (selectedImageUri == null) {
            Toast.makeText(requireContext(), "Vui lòng chụp hoặc chọn ảnh mặt đường", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(
                requireContext(),
                "Chưa gửi báo cáo: API tạo báo cáo chưa được tích hợp.",
                Toast.LENGTH_LONG
        ).show();
    }

    @Override public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        if (selectedImageUri != null) out.putString("report.image", selectedImageUri.toString());
        if (selectedPoint != null) { out.putDouble("report.latitude", selectedPoint.latitude); out.putDouble("report.longitude", selectedPoint.longitude); }
    }
    @Override protected void onLocating(boolean loading) {
        if (binding != null) { binding.btnAdjustPin.setEnabled(!loading); binding.btnAdjustPin.setText(loading ? R.string.locating : R.string.report_location_button); }
    }
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
