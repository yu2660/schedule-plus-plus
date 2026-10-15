package app.scheduleplus;

import android.content.*;
import android.content.res.Configuration;
import android.view.View;
import android.widget.*;
import java.time.LocalDateTime;
import java.util.*;

public final class WidgetListService extends RemoteViewsService {
    @Override public void onConfigurationChanged(Configuration config){super.onConfigurationChanged(config);ScheduleWidget.requestRefresh(this);}
    @Override public RemoteViewsFactory onGetViewFactory(Intent intent){return new Factory(getApplicationContext(),intent.getIntExtra("kind",0));}
    static final class Factory implements RemoteViewsFactory {
        private final Context context;private final int kind;private List<WidgetData.Entry> entries=Collections.emptyList();private String semesterId="";
        Factory(Context c,int kind){context=c;this.kind=kind;}
        @Override public void onCreate(){onDataSetChanged();}
        @Override public synchronized void onDataSetChanged(){try{WidgetData data=new WidgetData(new Store(context).load(),LocalDateTime.now());entries=kind==0?data.courses:data.tasks;semesterId=data.semesterId;}catch(Exception ignored){entries=Collections.emptyList();semesterId="";}}
        @Override public synchronized void onDestroy(){entries=Collections.emptyList();}
        @Override public synchronized int getCount(){return entries.size();}
        @Override public synchronized RemoteViews getViewAt(int position){if(position<0||position>=entries.size())return null;WidgetData.Entry entry=entries.get(position);RemoteViews v=new RemoteViews(context.getPackageName(),kind==0?R.layout.widget_course_item:entry.urgent?R.layout.widget_item_late:R.layout.widget_item);
            v.setTextViewText(R.id.widget_item_title,entry.title);v.setTextViewText(R.id.widget_item_meta,entry.meta);v.setTextViewText(R.id.widget_item_detail,entry.detail);
            if(kind==0){v.setViewVisibility(R.id.widget_course_bar,entry.ongoing?View.VISIBLE:View.INVISIBLE);v.setViewVisibility(R.id.widget_course_status,entry.ongoing||entry.next?View.VISIBLE:View.GONE);v.setTextViewText(R.id.widget_course_status,entry.status);}
            Intent fill=new Intent().putExtra("widgetSemester",semesterId).putExtra(kind==0?"widgetCourse":"widgetTask",entry.id);v.setOnClickFillInIntent(R.id.widget_item,fill);return v;
        }
        @Override public RemoteViews getLoadingView(){return null;}
        @Override public int getViewTypeCount(){return 3;}
        @Override public long getItemId(int position){return position;}
        @Override public boolean hasStableIds(){return false;}
    }
}
