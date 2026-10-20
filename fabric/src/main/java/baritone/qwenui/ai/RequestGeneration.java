package baritone.qwenui.ai;

/** Client-thread ownership token: cancelled or old callbacks cannot mutate a newer conversation. */
public final class RequestGeneration {
    private long generation;
    public long begin(){return ++generation;}
    public void invalidate(){generation++;}
    public boolean accepts(long token){return token==generation;}
}
