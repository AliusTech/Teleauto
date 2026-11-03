package tech.alius.rc.license.internal;

/** Epoch seconds plus monotonic milliseconds. Wall-clock rollback tolerance is 120 seconds. */
public final class TeleautoLicenseClock {
    public static final long TOLERANCE=120;
    public static final class State {
        public final long wall,elapsed,boot;
        public State(long wall,long elapsed,long boot){this.wall=wall;this.elapsed=elapsed;this.boot=boot;}
    }
    public static long effectiveTime(State before,long wall,long elapsed,long boot){
        if(wall<0||elapsed<0)throw new IllegalArgumentException();
        if(before==null)return wall;
        long floor=before.wall;
        if(boot>=0&&boot==before.boot){if(elapsed<before.elapsed)throw new IllegalArgumentException();floor+= (elapsed-before.elapsed)/1000;}
        if(wall<floor-TOLERANCE)throw new IllegalArgumentException();
        return Math.max(wall,floor);
    }
}
