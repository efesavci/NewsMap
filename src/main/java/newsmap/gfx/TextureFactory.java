package newsmap.gfx;

import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

public class TextureFactory {

    public Image makeRingTexture(int size, Color color) {
        WritableImage img = new WritableImage(size, size);
        PixelWriter pw = img.getPixelWriter();

        double cx = (size - 1) / 2.0;
        double cy = (size - 1) / 2.0;
        double maxR = Math.min(cx, cy);

        double inner = 0.75 * maxR;
        double outer = 0.95 * maxR;
        double thickness = outer - inner;
        double feather = 0.6 * thickness;

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double dx = x - cx, dy = y - cy;
                double r = Math.hypot(dx, dy);

                double centerline = inner + thickness / 2.0;
                double dist = Math.abs(r - centerline);

                double a = 1.0 - clamp((dist - (thickness/2.0 - feather)) / feather, 0.0, 1.0);
                double fadeOuter = 1.0 - clamp((r - outer + feather) / feather, 0.0, 1.0);
                double fadeInner = 1.0 - clamp((inner - r + feather) / feather, 0.0, 1.0);
                double alpha = a * fadeOuter * fadeInner;

                if (alpha <= 0) {
                    pw.setColor(x, y, Color.TRANSPARENT);
                } else {
                    pw.setColor(x, y, new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
                }
            }
        }
        return img;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}

