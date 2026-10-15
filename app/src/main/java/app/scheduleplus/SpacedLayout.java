package app.scheduleplus;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

/** Shared action spacing keeps stacked and adjacent rounded buttons visually separate. */
public final class SpacedLayout extends LinearLayout {
    public SpacedLayout(Context context){super(context);}
    @Override public void addView(View child,int index,ViewGroup.LayoutParams params){
        // Undo actions are text on a compact bar, rather than rounded action cards.
        boolean textOnly=child.getBackground() instanceof ColorDrawable && ((ColorDrawable)child.getBackground()).getColor()==android.graphics.Color.TRANSPARENT;
        if(child instanceof Button && !textOnly){
            LayoutParams p=params instanceof LayoutParams?(LayoutParams)params:params==null?generateDefaultLayoutParams():new LayoutParams(params);
            int gap=Math.round(4*getResources().getDisplayMetrics().density);
            p.topMargin=Math.max(p.topMargin,gap);p.bottomMargin=Math.max(p.bottomMargin,gap);
            if(getOrientation()==HORIZONTAL){p.leftMargin=Math.max(p.leftMargin,gap);p.rightMargin=Math.max(p.rightMargin,gap);}
            params=p;
        }
        super.addView(child,index,params);
    }
}
