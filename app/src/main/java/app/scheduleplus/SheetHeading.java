package app.scheduleplus;

import android.content.Context;
import android.view.*;
import android.widget.LinearLayout;

public final class SheetHeading extends LinearLayout {
    private float startY;private boolean moved;private Runnable expand=()->{},close=()->{};
    public SheetHeading(Context context){super(context);setOrientation(VERTICAL);setClickable(true);setFocusable(true);}
    public void setActions(Runnable expand,Runnable close){this.expand=expand;this.close=close;}
    @Override public boolean onTouchEvent(MotionEvent e){float density=getResources().getDisplayMetrics().density;switch(e.getActionMasked()){
        case MotionEvent.ACTION_DOWN:startY=e.getRawY();moved=false;return true;
        case MotionEvent.ACTION_MOVE:float delta=e.getRawY()-startY;if(Math.abs(delta)>ViewConfiguration.get(getContext()).getScaledTouchSlop())moved=true;if(moved)getRootView().setTranslationY(Math.max(-14*density,Math.min(180*density,delta)));return true;
        case MotionEvent.ACTION_UP:float travel=e.getRawY()-startY;getRootView().setTranslationY(0);if(!moved)performClick();else if(travel>64*density)close.run();else if(travel<-40*density)expand.run();return true;
        case MotionEvent.ACTION_CANCEL:getRootView().setTranslationY(0);return true;
        default:return true;
    }}
    @Override public boolean performClick(){super.performClick();expand.run();return true;}
}
