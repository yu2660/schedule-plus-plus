package app.scheduleplus;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Small vector icon set, drawn locally with no font or bitmap dependencies. */
public final class Glyph extends Drawable {
    private final String kind;private final Paint p=new Paint(3);private final int color;
    public Glyph(String kind,int color){this.kind=kind;this.color=color;}
    @Override public void draw(Canvas canvas){int save=canvas.save();Rect b=getBounds();canvas.translate(b.left,b.top);canvas.scale(b.width()/24f,b.height()/24f);p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.7f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
        switch(kind){
            case "calendar":canvas.drawRoundRect(3,5,21,21,3,3,p);line(canvas,3,10,21,10);line(canvas,8,3,8,7);line(canvas,16,3,16,7);line(canvas,8,14,10,14);line(canvas,14,14,16,14);line(canvas,8,18,10,18);break;
            case "tasks":canvas.drawRoundRect(5,4,21,21,3,3,p);line(canvas,9,9,17,9);line(canvas,11,14,17,14);line(canvas,11,18,16,18);path(canvas,new float[]{2,13,5,16,10,11});break;
            case "settings":canvas.drawCircle(12,12,3,p);for(int i=0;i<8;i++){double a=i*Math.PI/4;line(canvas,12+(float)Math.cos(a)*7,12+(float)Math.sin(a)*7,12+(float)Math.cos(a)*9,12+(float)Math.sin(a)*9);}canvas.drawCircle(12,12,7,p);break;
            case "expand":path(canvas,new float[]{9,3,3,3,3,9});path(canvas,new float[]{15,3,21,3,21,9});path(canvas,new float[]{3,15,3,21,9,21});path(canvas,new float[]{15,21,21,21,21,15});break;
            case "place":canvas.drawCircle(12,9,2.5f,p);path(canvas,new float[]{12,22,5,12,4,8,6,3,12,1,18,3,20,8,19,12,12,22});break;
            case "clock":canvas.drawCircle(12,12,9,p);path(canvas,new float[]{12,6,12,12,16,14});break;
            case "person":canvas.drawCircle(12,7,4,p);canvas.drawArc(4,12,20,26,180,180,false,p);break;
            case "check":path(canvas,new float[]{5,12,10,17,20,6});break;
            default:canvas.drawCircle(12,12,8,p);break;
        }canvas.restoreToCount(save);
    }
    private void line(Canvas c,float a,float b,float d,float e){c.drawLine(a,b,d,e,p);}private void path(Canvas c,float[] xy){Path path=new Path();path.moveTo(xy[0],xy[1]);for(int i=2;i<xy.length;i+=2)path.lineTo(xy[i],xy[i+1]);c.drawPath(path,p);}
    @Override public void setAlpha(int a){p.setAlpha(a);invalidateSelf();}@Override public void setColorFilter(ColorFilter f){p.setColorFilter(f);invalidateSelf();}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
