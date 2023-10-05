package cbor;

import lombok.Getter;

/**
 * Major type
 * The initial byte of each encoded data item contains both information about the major type (the high-order 3 bits,
 * described in <a href="https://www.rfc-editor.org/rfc/rfc8949.html#majortypes">Section 3.1</a>) and additional information (the low-order 5 bits).
 */
@Getter
public class MajorType {
    public static final int TYPE_UNSIGNED_INTEGER = 0b000;
    public static final int TYPE_NEGATIVE_INTEGER = 0b001;
    public static final int TYPE_BYTE_STRING = 0b010;
    public static final int TYPE_TEXT_STRING = 0b011;
    public static final int TYPE_ARRAY = 0b100;
    public static final int TYPE_MAP = 0b101;
    public static final int TYPE_TAG = 0b110;
    public static final int TYPE_FLOATING_VALUE_OR_SIMPLE_VALUE = 0b111;

    private static final String ERROR_NOT_WELL_FORMED_ARGUMENT = "The encoded item is not well-formed.";
    private static final String ERROR_INVALID_MAJOR_TYPE_VALUE = "Invalid major type value: ";
    private static final String ERROR_INVALID_ADDITIONAL_INFORMATION_VALUE = "Invalid additional information value: ";

    private final int majorType;
    private final int additionalInformation;

    MajorType(int majorType, int additionalInformation) {
        this.majorType = majorType;
        this.additionalInformation = additionalInformation;
    }

    /**
     * Convert a given byte value to a {@link MajorType} value.
     *
     * @param i the input integer (8-bit byte) to convert into a {@link MajorType} instance.
     * @return a {@link MajorType} instance.
     */
    public static MajorType valueOf(int i) {
        return new MajorType((i & 0xff) >>> 5, i & 0x1f);
    }
}
