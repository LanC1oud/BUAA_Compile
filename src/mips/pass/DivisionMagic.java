package mips.pass;

import java.math.BigInteger;

/** Exact 32-bit reciprocal parameters; all wide arithmetic happens at compile time. */
final class DivisionMagic {
    record Signed(int multiplier, int shift) { }
    record Unsigned(int multiplier, int shift, boolean add) { }
    static Signed signed(int divisor) {
        long ad = Math.abs((long)divisor), two31 = 1L << 31;
        long t = two31 + (divisor < 0 ? 1 : 0), anc = t - 1 - t % ad;
        long q1 = two31 / anc, r1 = two31 % anc, q2 = two31 / ad, r2 = two31 % ad;
        int p = 31;
        long delta;
        do {
            p++; q1 *= 2; r1 *= 2; if (r1 >= anc) { q1++; r1 -= anc; }
            q2 *= 2; r2 *= 2; if (r2 >= ad) { q2++; r2 -= ad; }
            delta = ad - r2;
        } while (q1 < delta || q1 == delta && r1 == 0);
        long multiplier = q2 + 1;
        return new Signed((int)(divisor < 0 ? -multiplier : multiplier), p - 32);
    }
    static Unsigned unsigned(int divisor) {
        long d = Integer.toUnsignedLong(divisor);
        int log = 63 - Long.numberOfLeadingZeros(d);
        BigInteger[] qr = BigInteger.ONE.shiftLeft(32 + log).divideAndRemainder(BigInteger.valueOf(d));
        long proposed = qr[0].longValue(), remainder = qr[1].longValue();
        boolean add = d - remainder >= (1L << log);
        if (add) { proposed *= 2; if (remainder * 2 >= d) proposed++; }
        return new Unsigned((int)(proposed + 1), log, add);
    }
}
