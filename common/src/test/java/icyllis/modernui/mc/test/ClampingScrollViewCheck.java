package icyllis.modernui.mc.test;

import icyllis.modernui.ModernUI;
import icyllis.modernui.mc.ui.ClampingScrollView;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.ScrollView;

/** Measures width-dependent content without opening a window or creating a GPU context. */
public final class ClampingScrollViewCheck {
    public static void main(String[] args) {
        var context = new ModernUI();
        int checks = 0;
        for (int width : new int[]{600, 1600}) {
            for (int naturalHeight : new int[]{100, 400}) {
                for (boolean fillViewport : new boolean[]{false, true}) {
                    for (int heightMode : new int[]{MeasureSpec.EXACTLY, MeasureSpec.AT_MOST}) {
                        var scrollView = new ClampingScrollView(context);
                        scrollView.setChildMaxWidth(800);
                        scrollView.setFillViewport(fillViewport);
                        var child = new ResponsiveView(context, naturalHeight);
                        scrollView.addView(child, new ScrollView.LayoutParams(-1, -2));
                        scrollView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                                MeasureSpec.makeMeasureSpec(300, heightMode));
                        int viewportHeight = heightMode == MeasureSpec.EXACTLY ? 300 :
                                Math.min(naturalHeight, 300);
                        int contentHeight = fillViewport ? Math.max(naturalHeight, viewportHeight) : naturalHeight;
                        if (child.getMeasuredWidth() != Math.min(width, 800) ||
                                child.getMeasuredHeight() != contentHeight ||
                                scrollView.getMeasuredWidth() != width ||
                                scrollView.getMeasuredHeight() != viewportHeight || child.measureCount > 3) {
                            throw new AssertionError("width=" + width + ", naturalHeight=" + naturalHeight +
                                    ", fillViewport=" + fillViewport + ", heightMode=" + heightMode +
                                    ", content=" + child.getMeasuredWidth() + "x" + child.getMeasuredHeight() +
                                    ", viewport=" + scrollView.getMeasuredWidth() + "x" + scrollView.getMeasuredHeight() +
                                    ", measureCount=" + child.measureCount);
                        }
                        checks++;
                    }
                }
            }
        }
        System.out.println("ClampingScrollView measurement checks passed: " + checks);
    }

    private static final class ResponsiveView extends View {
        private final int narrowHeight;
        private int measureCount;

        ResponsiveView(ModernUI context, int narrowHeight) {
            super(context);
            this.narrowHeight = narrowHeight;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            measureCount++;
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int desiredHeight = width > 800 ? narrowHeight / 2 : narrowHeight;
            setMeasuredDimension(width, resolveSizeAndState(desiredHeight, heightMeasureSpec, 0));
        }
    }
}
