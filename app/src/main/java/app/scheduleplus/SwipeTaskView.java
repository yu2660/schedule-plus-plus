package app.scheduleplus;

import android.content.Context;
import android.view.*;
import android.widget.FrameLayout;

/** Right swipe only; vertical scrolling, taps and long presses remain native. */
@android.annotation.SuppressLint("ViewConstructor") // Programmatic card wrapper requires its action.
public final class SwipeTaskView extends FrameLayout {
    private float startX,startY,delta;private boolean horizontal;private final Runnable completed;private final boolean enabled;private final int slop;
    public SwipeTaskView(Context c,boolean enabled,Runnable completed){super(c);this.enabled=enabled;this.completed=completed;slop=ViewConfiguration.get(c).getScaledTouchSlop();}
    @Override public boolean onInterceptTouchEvent(MotionEvent e){if(!enabled)return false;if(e.getActionMasked()==MotionEvent.ACTION_DOWN){startX=e.getX();startY=e.getY();horizontal=false;delta=0;}else if(e.getActionMasked()==MotionEvent.ACTION_MOVE){float dx=e.getX()-startX,dy=e.getY()-startY;if(dx>slop && dx>Math.abs(dy)*1.5f){horizontal=true;getParent().requestDisallowInterceptTouchEvent(true);return true;}}return false;}
    @android.annotation.SuppressLint("ClickableViewAccessibility") // This wrapper only intercepts drags; child card and checkbox own accessible clicks.
    @Override public boolean onTouchEvent(MotionEvent e){if(!horizontal)return super.onTouchEvent(e);delta=Math.max(0,Math.min(getWidth(),e.getX()-startX));if(e.getActionMasked()==MotionEvent.ACTION_MOVE){getChildAt(0).setTranslationX(delta);getChildAt(0).setAlpha(1-delta/Math.max(1,getWidth())*.4f);return true;}if(e.getActionMasked()==MotionEvent.ACTION_UP || e.getActionMasked()==MotionEvent.ACTION_CANCEL){View card=getChildAt(0);boolean done=e.getActionMasked()==MotionEvent.ACTION_UP && delta>Math.min(getWidth()*.28f,88*getResources().getDisplayMetrics().density);if(done){card.animate().translationX(getWidth()).alpha(0).setDuration(140).withEndAction(completed).start();}else card.animate().translationX(0).alpha(1).setDuration(140).start();horizontal=false;getParent().requestDisallowInterceptTouchEvent(false);return true;}return true;}
}
