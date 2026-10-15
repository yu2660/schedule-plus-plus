package app.scheduleplus;

import android.content.Context;
import android.graphics.*;
import android.text.TextUtils;
import android.text.TextPaint;
import android.view.*;
import java.time.*;
import java.util.List;
import static app.scheduleplus.Models.*;

/** A complete fit-to-window timetable. Zoom only changes viewing, never stored geometry. */
@android.annotation.SuppressLint("ViewConstructor") // Programmatic-only view requires its timetable owner.
public final class OverviewView extends View {
    public interface Selection { void select(int day,int section); }
    private final MainActivity owner;
    private final Selection selection;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text=new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final ScaleGestureDetector pinch;
    private float zoom=1,panX,panY,lastX,lastY,downX,downY;
    private boolean moved;
    private int selectedDay,selectedSection;
    public OverviewView(MainActivity owner,Selection selection){
        super(owner);this.owner=owner;this.selection=selection;setClickable(true);setFocusable(true);
        pinch=new ScaleGestureDetector(owner,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScale(ScaleGestureDetector d){float next=Math.max(1,Math.min(4,zoom*d.getScaleFactor()));float ratio=next/zoom;panX=d.getFocusX()-(d.getFocusX()-panX)*ratio;panY=d.getFocusY()-(d.getFocusY()-panY)*ratio;zoom=next;clamp();moved=true;invalidate();return true;}
        });updateDescription();
    }
    void updateDescription(){setContentDescription("完整课表，第 "+owner.week+" 周，"+(owner.state.display.weekends?7:5)+" 天，"+owner.state.slots.size()+" 节；双指缩放，点击课程查看详情");}
    public void fit(){zoom=1;panX=panY=0;updateDescription();invalidate();}
    public float zoom(){return zoom;}
    private void clamp(){panX=Math.min(0,Math.max(getWidth()*(1-zoom),panX));panY=Math.min(0,Math.max(getHeight()*(1-zoom),panY));}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);fit();}
    private float axis(){return Math.min(owner.dp(46),getWidth()*.14f);}
    private float header(){return Math.min(owner.dp(42),getHeight()*.09f);}
    private void rect(Canvas c,float l,float t,float r,float b,int color){paint.setColor(color);paint.setStyle(Paint.Style.FILL);c.drawRect(l,t,r,b,paint);}
    private void lines(Canvas c,String value,float l,float t,float r,float b,int color,boolean bold){
        if(r-l<2 || b-t<2)return;int save=c.save();c.clipRect(l+1,t+1,r-1,b-1);
        String[] parts=value.split("\n");float size=Math.min(owner.dp(bold?12:10),(b-t-2)/Math.max(1,parts.length)*.74f);text.setTextSize(Math.max(2,size));text.setColor(color);text.setTypeface(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT);
        float step=Math.min((b-t)/Math.max(1,parts.length),text.getTextSize()*1.3f),y=t+(b-t-step*parts.length)/2+(step-text.ascent()-text.descent())/2;
        for(String line:parts){String s=TextUtils.ellipsize(line,text,Math.max(1,r-l-4),TextUtils.TruncateAt.END).toString();c.drawText(s,l+(r-l-text.measureText(s))/2,y,text);y+=step;}c.restoreToCount(save);
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);State s=owner.state;if(s==null || getWidth()==0 || getHeight()==0)return;
        int save=c.save();c.translate(panX,panY);c.scale(zoom,zoom);rect(c,0,0,getWidth(),getHeight(),owner.appearance.surface);
        int days=s.display.weekends?7:5;float a=axis(),h=header(),w=(getWidth()-a)/days,row=(getHeight()-h)/s.slots.size();
        lines(c,"节次",0,0,a,h,owner.MUTED,false);LocalDate monday=LocalDate.parse(s.monday).plusWeeks(owner.week-1);LocalDate today=LocalDate.now();LocalTime now=LocalTime.now();
        for(int i=0;i<s.slots.size();i++){Slot slot=s.slots.get(i);lines(c,(i+1)+(s.display.time?"\n"+slot.start+"\n"+slot.end:""),0,h+i*row,a,h+(i+1)*row,owner.MUTED,false);}
        for(int day=1;day<=days;day++){
            float l=a+(day-1)*w;boolean current=monday.plusDays(day-1).equals(today);
            lines(c,MainActivity.DAYS[day-1]+"\n"+monday.plusDays(day-1).getDayOfMonth(),l,0,l+w,h,current?owner.ACCENT:owner.MUTED,true);
            for(CourseBlock block:courseBlocks(s,day,owner.week)){
                float top=h+(block.first-1)*row,bottom=h+block.last*row;Course course=block.courses.isEmpty()?null:block.courses.get(0);
                rect(c,l+1,top+1,l+w-1,bottom-1,course==null?(current?owner.appearance.soft:owner.appearance.surface):owner.appearance.options.style==0?owner.PASTELS[course.color]:owner.appearance.surface);
                if(course!=null){if(owner.appearance.options.style==1)rect(c,l+1,top+1,l+4,bottom-1,owner.COLORS[course.color]);if(owner.appearance.options.style==3){paint.setColor(owner.COLORS[course.color]);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1);c.drawRect(l+1,top+1,l+w-1,bottom-1,paint);paint.setStyle(Paint.Style.FILL);}String value=course.name+(s.display.location && !course.location.isEmpty()?"\n"+course.location:"")+(s.display.teacher && !course.teacher.isEmpty()?"\n"+course.teacher:"")+(s.display.time?"\n"+s.slots.get(block.first-1).start+"–"+s.slots.get(block.last-1).end:"")+(block.courses.size()>1?"\n重叠 "+block.courses.size()+" 门":"");lines(c,value,l+2,top+2,l+w-2,bottom-2,owner.COLORS[course.color],true);
                    if(current && !now.isBefore(LocalTime.parse(s.slots.get(block.first-1).start)) && now.isBefore(LocalTime.parse(s.slots.get(block.last-1).end))){paint.setColor(owner.ACCENT);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(owner.dp(2));c.drawRect(l+2,top+2,l+w-2,bottom-2,paint);paint.setStyle(Paint.Style.FILL);}
                }
            }
        }c.restoreToCount(save);
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        pinch.onTouchEvent(e);int action=e.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){lastX=downX=e.getX();lastY=downY=e.getY();moved=false;return true;}
        if(action==MotionEvent.ACTION_POINTER_DOWN)moved=true;
        if(action==MotionEvent.ACTION_POINTER_UP){int remaining=e.getActionIndex()==0?1:0;lastX=e.getX(remaining);lastY=e.getY(remaining);moved=true;return true;}
        if(action==MotionEvent.ACTION_MOVE){if(Math.hypot(e.getX()-downX,e.getY()-downY)>ViewConfiguration.get(getContext()).getScaledTouchSlop())moved=true;if(!pinch.isInProgress() && zoom>1){panX+=e.getX()-lastX;panY+=e.getY()-lastY;clamp();invalidate();}lastX=e.getX();lastY=e.getY();return true;}
        if(action==MotionEvent.ACTION_UP && !moved){float x=(e.getX()-panX)/zoom,y=(e.getY()-panY)/zoom;int days=owner.state.display.weekends?7:5;selectedDay=(int)((x-axis())/((getWidth()-axis())/days))+1;selectedSection=(int)((y-header())/((getHeight()-header())/owner.state.slots.size()))+1;if(x>=axis() && y>=header() && selectedDay<=days && selectedSection<=owner.state.slots.size())performClick();return true;}
        return true;
    }
    @Override public boolean performClick(){super.performClick();if(selectedDay>=1 && selectedSection>=1)selection.select(selectedDay,selectedSection);return true;}
}
