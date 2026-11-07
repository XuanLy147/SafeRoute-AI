package com.comeforfood.saferoute.feature.report;

import android.net.Uri;
import android.os.Bundle;
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
import androidx.navigation.Navigation;
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
public class ReportWizardFragment extends Fragment {

    private FragmentReportWizardBinding binding;
    private Uri selectedImageUri;
    private List<DetectionDto> currentDetections = new ArrayList<>();
    private double currentLat = 10.7542;
    private double currentLng = 106.6631;

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
        binding.btnBack.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());
        binding.btnOpenReports.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.action_report_to_myReports));
        binding.btnOpenProfile.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.action_report_to_profile));
        binding.btnOpenReview.setVisibility(
                new SessionManager(requireContext()).isReviewerOrAdmin() ? View.VISIBLE : View.GONE);
        binding.btnOpenReview.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.action_report_to_reviewer));

        // Select photo
        binding.layoutPlaceholder.setOnClickListener(v -> selectImageLauncher.launch("image/*"));
        binding.btnPickGallery.setOnClickListener(v -> selectImageLauncher.launch("image/*"));

        // Local preview only; the AI API contract is not available yet.
        binding.btnSimulateAi.setOnClickListener(v -> runAiAnalysis());

        // Adjust Pin trigger
        binding.btnAdjustPin.setOnClickListener(v -> {
            Toast.makeText(requireContext(), "Nhấn trên bản đồ để di chuyển ghim tọa độ", Toast.LENGTH_SHORT).show();
        });

        // Submit report
        binding.btnSubmitReport.setOnClickListener(v -> submitReport());
    }

    private void onImageSelected(Uri uri) {
        selectedImageUri = uri;
        binding.layoutPlaceholder.setVisibility(View.GONE);
        binding.imgPreview.setVisibility(View.VISIBLE);
        binding.imgPreview.setImageURI(uri);

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

        // Sample result for UI testing only, not a live model prediction.
        currentDetections.clear();
        DetectionDto potHole = new DetectionDto("D40", 0.87f, Arrays.asList(0.25f, 0.35f, 0.75f, 0.80f));
        currentDetections.add(potHole);

        // Draw Bounding Box Overlay
        binding.bboxOverlay.setDetections(currentDetections);

        // Update UI Summary
        binding.tvAiTitle.setText("Gợi ý mô phỏng (chưa kết nối AI)");
        binding.tvAiDetectionSummary.setText("Dữ liệu mẫu: D40 · Ổ gà (Độ tin cậy: 87%)");
        binding.chipD40.setChecked(true);
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
