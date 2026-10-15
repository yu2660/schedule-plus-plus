package app.scheduleplus;

import java.time.*;
import java.util.*;
import static app.scheduleplus.Models.*;

/** Read-only widget projection, independent of the week currently being browsed in the app. */
public final class WidgetData {
    public static final class Entry {
        public final String id,title,meta,detail,status;
        public final boolean urgent,ongoing,next;
        public Entry(String id,String title,String meta,String detail,boolean urgent){this(id,title,meta,detail,urgent,false,false,"");}
        public Entry(String id,String title,String meta,String detail,boolean urgent,boolean ongoing,boolean next,String status){this.id=id;this.title=title;this.meta=meta;this.detail=detail;this.urgent=urgent;this.ongoing=ongoing;this.next=next;this.status=status;}
    }
    public final String semesterId,semester;
    public final List<Entry> courses=new ArrayList<>(),tasks=new ArrayList<>();
    public LocalDateTime nextUpdate;
    public WidgetData(State state,LocalDateTime now){
        semesterId=state.id;semester=state.semester;nextUpdate=now.toLocalDate().plusDays(1).atStartOfDay().plusSeconds(1);
        int week=state.currentWeek(now.toLocalDate()),day=now.getDayOfWeek().getValue();
        if(week>=1 && week<=state.weeks){
            List<CourseTime> times=new ArrayList<>();
            for(Course c:state.courses){
                boolean[] sections=new boolean[state.slots.size()];
                for(Meeting m:c.meetings)if(m.day==day && m.weeks.contains(week))for(int i=m.first-1;i<m.last;i++)sections[i]=true;
                for(int i=0;i<sections.length;i++)if(sections[i]){int last=i;while(last+1<sections.length && sections[last+1])last++;
                    LocalDateTime start=now.toLocalDate().atTime(LocalTime.parse(state.slots.get(i).start)),end=now.toLocalDate().atTime(LocalTime.parse(state.slots.get(last).end));
                    if(end.isAfter(now)){times.add(new CourseTime(c,start,end));boundary(start,now);boundary(end,now);}i=last;
                }
            }
            times.sort(Comparator.comparing((CourseTime t)->t.start).thenComparing(t->t.course.name));
            LocalDateTime nextStart=null;for(CourseTime t:times)if(t.start.isAfter(now)){nextStart=t.start;break;}
            for(CourseTime t:times){boolean ongoing=!t.start.isAfter(now),next=t.start.equals(nextStart);
                String room=t.course.location.isEmpty()?"教室未填写":t.course.location,status=ongoing?"进行中":"";
                if(next){long minutes=Duration.between(now,t.start).toMinutes();if(now.plusMinutes(minutes).isBefore(t.start))minutes++;
                    status=minutes+"分钟后";boundary(t.start.minusMinutes(minutes-1),now);
                }
                courses.add(new Entry(t.course.id,t.course.name,(next?"下一节 · ":"")+t.start.toLocalTime()+"–"+t.end.toLocalTime(),room,false,ongoing,next,status));
            }
        }
        for(Task t:state.sortedTasks(null,false)){Course course=state.course(t.courseId);boolean late=t.deadline().isBefore(now);
            String date=t.date.equals(now.toLocalDate().toString())?"今天":t.date.equals(now.toLocalDate().plusDays(1).toString())?"明天":t.date;
            tasks.add(new Entry(t.id,t.title,course==null?"个人任务":course.name,(late?"已逾期 · ":"")+date+(t.time==null?" 截止":" "+t.time+" 截止"),late));boundary(t.deadline(),now);
        }
    }
    private void boundary(LocalDateTime value,LocalDateTime now){LocalDateTime next=value.plusSeconds(1);if(value.isAfter(now) && next.isBefore(nextUpdate))nextUpdate=next;}
    private static final class CourseTime {final Course course;final LocalDateTime start,end;CourseTime(Course c,LocalDateTime s,LocalDateTime e){course=c;start=s;end=e;}}
}
