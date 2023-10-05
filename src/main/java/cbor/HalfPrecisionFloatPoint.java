package cbor;

/**
 * <p>The IEEE 754 standard specifies an fp16 as having the following format:</p>
 * <ul>
 * <li>Sign bit: 1 bit</li>
 * <li>Exponent width: 5 bits</li>
 * <li>Significand: 10 bits</li>
 * </ul>
 *
 * <p>The format is laid out as follows:</p>
 * <pre>
 * 1   11111   1111111111
 * ^   --^--   -----^----
 * sign  |          |_______ significand
 *       |
 *       -- exponent
 * </pre>
 */
public class HalfPrecisionFloatPoint {
    public static final int SIGN_MASK = 0x8000;
    public static final int EXPONENT_SHIFT = 10;
    public static final int SHIFTED_EXPONENT_MASK = 0x1f;
    public static final int SIGNIFICAND_MASK = 0x3ff;
    public static final int EXPONENT_BIAS = 15;
    private static final int FP32_EXPONENT_SHIFT = 23;
    private static final int FP32_EXPONENT_BIAS = 127;
    private static final int FP32_QNAN_MASK = 0x400000;
    private static final int FP32_DENORMAL_MAGIC = 126 << 23;
    private static final float FP32_DENORMAL_FLOAT = Float.intBitsToFloat(FP32_DENORMAL_MAGIC);

    /**
     * <p>Converts the specified half-precision float value into a
     * single-precision float value. The following special cases are handled:</p>
     *
     * @param h The half-precision float value to convert to single-precision
     * @return A normalized single-precision float value
     * @hide
     */
    public static float toFloat(short h) {
        int bits = h & 0xffff;
        int s = bits & SIGN_MASK;
        int e = (bits >>> EXPONENT_SHIFT) & SHIFTED_EXPONENT_MASK;
        int m = (bits) & SIGNIFICAND_MASK;
        int outE = 0;
        int outM = 0;
        if (e == 0) { // Denormal or 0
            if (m != 0) {
                // Convert denorm fp16 into normalized fp32
                float o = Float.intBitsToFloat(FP32_DENORMAL_MAGIC + m);
                o -= FP32_DENORMAL_FLOAT;
                return s == 0 ? o : -o;
            }
        } else {
            outM = m << 13;
            if (e == 0x1f) { // Infinite or NaN
                outE = 0xff;
                if (outM != 0) { // SNaNs are quieted
                    outM |= FP32_QNAN_MASK;
                }
            } else {
                outE = e - EXPONENT_BIAS + FP32_EXPONENT_BIAS;
            }
        }
        int out = (s << 16) | (outE << FP32_EXPONENT_SHIFT) | outM;
        return Float.intBitsToFloat(out);
    }

    public static float toFloat(byte[] bytes) {
        return toFloat((short) ((bytes[0] << 8) | (bytes[1] & 0xff)));
    }
}
