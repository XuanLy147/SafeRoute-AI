package com.comeforfood.saferoute.feature.map;

import android.content.Context;
import android.util.AttributeSet;
import androidx.core.widget.NestedScrollView;

/** Keep long report lists from covering the map controls. */
public class HazardListScrollView extends NestedScrollView {
    public HazardListScrollView(Context context, AttributeSet attrs) { super(context, attrs); }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int limit = Math.round(getResources().getDisplayMetrics().heightPixels * 0.30f);
        if (MeasureSpec.getMode(heightSpec) != MeasureSpec.UNSPECIFIED)
            limit = Math.min(limit, MeasureSpec.getSize(heightSpec));
        super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST));
    }
}
