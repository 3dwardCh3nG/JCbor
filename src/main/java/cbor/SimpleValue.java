package cbor;

import lombok.Getter;

@Getter
public class SimpleValue {
    public static final SimpleValue FALSE = new SimpleValue(20);
    public static final SimpleValue TRUE = new SimpleValue(21);
    public static final SimpleValue NULL = new SimpleValue(22);
    public static final SimpleValue UNDEFINED = new SimpleValue(23);

    private final int majorType;
    private final int additionalInformation;

    public SimpleValue(int additionalInformation) {
        this.majorType = 7;
        this.additionalInformation = additionalInformation;
    }

    public static SimpleValue valueOf(int i) {
        return switch (i) {
            case 20 -> FALSE;
            case 21 -> TRUE;
            case 22 -> NULL;
            case 23 -> UNDEFINED;
            default -> null;
        };
    }
}
