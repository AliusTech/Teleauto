package tech.alius.rc.license.internal;

/** Process-local monotonic floor plus the highest observed wall clock. No Android dependencies. */
public final class TeleautoLicenseTimeGuard {
    private final long startWall, startElapsed, expiresAt;
    private long highWater;
    public TeleautoLicenseTimeGuard(long effectiveWall, long elapsed, long expiresAt) {
        if(effectiveWall < 0 || elapsed < 0 || expiresAt <= effectiveWall) throw new IllegalArgumentException();
        startWall=effectiveWall;startElapsed=elapsed;this.expiresAt=expiresAt;highWater=effectiveWall;
    }
    public synchronized long time(long wall,long elapsed) {
        if(wall<0 || elapsed<startElapsed) throw new IllegalArgumentException("Clock rollback");
        long floor=Math.max(highWater,startWall+(elapsed-startElapsed)/1000);
        if(wall<floor-TeleautoLicenseClock.TOLERANCE) throw new IllegalArgumentException("Clock rollback");
        highWater=Math.max(wall,floor);
        return highWater;
    }
    public synchronized TeleautoLicenseVerifier.Error check(long wall,long elapsed) {
        try {return time(wall,elapsed)>=expiresAt?TeleautoLicenseVerifier.Error.LICENSE_EXPIRED:TeleautoLicenseVerifier.Error.VALID;}
        catch(IllegalArgumentException e){return TeleautoLicenseVerifier.Error.CLOCK_ROLLBACK;}
    }
}
