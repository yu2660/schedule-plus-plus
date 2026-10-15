package app.scheduleplus;

import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.*;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.*;

/** Device-local presentation settings. Photos never enter a course backup or a network request. */
public final class Appearance {
    public static final class Options {
        public int mode=0,background=1,style=0,opacity=90,mask=65;
        public String start="#F1F5FF",end="#F5FAF8";
        public Options copy(){Options o=new Options();o.mode=mode;o.background=background;o.style=style;o.opacity=opacity;o.mask=mask;o.start=start;o.end=end;return o;}
        public String encode(){try{return new JSONObject().put("mode",mode).put("background",background).put("style",style).put("opacity",opacity).put("mask",mask).put("start",start).put("end",end).toString();}catch(Exception e){throw new IllegalStateException(e);}}
        public static Options decode(String raw){Options o=new Options();try{JSONObject j=new JSONObject(raw);o.mode=j.getInt("mode");o.background=j.getInt("background");o.style=j.getInt("style");o.opacity=j.getInt("opacity");o.mask=j.getInt("mask");o.start=j.getString("start");o.end=j.getString("end");o.validate();}catch(Exception e){return new Options();}return o;}
        public void validate(){if(mode<0 || mode>2 || background<0 || background>2 || style<0 || style>3 || opacity<85 || opacity>100 || mask<25 || mask>90 || !start.matches("#[0-9a-fA-F]{6}") || !end.matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException("请使用有效的外观选项和 #RRGGBB 颜色");}
    }
    public final Options options;
    public final boolean dark;
    public final int bg,surface,ink,muted,accent,soft,line,danger,onAccent;
    public final int[] fills,colors;
    private final Context context;
    private Bitmap wallpaper;
    private static java.lang.ref.WeakReference<Bitmap> cachedPhoto=new java.lang.ref.WeakReference<>(null);private static String photoKey="";
    public Appearance(Context c){this(c,Options.decode(c.getSharedPreferences("appearance",Context.MODE_PRIVATE).getString("options","")));}
    public Appearance(Context c,Options o){context=c;options=o;dark=o.mode==2 || o.mode==0 && (c.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        bg=dark?0xff101827:0xffF3F6FC;surface=dark?0xff1C283B:Color.WHITE;ink=dark?0xffECF1FA:0xff1C2C48;muted=dark?0xffACB9CC:0xff60718A;accent=dark?0xffADC3FF:0xff355BC5;soft=dark?0xff293953:0xffEAF0FF;line=dark?0xff33435B:0xffE3EAF4;danger=dark?0xffFFACAA:0xffAF414D;onAccent=dark?0xff142341:Color.WHITE;
        fills=dark?new int[]{0xff34314F,0xff213F3B,0xff473A2B,0xff482E40,0xff263C56,0xff403B30}:new int[]{0xffEEEAFE,0xffE0F3EC,0xffFFF0DC,0xffFCE5EF,0xffE4EEFF,0xffF2EDDF};
        colors=dark?new int[]{0xffD2C9FF,0xffA5DDCE,0xffF6D5A1,0xffF0BDD6,0xffB7D1FF,0xffE1D5B7}:new int[]{0xff7160B5,0xff2E7969,0xffA06B31,0xffA04F79,0xff416CAE,0xff80704C};
    }
    public void save(Options o){o.validate();if(!context.getSharedPreferences("appearance",Context.MODE_PRIVATE).edit().putString("options",o.encode()).commit())throw new IllegalStateException("外观设置保存失败");}
    public File photoFile(){return new File(context.getFilesDir(),"wallpaper.jpg");}
    public Drawable background(){
        if(options.background==2 && photoFile().exists()){
            if(wallpaper==null){String key=photoFile().getAbsolutePath()+":"+photoFile().lastModified()+":"+photoFile().length();synchronized(Appearance.class){wallpaper=key.equals(photoKey)?cachedPhoto.get():null;if(wallpaper==null){wallpaper=BitmapFactory.decodeFile(photoFile().getAbsolutePath());photoKey=key;cachedPhoto=new java.lang.ref.WeakReference<>(wallpaper);}}}
            if(wallpaper!=null)return new Photo(wallpaper,dark?0xff101827:0xffF5F8FF,options.mask);
        }
        int first=backgroundColor(options.start),last=backgroundColor(options.end);
        return options.background==0?new ColorDrawable(first):new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{first,last});
    }
    public int backgroundColor(String value){int color=Color.parseColor(value);return dark?blend(color,bg,.92f):color;}
    public GradientDrawable rounded(int color,float radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}
    public GradientDrawable course(int index,float radius,boolean current){
        int fill=options.style==0?fills[index]:surface;if(options.style==2)fill=alpha(surface,options.opacity);
        GradientDrawable d=rounded(fill,radius);if(current || options.style==3)d.setStroke(Math.max(1,(int)(context.getResources().getDisplayMetrics().density*(current?2:1))),current?accent:colors[index]);return d;
    }
    public static int alpha(int color,int percent){return (color&0xffffff)|(Math.round(percent*2.55f)<<24);}
    public static int blend(int a,int b,float ratio){return Color.rgb((int)(Color.red(a)*(1-ratio)+Color.red(b)*ratio),(int)(Color.green(a)*(1-ratio)+Color.green(b)*ratio),(int)(Color.blue(a)*(1-ratio)+Color.blue(b)*ratio));}
    public static Bitmap readPhoto(InputStream in)throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(bytes.size()+n>20*1024*1024)throw new IOException("图片文件不能超过 20 MB");bytes.write(buffer,0,n);}byte[] data=bytes.toByteArray();BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,bounds);
        if(bounds.outWidth<=0 || bounds.outHeight<=0 || (long)bounds.outWidth*bounds.outHeight>64000000)throw new IOException("图片尺寸无效或超过 6400 万像素");
        if(android.os.Build.VERSION.SDK_INT>=28)return ImageDecoder.decodeBitmap(ImageDecoder.createSource(java.nio.ByteBuffer.wrap(data)),(decoder,info,source)->{int max=Math.max(info.getSize().getWidth(),info.getSize().getHeight());decoder.setTargetSampleSize(Math.max(1,(int)Math.ceil(max/2048.0)));decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);});
        BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;while(Math.max(bounds.outWidth,bounds.outHeight)/options.inSampleSize>2048)options.inSampleSize*=2;
        Bitmap image=BitmapFactory.decodeByteArray(data,0,data.length,options);if(image==null)throw new IOException("无法读取此图片");return image;
    }
    public void writePhoto(Bitmap image)throws IOException{AtomicFile file=new AtomicFile(photoFile());FileOutputStream out=null;try{out=file.startWrite();if(!image.compress(Bitmap.CompressFormat.JPEG,88,out))throw new IOException("图片保存失败");file.finishWrite(out);synchronized(Appearance.class){photoKey="";cachedPhoto=new java.lang.ref.WeakReference<>(null);}}catch(IOException|RuntimeException e){if(out!=null)file.failWrite(out);throw e;}}
    private static final class Photo extends Drawable {
        final Bitmap image;final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);final int color,mask;
        Photo(Bitmap b,int c,int m){image=b;color=c;mask=m;}
        @Override public void draw(Canvas c){Rect r=getBounds();int saved=c.save();c.clipRect(r);float scale=Math.max(r.width()/(float)image.getWidth(),r.height()/(float)image.getHeight());float w=image.getWidth()*scale,h=image.getHeight()*scale;c.drawBitmap(image,null,new RectF(r.centerX()-w/2,r.centerY()-h/2,r.centerX()+w/2,r.centerY()+h/2),p);c.drawColor(alpha(color,mask));c.restoreToCount(saved);}
        @Override public void setAlpha(int a){p.setAlpha(a);invalidateSelf();}@Override public void setColorFilter(ColorFilter f){p.setColorFilter(f);invalidateSelf();}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
