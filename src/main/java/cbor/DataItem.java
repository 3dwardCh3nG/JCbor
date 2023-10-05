package cbor;

import cbor.exceptions.InvalidAdditionalInformationException;
import cbor.exceptions.InvalidDataItemException;
import cbor.exceptions.InvalidMajorTypeException;
import lombok.Getter;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static cbor.MajorType.*;

@Getter
public class DataItem {
    private static final String ERROR_NOT_WELL_FORMED_ARGUMENT = "The encoded item is not well-formed.";
    private static final String ERROR_NO_ARGUMENT_VALUE_DERIVED = "No argument value is derived.";
    private static final byte BREAK_STOP_CODE = (byte) 0xFF;
    private static final byte BYTE_0 = (byte) 0x00;

    private final MajorType majorType;
    private byte[] argument;
    private Object value;

    public DataItem(MajorType majorType, byte[] argument, Object value) {
        this.majorType = majorType;
        this.argument = argument;
        this.value = value;
    }

    public static DataItem getNext(InputStream is, MajorType majorType) throws IOException {
        if (is == null || majorType == null) return null;
        if (majorType.getMajorType() == TYPE_UNSIGNED_INTEGER) {
            byte[] argument = getMajorTypeArgumentValue(is, majorType);
            return new DataItem(majorType,
                    getMajorTypeArgumentValue(is, majorType),
                    getUnsignedIntegerValue(argument)
            );
        } else if (majorType.getMajorType() == TYPE_NEGATIVE_INTEGER) {
            byte[] argument = getMajorTypeArgumentValue(is, majorType);
            return new DataItem(majorType,
                    getMajorTypeArgumentValue(is, majorType),
                    getNegativeIntegerValue(argument)
            );
        } else if (majorType.getMajorType() == TYPE_BYTE_STRING) {
            return getNextByteString(is, majorType);
        } else if (majorType.getMajorType() == MajorType.TYPE_TEXT_STRING) {
            return getNextTextString(is, majorType);
        } else if (majorType.getMajorType() == TYPE_ARRAY) {
            return getNextArray(is, majorType);
        } else if (majorType.getMajorType() == TYPE_MAP) {
            return getNextMap(is, majorType);
        } else if (majorType.getMajorType() == TYPE_TAG) {
            return getNextTag(is, majorType);
        } else if (majorType.getMajorType() == TYPE_FLOATING_VALUE_OR_SIMPLE_VALUE) {
            return getNextFloatPointValueOrSimpleValue(is, majorType);
        }
        return null;
    }

    private static DataItem getNextByteString(InputStream is, MajorType majorType) throws IOException {
        int additionalInformation = majorType.getAdditionalInformation();
        byte[] argument = getMajorTypeArgumentValue(is, majorType);
        byte[] value;
        if (additionalInformation == 31) {
            value = getIndefiniteByteStringValue(is);
        } else {
            value = getByteStringValue(argument, is);
        }
        return new DataItem(majorType, argument, value);
    }

    private static DataItem getNextTextString(InputStream is, MajorType majorType) throws IOException {
        int additionalInformation = majorType.getAdditionalInformation();
        byte[] argument = getMajorTypeArgumentValue(is, majorType);
        String value;
        if (additionalInformation == 31) {
            value = getIndefiniteTextStringValue(is);
        } else {
            value = getTextStringValue(argument, is);
        }
        return new DataItem(majorType, argument, value);
    }

    private static DataItem getNextArray(InputStream is, MajorType majorType) throws IOException {
        int additionalInformation = majorType.getAdditionalInformation();
        byte[] argument = getMajorTypeArgumentValue(is, majorType);
        DataItem[] value;
        if (additionalInformation == 31) {
            value = getIndefiniteArrayValue(is);
        } else {
            value = getArrayValue(argument, is);
        }
        return new DataItem(majorType, argument, value);
    }

    private static DataItem getNextMap(InputStream is, MajorType majorType) throws IOException {
        int additionalInformation = majorType.getAdditionalInformation();
        byte[] argument = getMajorTypeArgumentValue(is, majorType);
        Map<DataItem, DataItem> value;
        if (additionalInformation == 31) {
            value = getIndefiniteMapValue(is);
        } else {
            value = getMapValue(argument, is);
        }
        return new DataItem(majorType, argument, value);
    }

    private static DataItem getNextTag(InputStream is, MajorType majorType) throws IOException {
        byte[] argument = getMajorTypeArgumentValue(is, majorType);
        int tagNumber = (int) getUnsignedIntegerValue(argument);
        DataItem tagContent = getTagContentValue(is, tagNumber);
        return new DataItem(majorType, argument, tagContent);
    }

    private static DataItem getNextFloatPointValueOrSimpleValue(InputStream is, MajorType majorType) throws IOException {
        int additionalInformation = majorType.getAdditionalInformation();
        if (additionalInformation == 20) {
            return new DataItem(majorType,
                    getMajorTypeArgumentValue(is, majorType),
                    false
            );
        } else if (additionalInformation == 21) {
            return new DataItem(majorType,
                    getMajorTypeArgumentValue(is, majorType),
                    true
            );
        } else if (additionalInformation == 22 || additionalInformation == 23) {
            return new DataItem(majorType,
                    getMajorTypeArgumentValue(is, majorType),
                    null
            );
        } else if (additionalInformation == 25 || additionalInformation == 26 || additionalInformation == 27) {
            return new DataItem(majorType,
                    getMajorTypeArgumentValue(is, majorType),
                    getFloatingPointValue(is, majorType)
            );
        }
        return null;
    }

    private static byte[] getMajorTypeArgumentValue(InputStream is, MajorType majorType) throws IOException {
        return switch (majorType.getMajorType()) {
            case TYPE_UNSIGNED_INTEGER, TYPE_NEGATIVE_INTEGER, TYPE_TAG -> readArgumentValue(is, majorType);
            case TYPE_BYTE_STRING, TYPE_TEXT_STRING, TYPE_ARRAY, TYPE_MAP -> readArgumentValueForMajorType2To5(is, majorType);
            case TYPE_FLOATING_VALUE_OR_SIMPLE_VALUE -> readArgumentValueForMajorType7(is, majorType);
            default -> throw new InvalidMajorTypeException("Invalid major type " + majorType.getMajorType() + ".");
        };
    }

    /**
     * <p>
     * Less than 24:
     * The argument's value is the value of the additional information.
     * </p>
     * <p>
     * 24, 25, 26, or 27:
     * The argument's value is held in the following 1, 2, 4, or 8 bytes, respectively, in network byte order. For major type 7 and additional information value 25, 26, 27, these bytes are not used as an integer argument, but as a floating-point value (see Section 3.3).
     * </p>
     * <p>
     * 28, 29, 30:
     * These values are reserved for future additions to the CBOR format. In the present version of CBOR, the encoded item is not well-formed.
     * </p>
     * <p>
     * 31:
     * No argument value is derived. If the major type is 0, 1, or 6, the encoded item is not well-formed. For major types 2 to 5, the item's length is indefinite, and for major type 7, the byte does not constitute a data item at all but terminates an indefinite-length item; all are described in Section 3.2.
     * </p>
     *
     * @param is        The input stream to read from.
     * @param majorType The major type of the data item.
     * @return The argument value of the data item.
     * @throws IOException
     */
    private static byte[] readArgumentValue(InputStream is, MajorType majorType) throws IOException {
        int additionalInformation = majorType.getAdditionalInformation();
        if (additionalInformation < 24) {
            return new byte[]{(byte) additionalInformation};
        } else if (additionalInformation == 24) {
            is.readNBytes(1);
        } else if (additionalInformation == 25) {
            is.readNBytes(2);
        } else if (additionalInformation == 26) {
            is.readNBytes(4);
        } else if (additionalInformation == 27) {
            is.readNBytes(8);
        } else if (additionalInformation == 28 || additionalInformation == 29 || additionalInformation == 30) {
            throw new InvalidAdditionalInformationException(ERROR_NOT_WELL_FORMED_ARGUMENT);
        } else if (additionalInformation == 31) {
            throw new InvalidAdditionalInformationException(ERROR_NO_ARGUMENT_VALUE_DERIVED);
        }
        throw new InvalidAdditionalInformationException(ERROR_NOT_WELL_FORMED_ARGUMENT);
    }

    private static byte[] readArgumentValueForMajorType2To5(InputStream is, MajorType majorType) throws IOException {
        int additionalInformation = majorType.getAdditionalInformation();
        if (additionalInformation == 31) {
            return new byte[]{};
        } else {
            return readArgumentValue(is, majorType);
        }
    }

    private static byte[] readArgumentValueForMajorType7(InputStream is, MajorType majorType) throws IOException {
        int additionalInformation = majorType.getAdditionalInformation();
        if (additionalInformation == 31) {
            return new byte[]{};
        } else {
            return readArgumentValue(is, majorType);
        }
    }

    private static long getUnsignedIntegerValue(byte[] argument) {
        long bits = 0L;
        for (int i = 0; i < argument.length; i++) {
            bits |= (argument[i] & 0xFFL) << (8 * (argument.length - i - 1));
        }
        return bits;
    }

    private static long getUnsignedIntegerValue2(byte[] argument) {
        if (argument.length < 8) {
            byte[] newArray = new byte[8];
            System.arraycopy(argument, 0, newArray, 8 - argument.length, argument.length);
            return ByteBuffer.wrap(newArray).getLong();
        }
        return ByteBuffer.wrap(argument).getLong();
    }

    private static long getNegativeIntegerValue(byte[] argument) {
        return -1 - getUnsignedIntegerValue(argument);
    }

    private static byte[] getByteStringValue(byte[] argument, InputStream is) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        long byteStringContentLength = getUnsignedIntegerValue(argument);
        long remaining = byteStringContentLength;
        while(remaining > 0) {
            byte[] nBytes = is.readNBytes(remaining > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) remaining);
            os.write(nBytes);
            remaining -= nBytes.length;
        }
        byte[] value = os.toByteArray();
        if (value.length == byteStringContentLength) return value;
        throw new EOFException();
    }

    private static byte[] getIndefiniteByteStringValue(InputStream is) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        int nRead;
        while ((nRead = is.read()) != BREAK_STOP_CODE) {
            MajorType majorType = MajorType.valueOf(nRead);
            if (majorType.getMajorType() == 0b010) {
                try {
                    byte[] argument = getMajorTypeArgumentValue(is, majorType);
                    byte[] chunk = getByteStringValue(argument, is);
                    os.write(chunk);
                } catch (EOFException e) {
                    break;
                }
            }
        }
        return os.toByteArray();
    }

    private static String getTextStringValue(byte[] argument, InputStream is) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        long byteStringContentLength = getUnsignedIntegerValue(argument);
        long remaining = byteStringContentLength;
        while(remaining > 0) {
            byte[] nBytes = is.readNBytes(remaining > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) remaining);
            os.write(nBytes);
            remaining -= nBytes.length;
        }
        byte[] data = os.toByteArray();
        if (data.length == byteStringContentLength) return new String(data, StandardCharsets.UTF_8);
        throw new EOFException();
    }

    private static String getIndefiniteTextStringValue(InputStream is) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        int nRead;
        while ((nRead = is.read()) != BREAK_STOP_CODE) {
            MajorType majorType = MajorType.valueOf(nRead);
            if (majorType.getMajorType() == 0b011) {
                try {
                    byte[] argument = getMajorTypeArgumentValue(is, majorType);
                    byte[] chunk = getByteStringValue(argument, is);
                    os.write(chunk);
                } catch (EOFException e) {
                    throw new InvalidDataItemException(e);
                }
            }
        }
        return os.toString(StandardCharsets.UTF_8);
    }

    private static DataItem[] getArrayValue(byte[] argument, InputStream is) throws IOException {
        long arrayLength = getUnsignedIntegerValue(argument);
        DataItem[] dataItems = new DataItem[(int) arrayLength];
        int itemIndex = 0;
        int nRead;
        while(itemIndex < arrayLength && (nRead = is.read()) != -1) {
            MajorType majorType = MajorType.valueOf(nRead);
            DataItem dataItem = getNext(is, majorType);
            dataItems[itemIndex] = dataItem;
            itemIndex++;
        }
        return dataItems;
    }

    private static DataItem[] getIndefiniteArrayValue(InputStream is) throws IOException {
        List<DataItem> dataItems = new ArrayList<>();
        int nRead;
        while((nRead = is.read()) != BREAK_STOP_CODE) {
            MajorType majorType = MajorType.valueOf(nRead);
            DataItem dataItem = getNext(is, majorType);
            dataItems.add(dataItem);
        }
        return dataItems.toArray(new DataItem[0]);
    }

    private static Map<DataItem, DataItem> getMapValue(byte[] argument, InputStream is) throws IOException {
        long mapLength = getUnsignedIntegerValue(argument);
        Map<DataItem, DataItem> dataItems = new HashMap<>();
        int itemIndex = 0;
        int nRead;
        while(itemIndex < mapLength && (nRead = is.read()) != -1) {
            MajorType majorType = MajorType.valueOf(nRead);
            DataItem key = getNext(is, majorType);
            DataItem value = getNext(is, majorType);
            dataItems.put(key, value);
            itemIndex++;
        }
        return dataItems;
    }

    private static Map<DataItem, DataItem> getIndefiniteMapValue(InputStream is) throws IOException {
        Map<DataItem, DataItem> dataItems = new HashMap<>();
        int nRead;
        while((nRead = is.read()) != BREAK_STOP_CODE) {
            MajorType majorType = MajorType.valueOf(nRead);
            DataItem key = getNext(is, majorType);
            DataItem value = getNext(is, majorType);
            dataItems.put(key, value);
        }
        return dataItems;
    }

    private static DataItem getTagContentValue(InputStream is, int tagNumber) throws IOException {
        int nRead;
        nRead = is.read();
        MajorType tagContentMajorType = MajorType.valueOf(nRead);
        byte[] argument = getMajorTypeArgumentValue(is, tagContentMajorType);
        try {
            Object value = switch(tagNumber) {
                case 0 -> getTagStandardDateTimeString(is, tagContentMajorType);
                case 1 -> getTagEpochBasedDateTimeNumber(is, tagContentMajorType);
                case 2 -> getTagUnsignedBignum(is, tagContentMajorType);
                case 3 -> getTagNegativeBignum(is, tagContentMajorType);
//                case 4 -> getTagDecimalFraction(is, tagContentMajorType);
//                case 5 -> getTagBigfloat(is, tagContentMajorType);
//                case 21 -> getTagBase64URL(is, tagContentMajorType);
//                case 22 -> getTagBase64(is, tagContentMajorType);
//                case 23 -> getTagBase16(is, tagContentMajorType);
//                case 24 -> getTagEncodedCBORData(is, tagContentMajorType);
//                case 32 -> getTagURI(is, tagContentMajorType);
//                case 33 -> getTagBase64URLTextString(is, tagContentMajorType);
//                case 34 -> getTagBase64TextString(is, tagContentMajorType);
//                case 36 -> getTagMIMEMessage(is, tagContentMajorType);
//                case 55799 -> getTagSelfDescribeCBOR(is, tagContentMajorType);
                default -> new IllegalArgumentException("Invalid tag number " + tagNumber + ".");
            };
            return new DataItem(tagContentMajorType, argument, value);
        } catch(InvalidDataItemException e) {
            throw new InvalidDataItemException("Invalid tag content for the tag number " + tagNumber + ".");
        }
    }

    /**
     * Tag number 0 contains a text string in the standard format described by the date-time production in [RFC3339], as refined by Section 3.3 of [RFC4287], representing the point in time described there. A nested item of another type or a text string that doesn't match the format described in [RFC4287] is invalid.
     * @param is The input stream to read from.
     * @param tagContentMajorType The major type of the tag content.
     * @return the standard date/time string.
     * @throws IOException
     */
    private static String getTagStandardDateTimeString(InputStream is, MajorType tagContentMajorType) throws IOException {
        byte[] argument = getMajorTypeArgumentValue(is, tagContentMajorType);
        if (argument.length != 0) {
            return getTextStringValue(argument, is);
        }
        throw  new InvalidDataItemException("Invalid tag content for the tag number 0 - Standard Date/Time String.");
    }

    /**
     * <p>
     * Tag number 1 contains a numerical value counting the number of seconds from 1970-01-01T00:00Z in UTC time to the represented point in civil time.
     * </p>
     * <p>
     * The tag content MUST be an unsigned or negative integer (major types 0 and 1) or a floating-point number (major type 7 with additional information 25, 26, or 27). Other contained types are invalid.
     * </p>
     * @param is The input stream to read from.
     * @param tagContentMajorType The major type of the tag content.
     * @return the epoch-based date/time number (long, float or double).
     * @throws IOException
     */
    private static Object getTagEpochBasedDateTimeNumber(InputStream is, MajorType tagContentMajorType) throws IOException {
        byte[] argument = getMajorTypeArgumentValue(is, tagContentMajorType);
        switch (tagContentMajorType.getMajorType()) {
            case TYPE_UNSIGNED_INTEGER:
                return getUnsignedIntegerValue(argument);
            case TYPE_NEGATIVE_INTEGER:
                return getNegativeIntegerValue(argument);
            case TYPE_FLOATING_VALUE_OR_SIMPLE_VALUE:
                int additionalInformation = tagContentMajorType.getAdditionalInformation();
                if (additionalInformation >= 25 && additionalInformation <= 27) {
                    return getFloatingPointValue(is, tagContentMajorType);
                }
                throw new InvalidDataItemException(
                        "Invalid tag content additionalInformation for the tag number 1 - Epoch-based Date/Time: " +
                                additionalInformation + "."
                );
            default:
                throw new InvalidDataItemException("Invalid tag content major type the tag number 1 - Epoch-based Date/Time: " +
                        tagContentMajorType.getMajorType() + "."
                );
        }
    }

    private static Object getFloatingPointValue(InputStream is, MajorType tagContentMajorType) throws IOException {
        int additionalInformation = tagContentMajorType.getAdditionalInformation();
        byte[] data;
        if (additionalInformation == 25) {
            data = is.readNBytes(2);
            return HalfPrecisionFloatPoint.toFloat(data);
        } else if (additionalInformation == 26) {
            data = is.readNBytes(4);
            int bits = 0;
            for (int i = 0; i < 4; i++) {
                bits |= (data[i] & 0xFF) << (8 * (4 - i - 1));
            }
            return Float.intBitsToFloat(bits);
        } else if (additionalInformation == 27) {
            data = is.readNBytes(8);
            long bits = 0L;
            for (int i = 0; i < 8; i++) {
                bits |= (data[i] & 0xFFL) << (8 * (8 - i - 1));
            }
            return Double.longBitsToDouble(bits);
        } else {
            throw new InvalidAdditionalInformationException(ERROR_NOT_WELL_FORMED_ARGUMENT);
        }
    }

    private static BigInteger getTagUnsignedBignum(InputStream is, MajorType tagContentMajorType) throws IOException {
        byte[] argument = getMajorTypeArgumentValue(is, tagContentMajorType);
        long tagContentSize = getUnsignedIntegerValue(argument);
        byte[] value = getTagUnsignedBignumValue(is, tagContentSize);
        boolean isZero = IntStream.range(0, value.length).map(i -> value[i]).allMatch(b -> b == BYTE_0);
        if (value.length == tagContentSize) return new BigInteger(isZero ? 0 : 1, value);
        throw new EOFException();
    }

    private static byte[] getTagUnsignedBignumValue(InputStream is, long tagContentSize) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        long remaining = tagContentSize;
        byte[] nBytes;
        while(remaining > 0) {
            nBytes = is.readNBytes(remaining > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) remaining);
            os.write(nBytes);
            remaining -= nBytes.length;
        }
        return os.toByteArray();
    }

    private static BigInteger getTagNegativeBignum(InputStream is, MajorType tagContentMajorType) throws IOException {
        byte[] argument = getMajorTypeArgumentValue(is, tagContentMajorType);
        long tagContentSize = getUnsignedIntegerValue(argument);
        byte[] value = getTagUnsignedBignumValue(is, tagContentSize);
        boolean isZero = IntStream.range(0, value.length).map(i -> value[i]).allMatch(b -> b == BYTE_0);
        if (value.length == tagContentSize) return new BigInteger(isZero ? 0 : -1, value);
        throw new EOFException();
    }
}
