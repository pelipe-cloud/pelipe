package online.pelipe.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** La "P" del logo de Pelipe (con su triángulo de play), dibujada en vector. */
final class LogoP extends View {
    private final Path p = new Path();
    private final Path tri = new Path();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);

    LogoP(Context c) {
        super(c);
        p.setFillType(Path.FillType.EVEN_ODD);
        p.moveTo(168, 112);
        p.lineTo(292, 112);
        p.arcTo(new RectF(180, 112, 404, 336), -90, 180);
        p.lineTo(240, 336);
        p.lineTo(240, 400);
        p.lineTo(168, 400);
        p.close();
        p.moveTo(240, 176);
        p.lineTo(240, 272);
        p.lineTo(292, 272);
        p.arcTo(new RectF(244, 176, 340, 272), 90, -180);
        p.close();
        tri.moveTo(258, 197);
        tri.lineTo(258, 251);
        tri.lineTo(302, 224);
        tri.close();
    }

    @Override
    protected void onDraw(Canvas cv) {
        float s = getWidth() / 236f;
        cv.save();
        cv.scale(s, s);
        cv.translate(-168, -112);
        // sombra suave para que se lea sobre cualquier imagen
        cv.save();
        cv.translate(0, 6);
        fill.setColor(0x99000000);
        cv.drawPath(p, fill);
        cv.restore();
        fill.setColor(0xFFFF5A36);
        cv.drawPath(p, fill);
        fill.setColor(0xFFFFB347);
        cv.drawPath(tri, fill);
        cv.restore();
    }
}
