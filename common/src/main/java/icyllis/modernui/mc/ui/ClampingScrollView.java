/*
 * Modern UI.
 * Copyright (C) 2025 BloCamLimb. All rights reserved.
 *
 * Modern UI is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Modern UI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Modern UI. If not, see <https://www.gnu.org/licenses/>.
 */

package icyllis.modernui.mc.ui;

import icyllis.modernui.core.Context;
import icyllis.modernui.resources.ResourceId;
import icyllis.modernui.util.AttributeSet;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.ScrollView;
import org.jetbrains.annotations.ApiStatus;

/**
 * Constrain the max width of the direct child, which should use MATCH_PARENT layout_width.
 */
@ApiStatus.Internal
public class ClampingScrollView extends ScrollView {

    private int mChildMaxWidth;

    public ClampingScrollView(Context context) {
        super(context);
    }

    public ClampingScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ClampingScrollView(Context context, AttributeSet attrs, ResourceId defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ClampingScrollView(Context context, AttributeSet attrs, ResourceId defStyleAttr, ResourceId defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Deprecated
    public void setMaxWidth(int maxWidth) {
        setChildMaxWidth(maxWidth);
    }

    public void setChildMaxWidth(int childMaxWidth) {
        if (mChildMaxWidth != childMaxWidth) {
            mChildMaxWidth = childMaxWidth;
            requestLayout();
        }
    }

    public int getChildMaxWidth() {
        return mChildMaxWidth;
    }

    // Clamp before ScrollView measures its content height. Remeasuring a narrower
    // child with its old exact height can squeeze the last rows of a long page.
    @Override
    protected void measureChild(View child, int parentWidthMeasureSpec, int parentHeightMeasureSpec) {
        super.measureChild(child, clampWidthSpec(parentWidthMeasureSpec,
                getPaddingLeft() + getPaddingRight()), parentHeightMeasureSpec);
    }

    @Override
    protected void measureChildWithMargins(View child, int parentWidthMeasureSpec, int widthUsed,
                                          int parentHeightMeasureSpec, int heightUsed) {
        var params = (MarginLayoutParams) child.getLayoutParams();
        int widthPadding = getPaddingLeft() + getPaddingRight() +
                params.leftMargin + params.rightMargin + widthUsed;
        super.measureChildWithMargins(child, clampWidthSpec(parentWidthMeasureSpec, widthPadding),
                widthUsed, parentHeightMeasureSpec, heightUsed);
    }

    private int clampWidthSpec(int widthMeasureSpec, int widthPadding) {
        if (mChildMaxWidth <= 0) return widthMeasureSpec;
        int mode = MeasureSpec.getMode(widthMeasureSpec);
        int width = mChildMaxWidth + widthPadding;
        if (mode != MeasureSpec.UNSPECIFIED) {
            width = Math.min(width, MeasureSpec.getSize(widthMeasureSpec));
        }
        return MeasureSpec.makeMeasureSpec(width,
                mode == MeasureSpec.UNSPECIFIED ? MeasureSpec.AT_MOST : mode);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        if (getChildCount() > 0 && mChildMaxWidth > 0) {
            // ScrollView's fillViewport pass may expand the width again. Its
            // height is safe to retain because the natural height above was
            // already measured at the clamped width.
            final View child = getChildAt(0);

            int childWidth = child.getMeasuredWidth();
            int clampedWidth = Math.min(childWidth, mChildMaxWidth);

            if (childWidth != clampedWidth) {
                var newWidthSpec = MeasureSpec.makeMeasureSpec(clampedWidth, MeasureSpec.EXACTLY);
                var newHeightSpec = MeasureSpec.makeMeasureSpec(child.getMeasuredHeight(), MeasureSpec.EXACTLY);
                child.measure(newWidthSpec, newHeightSpec);
            }
        }
    }
}
