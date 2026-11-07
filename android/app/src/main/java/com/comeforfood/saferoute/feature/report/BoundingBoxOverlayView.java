package com.comeforfood.saferoute.feature.report;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import com.comeforfood.saferoute.core.model.DetectionDto;
import java.util.ArrayList;
import java.util.List;

/**
 * Custom View that overlays YOLO Bounding Boxes over road hazard images (Owned by TV2)
 */
public class BoundingBoxOverlayView extends View {

    private final List<DetectionDto> detections = new ArrayList<>();
    private Paint boxPaint;
    private Paint textBgPaint;
    private Paint textPaint;

    public BoundingBoxOverlayView(Context context) {
        super(context);
        init();
    }

    public BoundingBoxOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BoundingBoxOverlayView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Red Bounding Box stroke
        boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boxPaint.setColor(Color.parseColor("#EF4444"));
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(5f);

        // Badge background
        textBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textBgPaint.setColor(Color.parseColor("#EF4444"));
        textBgPaint.setStyle(Paint.Style.FILL);

        // Badge text
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(32f);
        textPaint.setFakeBoldText(true);
    }

    public void setDetections(List<DetectionDto> newDetections) {
        this.detections.clear();
        if (newDetections != null) {
            this.detections.addAll(newDetections);
        }
        invalidate(); // Redraw canvas
    }

    public void clear() {
        this.detections.clear();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (detections.isEmpty()) return;

        int viewWidth = getWidth();
        int viewHeight = getHeight();

        for (DetectionDto det : detections) {
            List<Float> bbox = det.getBbox();
            if (bbox == null || bbox.size() < 4) continue;

            // Map normalized coordinates [0.0 - 1.0] to pixel coordinates
            float left = bbox.get(0) * viewWidth;
            float top = bbox.get(1) * viewHeight;
            float right = bbox.get(2) * viewWidth;
            float bottom = bbox.get(3) * viewHeight;

            RectF rect = new RectF(left, top, right, bottom);
            canvas.drawRoundRect(rect, 8f, 8f, boxPaint);

            // Text Label: e.g. "D40 · Ổ gà (87%)"
            String label = det.getDisplayName() + " (" + Math.round(det.getConfidence() * 100) + "%)";
            float textWidth = textPaint.measureText(label);
            float textHeight = textPaint.getTextSize();

            float tagLeft = left;
            float tagTop = Math.max(0, top - textHeight - 16f);
            float tagRight = tagLeft + textWidth + 24f;
            float tagBottom = tagTop + textHeight + 16f;

            RectF tagRect = new RectF(tagLeft, tagTop, tagRight, tagBottom);
            canvas.drawRoundRect(tagRect, 6f, 6f, textBgPaint);
            canvas.drawText(label, tagLeft + 12f, tagBottom - 10f, textPaint);
        }
    }
}

