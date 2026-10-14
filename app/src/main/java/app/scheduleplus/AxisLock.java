package app.scheduleplus;

/** A gesture chooses one axis after touch slop, then keeps it until lift/cancel. */
public final class AxisLock {
    public enum Axis { UNDECIDED, HORIZONTAL, VERTICAL }
    private final float slop;
    private float startX,startY;
    private Axis axis=Axis.UNDECIDED;
    public AxisLock(float slop){this.slop=slop;}
    public void begin(float x,float y){startX=x;startY=y;axis=Axis.UNDECIDED;}
    public Axis move(float x,float y){
        if(axis==Axis.UNDECIDED){float dx=Math.abs(x-startX),dy=Math.abs(y-startY);
            if(Math.max(dx,dy)>slop)axis=dx>dy*1.35f?Axis.HORIZONTAL:Axis.VERTICAL;
        }
        return axis;
    }
    public Axis axis(){return axis;}
    public void preferVertical(){if(axis==Axis.UNDECIDED)axis=Axis.VERTICAL;}
    public void end(){axis=Axis.UNDECIDED;}
}
