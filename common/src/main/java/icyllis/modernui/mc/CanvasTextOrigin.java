package icyllis.modernui.mc;

import icyllis.arc3d.sketch.Matrix;

/** Keeps Canvas glyph baking and the text-blob cache independent of scrolling. */
public final class CanvasTextOrigin {
    private CanvasTextOrigin() {
    }

    public static Matrix forRasterization(Matrix positionMatrix) {
        // Granite still draws using the original Canvas matrix and run origin.
        // Its AtlasSubRun.getMatrixAndFilter applies the omitted translation
        // after glyph snapping, choosing bilinear sampling at fractional pixels.
        // Perspective size estimation is position-dependent; leave it untouched.
        if (!positionMatrix.hasPerspective()) {
            positionMatrix.setTranslateX(0);
            positionMatrix.setTranslateY(0);
        }
        return positionMatrix;
    }
}
