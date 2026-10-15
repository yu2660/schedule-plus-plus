package app.scheduleplus;

import android.content.Context;
import android.graphics.*;
import android.view.*;

@android.annotation.SuppressLint("ViewConstructor") // Programmatic photo editor, never inflated from XML.
public final class CropView extends View {
    private Bitmap image;private final Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG);private final ScaleGestureDetector detector;
    private float scale=1,minScale=1,x,y,lastX,lastY;private final RectF frame=new RectF(),drawing=new RectF();
    public CropView(Context c,Bitmap image){super(c);this.image=image;setContentDescription("背景图片裁剪；拖动调整位置，双指调整大小");detector=new ScaleGestureDetector(c,new ScaleGestureDetector.SimpleOnScaleGestureListener(){@Override public boolean onScale(ScaleGestureDetector d){float next=Math.max(minScale,Math.min(minScale*5,scale*d.getScaleFactor()));float ratio=next/scale;x=d.getFocusX()-(d.getFocusX()-x)*ratio;y=d.getFocusY()-(d.getFocusY()-y)*ratio;scale=next;clamp();invalidate();return true;}});}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);float aspect=getResources().getDisplayMetrics().widthPixels/(float)getResources().getDisplayMetrics().heightPixels;float fw=Math.min(w,h*aspect),fh=fw/aspect;frame.set((w-fw)/2,(h-fh)/2,(w+fw)/2,(h+fh)/2);minScale=Math.max(fw/image.getWidth(),fh/image.getHeight());scale=minScale;x=frame.centerX()-image.getWidth()*scale/2;y=frame.centerY()-image.getHeight()*scale/2;}
    private void clamp(){x=Math.min(frame.left,Math.max(frame.right-image.getWidth()*scale,x));y=Math.min(frame.top,Math.max(frame.bottom-image.getHeight()*scale,y));}
    @Override protected void onDraw(Canvas c){super.onDraw(c);paint.setColor(Color.BLACK);c.drawColor(Color.BLACK);int saved=c.save();c.clipRect(frame);drawing.set(x,y,x+image.getWidth()*scale,y+image.getHeight()*scale);c.drawBitmap(image,null,drawing,paint);c.restoreToCount(saved);}
    @Override public boolean onTouchEvent(MotionEvent e){detector.onTouchEvent(e);switch(e.getActionMasked()){case MotionEvent.ACTION_DOWN:lastX=e.getX();lastY=e.getY();return true;case MotionEvent.ACTION_POINTER_UP:int i=e.getActionIndex()==0?1:0;lastX=e.getX(i);lastY=e.getY(i);return true;case MotionEvent.ACTION_MOVE:if(!detector.isInProgress()){x+=e.getX()-lastX;y+=e.getY()-lastY;clamp();invalidate();}lastX=e.getX();lastY=e.getY();return true;case MotionEvent.ACTION_UP:performClick();return true;default:return true;}}
    @Override public boolean performClick(){super.performClick();return true;}
    public void rotate(){Matrix m=new Matrix();m.setRotate(90);image=Bitmap.createBitmap(image,0,0,image.getWidth(),image.getHeight(),m,true);onSizeChanged(getWidth(),getHeight(),getWidth(),getHeight());invalidate();}
    public Bitmap cropped(){int width=Math.max(1,(int)Math.min(1080,Math.min(frame.width(),2400*frame.width()/Math.max(1,frame.height())))),height=Math.max(1,Math.round(frame.height()*width/Math.max(1,frame.width())));Bitmap result=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(result);c.scale(width/Math.max(1,frame.width()),height/Math.max(1,frame.height()));c.translate(-frame.left,-frame.top);drawing.set(x,y,x+image.getWidth()*scale,y+image.getHeight()*scale);c.drawBitmap(image,null,drawing,paint);return result;}
}
