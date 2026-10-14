package app.scheduleplus;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

public final class Models {
    private Models() {}
    public static final int MAX_WEEKS = 60, MAX_PERIODS = 24, MAX_COURSES = 500, MAX_TASKS = 5000;
    public static class Slot {
        public String start, end;
        public Slot(String start, String end) { this.start=start; this.end=end; }
    }
    public static class Meeting {
        public int day, first, last;
        public final TreeSet<Integer> weeks = new TreeSet<>();
    }
    public static class Course {
        public String id=UUID.randomUUID().toString(), name="", location="", teacher="";
        public int color=0;
        public final List<Meeting> meetings=new ArrayList<>();
    }
    public static class Task {
        public String id=UUID.randomUUID().toString(), title="", courseId=null, date="", time=null, notes="", reminder=null;
        public boolean done=false, reminded=false;
        public int reminderMinutes=-1;
        public LocalDateTime deadline() { return LocalDate.parse(date).atTime(time==null ? LocalTime.MAX : LocalTime.parse(time)); }
        public long reminderMillis() { return LocalDateTime.parse(reminder).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(); }
    }
    public static class TaskTemplate {
        public String id=UUID.randomUUID().toString(),name="",prefix="",notes="";
        public int reminderMinutes=-1;
    }
    public static class DisplayOptions {
        public boolean weekends=true,teacher=true,location=true,time=true;
    }
    public static class Semester {
        public String id=UUID.randomUUID().toString(), semester="我的学期", description="", monday=startOfWeek(LocalDate.now()).toString();
        public int weeks=20;
        public final List<Slot> slots=new ArrayList<>();
        public final List<Course> courses=new ArrayList<>();
        public final List<Task> tasks=new ArrayList<>();
        public Semester() {
            String[] times={"08:00","08:55","10:00","10:55","14:00","14:55","16:00","16:55","19:00","19:55"};
            for(String t:times) slots.add(new Slot(t,LocalTime.parse(t).plusMinutes(45).toString()));
        }
        public Course course(String id) { for(Course c:courses) if(c.id.equals(id)) return c; return null; }
        public Task task(String id) { for(Task t:tasks) if(t.id.equals(id)) return t; return null; }
        public int currentWeek(LocalDate day) { return (int)Math.floorDiv(ChronoUnit.DAYS.between(LocalDate.parse(monday),day),7)+1; }
        public List<Task> sortedTasks(String courseId, boolean done) {
            List<Task> found=new ArrayList<>();
            for(Task t:tasks) if(t.done==done && (courseId==null || courseId.equals(t.courseId))) found.add(t);
            found.sort(Comparator.comparing(Task::deadline).thenComparing(t->t.id)); return found;
        }
    }
    public static class State extends Semester {
        public final DisplayOptions display=new DisplayOptions();
        public final List<TaskTemplate> templates=new ArrayList<>();
        public State(){
            for(String name:new String[]{"作业","实验报告","考试"}){TaskTemplate t=new TaskTemplate();t.id=UUID.nameUUIDFromBytes(("schedule++/template/"+name).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();t.name=name;t.prefix=name+"：";t.reminderMinutes=name.equals("考试")?60:1440;templates.add(t);}
        }
        public final List<Semester> otherSemesters=new ArrayList<>();
        public List<Semester> allSemesters() { List<Semester> all=new ArrayList<>();all.add(this);all.addAll(otherSemesters);return all; }
        public List<Task> allTasks() { List<Task> all=new ArrayList<>();for(Semester s:allSemesters())all.addAll(s.tasks);return all; }
        public Semester taskOwner(String taskId) { for(Semester s:allSemesters())if(s.task(taskId)!=null)return s;return null; }
        public Task findTask(String taskId) { Semester s=taskOwner(taskId);return s==null?null:s.task(taskId); }
        public Semester findSemester(String semesterId) { for(Semester s:allSemesters())if(s.id.equals(semesterId))return s;return null; }
        public void switchSemester(String semesterId) {
            if(id.equals(semesterId))return;
            Semester target=findSemester(semesterId);check(target!=null,"学期不存在");
            Semester previous=new Semester();copySemester(this,previous);
            otherSemesters.remove(target);otherSemesters.add(previous);copySemester(target,this);
        }
        public void addSemester(Semester semester) {
            check(findSemester(semester.id)==null,"学期 ID 重复");otherSemesters.add(semester);switchSemester(semester.id);
        }
        public void deleteCurrentSemester() {
            check(!otherSemesters.isEmpty(),"至少保留一个学期");String removed=id;switchSemester(otherSemesters.get(0).id);otherSemesters.removeIf(s->s.id.equals(removed));
        }
    }
    public static void copySemester(Semester from,Semester to) {
        to.id=from.id;to.semester=from.semester;to.description=from.description;to.monday=from.monday;to.weeks=from.weeks;
        to.slots.clear();to.slots.addAll(from.slots);to.courses.clear();to.courses.addAll(from.courses);to.tasks.clear();to.tasks.addAll(from.tasks);
    }
    public static Meeting duplicateMeeting(Meeting from){Meeting m=new Meeting();m.day=from.day;m.first=from.first;m.last=from.last;m.weeks.addAll(from.weeks);return m;}
    public static Course duplicateCourse(Course from){Course c=new Course();c.name=from.name;c.location=from.location;c.teacher=from.teacher;c.color=from.color;for(Meeting m:from.meetings)c.meetings.add(duplicateMeeting(m));return c;}
    /** Copies are independent; old tasks and their reminder state never enter a new semester. */
    public static Semester reuseSemester(Semester source,String name,String note,String monday,int weeks,boolean courses){
        Semester s=new Semester();s.semester=name;s.description=note;s.monday=monday;s.weeks=weeks;
        if(source!=null){s.slots.clear();for(Slot p:source.slots)s.slots.add(new Slot(p.start,p.end));
            if(courses)for(Course old:source.courses){Course c=duplicateCourse(old);for(Meeting m:c.meetings)m.weeks.removeIf(w->w>weeks);c.meetings.removeIf(m->m.weeks.isEmpty());if(!c.meetings.isEmpty())s.courses.add(c);}}
        validateSemester(s);return s;
    }
    public static LocalDateTime relativeReminder(String date,String time,int minutes){return LocalDate.parse(date).atTime(time==null?LocalTime.of(23,59):LocalTime.parse(time)).minusMinutes(minutes);}
    public static String taskGroup(Task t,LocalDateTime now){if(t.deadline().isBefore(now))return "已逾期";LocalDate d=LocalDate.parse(t.date);return d.equals(now.toLocalDate())?"今天":d.equals(now.toLocalDate().plusDays(1))?"明天":"之后";}
    public static String relativeDeadline(Task t,LocalDateTime now){if(t.deadline().isBefore(now)){long minutes=Math.max(1,ChronoUnit.MINUTES.between(t.deadline(),now));return "已逾期 "+(minutes<60?minutes+" 分钟":minutes<1440?minutes/60+" 小时":minutes/1440+" 天");}long days=ChronoUnit.DAYS.between(now.toLocalDate(),LocalDate.parse(t.date));return days==0?"今天截止":days==1?"明天截止":"还剩 "+days+" 天";}
    public static class CourseBlock {
        public final int first; public int last;
        public final List<Course> courses;
        CourseBlock(int section,List<Course> courses) { first=last=section;this.courses=courses; }
    }
    public static class TimetableFocus {
        public final int day,first,last;
        TimetableFocus(int day,int first,int last){this.day=day;this.first=first;this.last=last;}
    }
    /** Prefer the ongoing merged course; otherwise use the current or next teaching period. */
    public static TimetableFocus timetableFocus(Semester semester,LocalDateTime now,boolean weekends){
        int week=semester.currentWeek(now.toLocalDate()),day=now.getDayOfWeek().getValue();
        if(week<1 || week>semester.weeks || !weekends && day>5 || semester.slots.isEmpty())return null;
        List<CourseBlock> blocks=courseBlocks(semester,day,week);LocalTime time=now.toLocalTime();
        for(CourseBlock b:blocks)if(!b.courses.isEmpty() && !time.isBefore(LocalTime.parse(semester.slots.get(b.first-1).start)) && time.isBefore(LocalTime.parse(semester.slots.get(b.last-1).end)))return new TimetableFocus(day,b.first,b.last);
        int section=semester.slots.size();for(int i=0;i<semester.slots.size();i++)if(time.isBefore(LocalTime.parse(semester.slots.get(i).end))){section=i+1;break;}
        for(CourseBlock b:blocks)if(b.first<=section && section<=b.last)return new TimetableFocus(day,b.first,b.last);
        return null;
    }
    /** Merge consecutive sections with the same visible course set; retain overlap boundaries. */
    public static List<CourseBlock> courseBlocks(Semester semester,int day,int week) {
        List<CourseBlock> blocks=new ArrayList<>();
        for(int section=1;section<=semester.slots.size();section++) {
            List<Course> matching=new ArrayList<>();
            for(Course c:semester.courses)for(Meeting m:c.meetings)if(occurs(m,day,week,section)){matching.add(c);break;}
            CourseBlock previous=blocks.isEmpty()?null:blocks.get(blocks.size()-1);
            if(!matching.isEmpty() && previous!=null && previous.courses.equals(matching))previous.last=section;
            else blocks.add(new CourseBlock(section,matching));
        }
        return blocks;
    }
    /** Catch up only recent undelivered reminders, including a delayed inexact alarm on resume. */
    public static long nextReminderTrigger(Task task,long now) {
        if(task.done || task.reminder==null || task.reminded)return -1;
        long at=task.reminderMillis();
        if(at<now-Duration.ofHours(24).toMillis())return -1;
        return Math.max(at,now+1000);
    }
    /** Ported/adapted from ShiGuangSchedule ScheduleDateTimeUtils.kt, Apache-2.0.
     * Source commit feabb68f75c4058319b449287980b5f645eb8d61. See THIRD_PARTY_NOTICES.md.
     */
    public static LocalDate startOfWeek(LocalDate date) { return date.minusDays((date.getDayOfWeek().getValue()-1+7)%7); }
    public static boolean occurs(Meeting m, int day, int week, int section) {
        return m.day==day && m.weeks.contains(week) && section>=m.first && section<=m.last;
    }
    public static boolean conflicts(Meeting a, Meeting b) {
        return a.day==b.day && a.first<=b.last && b.first<=a.last && !Collections.disjoint(a.weeks,b.weeks);
    }
    public static void validate(State s) {
        check(s.templates.size()<=30,"最多保存 30 个任务模板");Set<String> templateIds=new HashSet<>();
        for(TaskTemplate t:s.templates){uuid(t.id);check(templateIds.add(t.id),"模板 ID 重复");check(!t.name.trim().isEmpty() && t.name.length()<=40,"模板名称需为 1–40 字");check(t.prefix.length()<=200 && t.notes.length()<=8000,"模板内容过长");check(t.reminderMinutes==-1 || t.reminderMinutes>=0 && t.reminderMinutes<=10080,"模板提醒范围为截止前 0–10080 分钟");}
        check(s.otherSemesters.size()<30,"最多保存 30 个学期");
        Set<String> semesterIds=new HashSet<>(),courseIds=new HashSet<>(),taskIds=new HashSet<>();int courseCount=0,taskCount=0;
        for(Semester term:s.allSemesters()) {
            uuid(term.id);check(semesterIds.add(term.id),"学期 ID 重复");validateSemester(term);
            courseCount+=term.courses.size();taskCount+=term.tasks.size();
            for(Course c:term.courses)check(courseIds.add(c.id),"跨学期课程 ID 重复");
            for(Task t:term.tasks)check(taskIds.add(t.id),"跨学期任务 ID 重复");
        }
        check(courseCount<=MAX_COURSES && taskCount<=MAX_TASKS,"全部学期的数据条数超过上限");
    }
    public static void validateSemester(Semester s) {
        check(!s.semester.trim().isEmpty() && s.semester.length()<=100,"学期名称需为 1–100 字");
        check(s.description.length()<=500,"学期备注最多 500 字");
        check(LocalDate.parse(s.monday).getDayOfWeek()==DayOfWeek.MONDAY,"第一教学周的起始日必须是周一");
        validDate(LocalDate.parse(s.monday));
        check(s.weeks>=1 && s.weeks<=MAX_WEEKS,"教学周数需为 1–60");
        check(!s.slots.isEmpty() && s.slots.size()<=MAX_PERIODS,"每日节数需为 1–24");
        LocalTime previous=null;
        for(Slot p:s.slots) {
            LocalTime start=LocalTime.parse(p.start), end=LocalTime.parse(p.end);
            new TimeSpan(start,end);
            check(previous==null || !start.isBefore(previous),"作息时间必须按顺序排列且不能重叠"); previous=end;
        }
        check(s.courses.size()<=MAX_COURSES && s.tasks.size()<=MAX_TASKS,"数据条数超过上限");
        Set<String> ids=new HashSet<>();
        for(Course c:s.courses) {
            uuid(c.id); check(ids.add(c.id),"课程 ID 重复");
            check(!c.name.trim().isEmpty() && c.name.length()<=100,"课程名称需为 1–100 字");
            check(c.location.length()<=200 && c.teacher.length()<=100,"课程信息过长");
            check(c.color>=0 && c.color<6,"课程颜色无效");
            check(!c.meetings.isEmpty() && c.meetings.size()<=30,"课程需有 1–30 个上课时段");
            for(Meeting m:c.meetings) {
                check(m.day>=1 && m.day<=7 && m.first>=1 && m.last>=m.first && m.last<=s.slots.size(),"上课星期或节次无效");
                check(!m.weeks.isEmpty() && m.weeks.first()>=1 && m.weeks.last()<=s.weeks,"请选择有效的教学周");
            }
        }
        Set<String> taskIds=new HashSet<>();
        for(Task t:s.tasks) {
            uuid(t.id); check(taskIds.add(t.id),"任务 ID 重复");
            check(!t.title.trim().isEmpty() && t.title.length()<=200,"任务标题需为 1–200 字");
            check(t.notes.length()<=8000,"补充说明最多 8000 字");
            check(t.reminderMinutes==-1 || t.reminderMinutes>=0 && t.reminderMinutes<=10080,"快捷提醒范围无效");
            check(t.reminderMinutes==-1 || t.reminder!=null,"快捷提醒缺少提醒时间");
            check(t.courseId==null || ids.contains(t.courseId),"任务关联的课程不存在");
            validDate(t.deadline().toLocalDate());
            if(t.reminder!=null) {
                LocalDateTime reminder=LocalDateTime.parse(t.reminder);validDate(reminder.toLocalDate());
                check(t.reminderMinutes<0 || reminder.equals(relativeReminder(t.date,t.time,t.reminderMinutes).withSecond(0).withNano(0)),"快捷提醒与截止时间不一致");
                check(!reminder.isAfter(t.deadline()),"提醒不能晚于截止时间");t.reminderMillis();
            }
        }
    }
    private static void uuid(String id) { check(UUID.fromString(id).toString().equals(id),"ID 格式无效"); }
    private static void validDate(LocalDate date) { check(date.getYear()>=1900 && date.getYear()<=2200,"日期需在 1900–2200 年之间"); }
    public static void check(boolean ok,String message) { if(!ok) throw new IllegalArgumentException(message); }
}
