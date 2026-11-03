package com.openipc.wfbngrtl8812;

/** Snapshot of WFB-NG receive statistics. Kept for JNI construction. */
public final class WfbNGStats {
    public final int count_p_all;
    public final int count_p_dec_err;
    public final int count_p_dec_ok;
    public final int count_p_fec_recovered;
    public final int count_p_lost;
    public final int count_p_bad;
    public final int count_p_override;
    public final int count_p_outgoing;
    public final int avg_rssi;

    public WfbNGStats(int all, int decErr, int decOk, int fecRecovered, int lost,
                      int bad, int override, int outgoing, int rssi) {
        count_p_all = all;
        count_p_dec_err = decErr;
        count_p_dec_ok = decOk;
        count_p_fec_recovered = fecRecovered;
        count_p_lost = lost;
        count_p_bad = bad;
        count_p_override = override;
        count_p_outgoing = outgoing;
        avg_rssi = rssi;
    }
}
