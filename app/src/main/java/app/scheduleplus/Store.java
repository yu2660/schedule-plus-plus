package app.scheduleplus;

import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import static app.scheduleplus.Models.*;

public final class Store {
    public static final int LIMIT=2*1024*1024;
    private static final Object LOCK=new Object();
    private final AtomicFile file;
    public Store(Context context) { file=new AtomicFile(new File(context.getFilesDir(),"schedule-v1.json")); }
    public synchronized State load() throws Exception {
        synchronized(LOCK) {
        if(!file.getBaseFile().exists() && !new File(file.getBaseFile()+".bak").exists()) {State initial=new State();save(initial);return initial;}
        try(InputStream in=file.openRead()) { return decode(read(in)); }
        }
    }
    public synchronized State update(Consumer<State> change) throws Exception {
        synchronized(LOCK) { State s=load(); change.accept(s); save(s); return s; }
    }
    public synchronized void save(State state) throws Exception {
        synchronized(LOCK) {
        validate(state);String serialized=encode(state);byte[] data=serialized.getBytes(StandardCharsets.UTF_8);
        check(data.length<=LIMIT,"备份文件超过 2 MB 上限");
        JsonGuard.validate(serialized,12);
        FileOutputStream out=null;
        try { out=file.startWrite(); out.write(data); file.finishWrite(out); }
        catch(Exception e) { if(out!=null) file.failWrite(out); throw e; }
        }
    }
    public static String read(InputStream in) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] buf=new byte[8192]; int n;
        while((n=in.read(buf))!=-1) { if(out.size()+n>LIMIT) throw new IOException("文件超过 2 MB，已取消读取"); out.write(buf,0,n); }
        return out.toString(StandardCharsets.UTF_8.name());
    }
    public static String encode(State s) throws JSONException {
        JSONArray semesters=new JSONArray();for(Semester term:s.allSemesters())semesters.put(encodeSemester(term));
        JSONArray templates=new JSONArray();for(TaskTemplate t:s.templates)templates.put(new JSONObject().put("id",t.id).put("name",t.name).put("prefix",t.prefix).put("notes",t.notes).put("reminderMinutes",t.reminderMinutes));
        JSONObject display=new JSONObject().put("weekends",s.display.weekends).put("teacher",s.display.teacher).put("location",s.display.location).put("time",s.display.time);
        return new JSONObject().put("format","schedule++").put("version",3).put("activeSemesterId",s.id).put("semesters",semesters).put("display",display).put("templates",templates).toString(2);
    }
    private static JSONObject encodeSemester(Semester s) throws JSONException {
        JSONObject o=new JSONObject().put("id",s.id).put("semester",s.semester).put("description",s.description).put("monday",s.monday).put("weeks",s.weeks);
        JSONArray slots=new JSONArray(),courses=new JSONArray(),tasks=new JSONArray();
        for(Slot p:s.slots) slots.put(new JSONObject().put("start",p.start).put("end",p.end));
        for(Course c:s.courses) {
            JSONArray meetings=new JSONArray();
            for(Meeting m:c.meetings) meetings.put(new JSONObject().put("day",m.day).put("first",m.first).put("last",m.last).put("weeks",new JSONArray(m.weeks)));
            courses.put(new JSONObject().put("id",c.id).put("name",c.name).put("location",c.location).put("teacher",c.teacher).put("color",c.color).put("meetings",meetings));
        }
        for(Task t:s.tasks) tasks.put(new JSONObject().put("id",t.id).put("title",t.title).put("courseId",nullable(t.courseId)).put("date",t.date).put("time",nullable(t.time)).put("notes",t.notes).put("reminder",nullable(t.reminder)).put("done",t.done).put("reminded",t.reminded).put("reminderMinutes",t.reminderMinutes));
        return o.put("slots",slots).put("courses",courses).put("tasks",tasks);
    }
    private static Object nullable(String s) { return s==null?JSONObject.NULL:s; }
    private static String optional(JSONObject o,String key) throws JSONException { return o.isNull(key)?null:o.getString(key); }
    public static State decode(String raw) throws Exception {
        check(raw.getBytes(StandardCharsets.UTF_8).length<=LIMIT,"备份超过 2 MB 上限");
        JsonGuard.validate(raw,12);
        JSONTokener parser=new JSONTokener(raw); Object parsed=parser.nextValue();
        check(parsed instanceof JSONObject && parser.nextClean()==0,"备份必须是完整的 JSON 对象");
        JSONObject o=(JSONObject)parsed;
        int version=o.getInt("version");check("schedule++".equals(o.getString("format")) && (version>=1 && version<=3),"不支持的备份格式或版本");
        State state=new State();
        if(version==1) {
            copySemester(decodeSemester(o,false),state);
            // Stable migration identity even before the first V2 save.
            state.id=java.util.UUID.nameUUIDFromBytes(raw.getBytes(StandardCharsets.UTF_8)).toString();
        } else {
            JSONArray terms=o.getJSONArray("semesters");check(terms.length()>=1 && terms.length()<=30,"学期数量需为 1–30");
            String active=o.getString("activeSemesterId");boolean found=false;
            for(int i=0;i<terms.length();i++) {
                Semester term=decodeSemester(terms.getJSONObject(i),true);
                if(term.id.equals(active) && !found){copySemester(term,state);found=true;}else state.otherSemesters.add(term);
            }
            check(found,"当前学期不存在");
        }
        if(version==3){
            JSONObject d=o.getJSONObject("display");state.display.weekends=d.getBoolean("weekends");state.display.teacher=d.getBoolean("teacher");state.display.location=d.getBoolean("location");state.display.time=d.getBoolean("time");
            JSONArray ts=o.getJSONArray("templates");check(ts.length()<=30,"最多保存 30 个任务模板");state.templates.clear();
            for(int i=0;i<ts.length();i++){JSONObject p=ts.getJSONObject(i);TaskTemplate t=new TaskTemplate();t.id=p.getString("id");t.name=p.getString("name");t.prefix=p.getString("prefix");t.notes=p.getString("notes");t.reminderMinutes=p.getInt("reminderMinutes");state.templates.add(t);}
        }
        validate(state);return state;
    }
    private static Semester decodeSemester(JSONObject o,boolean hasIdentity) throws Exception {
        Semester s=new Semester();s.slots.clear(); s.semester=o.getString("semester"); s.monday=o.getString("monday"); s.weeks=o.getInt("weeks");
        if(hasIdentity){s.id=o.getString("id");s.description=o.getString("description");}
        JSONArray slots=o.getJSONArray("slots"),courses=o.getJSONArray("courses"),tasks=o.getJSONArray("tasks");
        check(slots.length()<=MAX_PERIODS && courses.length()<=MAX_COURSES && tasks.length()<=MAX_TASKS,"备份条数超过上限");
        for(int i=0;i<slots.length();i++) { JSONObject p=slots.getJSONObject(i); s.slots.add(new Slot(p.getString("start"),p.getString("end"))); }
        for(int i=0;i<courses.length();i++) {
            JSONObject p=courses.getJSONObject(i); Course c=new Course(); c.id=p.getString("id"); c.name=p.getString("name"); c.location=p.getString("location"); c.teacher=p.getString("teacher"); c.color=p.getInt("color");
            JSONArray ms=p.getJSONArray("meetings"); check(ms.length()<=30,"上课时段过多");
            for(int j=0;j<ms.length();j++) {
                JSONObject q=ms.getJSONObject(j); Meeting m=new Meeting(); m.day=q.getInt("day"); m.first=q.getInt("first"); m.last=q.getInt("last");
                JSONArray ws=q.getJSONArray("weeks"); check(ws.length()<=MAX_WEEKS,"教学周数过多");
                for(int k=0;k<ws.length();k++) { int w=ws.getInt(k); check(m.weeks.add(w),"教学周重复"); }
                c.meetings.add(m);
            }
            s.courses.add(c);
        }
        for(int i=0;i<tasks.length();i++) {
            JSONObject p=tasks.getJSONObject(i); Task t=new Task(); t.id=p.getString("id"); t.title=p.getString("title"); t.courseId=optional(p,"courseId"); t.date=p.getString("date"); t.time=optional(p,"time"); t.notes=p.getString("notes"); t.reminder=optional(p,"reminder"); t.done=p.getBoolean("done"); t.reminded=p.getBoolean("reminded");t.reminderMinutes=p.has("reminderMinutes")?p.getInt("reminderMinutes"):-1;s.tasks.add(t);
        }
        validateSemester(s); return s;
    }
}
