package cbor;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.ArrayList;
import java.util.List;

public class Decoder {
    private final PushbackInputStream is;

    public Decoder(InputStream inputStream) {
        if (inputStream == null) {
            throw new IllegalArgumentException("InputStream cannot be null");
        }
        this.is = new PushbackInputStream(inputStream);
    }

    public List<DataItem> decode() {
        List<DataItem> dataItems = new ArrayList<>();
        while(true) {
            try {
                MajorType majorType = this.peekMajorType();
                DataItem dataItem = DataItem.getNext(this.is, majorType);
                if (dataItem == null) {
                    break;
                }
                dataItems.add(dataItem);
            } catch (IOException e) {
                e.printStackTrace();
                break;
            }
        }
        return dataItems;
    }

    public MajorType peekMajorType() throws IOException {
        int i = this.is.read();
        if (i < 0) return null;
        this.is.unread(i);
        return MajorType.valueOf(i);
    }

    public MajorType readMajorType() throws IOException {
        int i = this.is.read();
        if (i < 0) return null;
        return MajorType.valueOf(i);
    }
}
