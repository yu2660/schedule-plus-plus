package app.scheduleplus;

import android.content.Context;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;

/** Coordinates the timetable's native vertical and horizontal scrollers before interception.
 * Native scroll views still handle scrolling, flinging, accessibility and pointer cleanup.
 */
public final class DirectionLockedScrollView extends ScrollView {
    private final AxisLock lock;
    private final Rect hit=new Rect();
    private HorizontalScrollView horizontal;
    private boolean tracking;
    public DirectionLockedScrollView(Context context){super(context);lock=new AxisLock(ViewConfiguration.get(context).getScaledTouchSlop());}
    public HorizontalScrollView createHorizontalScroller(){horizontal=new Horizontal(getContext());return horizontal;}
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            tracking=horizontal!=null && horizontal.getGlobalVisibleRect(hit) && hit.contains((int)event.getRawX(),(int)event.getRawY());
            lock.begin(event.getRawX(),event.getRawY());
            if(tracking)fling(0);
        }else if(tracking && action==MotionEvent.ACTION_POINTER_DOWN)lock.preferVertical();
        else if(tracking && action==MotionEvent.ACTION_MOVE){
            AxisLock.Axis axis=lock.move(event.getRawX(),event.getRawY());
            if(axis==AxisLock.Axis.HORIZONTAL)requestDisallowInterceptTouchEvent(true);
            else if(axis==AxisLock.Axis.VERTICAL)requestDisallowInterceptTouchEvent(false);
        }
        try{return super.dispatchTouchEvent(event);}
        finally{if(action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_CANCEL){tracking=false;lock.end();}}
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent event){
        int action=event.getActionMasked();
        if(tracking){
            if(action==MotionEvent.ACTION_DOWN){super.onInterceptTouchEvent(event);return false;}
            if(action==MotionEvent.ACTION_MOVE && lock.axis()!=AxisLock.Axis.VERTICAL)return false;
        }
        return super.onInterceptTouchEvent(event);
    }
    private final class Horizontal extends HorizontalScrollView {
        Horizontal(Context context){super(context);}
        @Override public boolean onInterceptTouchEvent(MotionEvent event){
            if(tracking && event.getActionMasked()==MotionEvent.ACTION_MOVE && lock.axis()!=AxisLock.Axis.HORIZONTAL)return false;
            return super.onInterceptTouchEvent(event);
        }
        // Only defer MOVE. Native HorizontalScrollView handles DOWN/UP and its
        // inherited click/accessibility actions; no custom click is generated here.
        @android.annotation.SuppressLint("ClickableViewAccessibility")
        @Override public boolean onTouchEvent(MotionEvent event){
            // Non-clickable grid areas may deliver DOWN directly to this view. Do not
            // let its native drag detector choose an axis before our shared decision.
            if(tracking && event.getActionMasked()==MotionEvent.ACTION_MOVE && lock.axis()!=AxisLock.Axis.HORIZONTAL)return true;
            return super.onTouchEvent(event);
        }
    }
}
