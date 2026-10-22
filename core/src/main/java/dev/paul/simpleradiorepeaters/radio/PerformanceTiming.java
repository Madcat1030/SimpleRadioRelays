package dev.paul.simpleradiorepeaters.radio;

import java.util.LinkedHashMap;
import java.util.Map;

/** Fixed-memory elapsed-time distribution. Percentiles are explicitly bucket upper bounds. */
public final class PerformanceTiming {
    private final long[] buckets = new long[64];
    private long count, total, max;
    public void add(long nanos) {
        nanos = Math.max(0, nanos); count++; total += nanos; max = Math.max(max, nanos);
        buckets[nanos == 0 ? 0 : 64 - Long.numberOfLeadingZeros(nanos)]++;
    }
    public long count() { return count; }
    public long total() { return total; }
    public long upper(double quantile) {
        if (count == 0) return 0;
        long target = Math.max(1, (long) Math.ceil(count * Math.max(0, Math.min(1, quantile)))), seen = 0;
        for (int i = 0; i < buckets.length; i++) if ((seen += buckets[i]) >= target)
            return i == 63 ? Long.MAX_VALUE : (1L << i) - 1;
        return max;
    }
    public Map<String, Object> report() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("samples", count); out.put("total_ms", total / 1e6);
        out.put("mean_us", count == 0 ? 0 : total / (count * 1e3)); out.put("max_us", max / 1e3);
        out.put("p50_upper_us", upper(.5) / 1e3); out.put("p95_upper_us", upper(.95) / 1e3);
        out.put("p99_upper_us", upper(.99) / 1e3); return out;
    }
}
