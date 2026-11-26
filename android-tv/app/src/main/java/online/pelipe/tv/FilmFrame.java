package online.pelipe.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Marco de cine para el póster: tira de película con perforaciones y detalles (boleto, claqueta, palomitas y refresco). */
final class FilmFrame extends Drawable {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hole = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emoji = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int strip, holeW, holeH, step, radius;
    private final float emojiSize;

    FilmFrame(Context c) {
        strip = Ui.dp(c, 18);
        holeW = Ui.dp(c, 7);
        holeH = Ui.dp(c, 9);
        step = Ui.dp(c, 15);
        radius = Ui.dp(c, 7);
        emojiSize = Ui.dp(c, 30);
        fill.setColor(0xFF17171F);
        hole.setColor(0xFFECECF2);
        edge.setColor(0xFFFFB347);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(Ui.dp(c, 1));
        emoji.setTextSize(emojiSize);
        emoji.setTextAlign(Paint.Align.CENTER);
    }

    /** Margen que ocupa el marco alrededor del póster. */
    int inset() {
        return strip;
    }

    @Override
    public void draw(Canvas cv) {
        Rect b = getBounds();
        float w = b.width(), h = b.height();
        cv.drawRoundRect(new RectF(b), radius, radius, fill);

        // Perforaciones arriba, abajo, izquierda y derecha (se omiten en las esquinas, donde van los adornos)
        float margin = strip * 1.6f;
        for (float x = margin; x < w - margin - holeW; x += step) {
            float t = b.left + x, y1 = b.top + (strip - holeH) / 2f, y2 = b.bottom - (strip + holeH) / 2f;
            cv.drawRoundRect(new RectF(t, y1, t + holeW, y1 + holeH), 2, 2, hole);
            cv.drawRoundRect(new RectF(t, y2, t + holeW, y2 + holeH), 2, 2, hole);
        }
        for (float y = margin; y < h - margin - holeW; y += step) {
            float t = b.top + y, x1 = b.left + (strip - holeH) / 2f, x2 = b.right - (strip + holeH) / 2f;
            cv.drawRoundRect(new RectF(x1, t, x1 + holeH, t + holeW), 2, 2, hole);
            cv.drawRoundRect(new RectF(x2, t, x2 + holeH, t + holeW), 2, 2, hole);
        }
        // Filete dorado alrededor del póster
        cv.drawRect(b.left + strip - 1, b.top + strip - 1, b.right - strip + 1, b.bottom - strip + 1, edge);

        // Adornos de cine en las esquinas
        sticker(cv, "🎟️", b.left + strip * 0.7f, b.top + strip * 0.7f, -14);
        sticker(cv, "🎬", b.right - strip * 0.7f, b.top + strip * 0.7f, 12);
        sticker(cv, "🍿", b.left + strip * 0.7f, b.bottom - strip * 0.7f, 10);
        sticker(cv, "🥤", b.right - strip * 0.7f, b.bottom - strip * 0.7f, -10);
    }

    private void sticker(Canvas cv, String s, float cx, float cy, float deg) {
        Paint.FontMetrics fm = emoji.getFontMetrics();
        cv.save();
        cv.rotate(deg, cx, cy);
        cv.drawText(s, cx, cy - (fm.ascent + fm.descent) / 2f, emoji);
        cv.restore();
    }

    @Override
    public void setAlpha(int a) {
        fill.setAlpha(a);
    }

    @Override
    public void setColorFilter(ColorFilter f) {
        fill.setColorFilter(f);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
