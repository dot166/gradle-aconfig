package io.github.dot166.aconfig;

public class Flag {
    private String mKey;
    private String mValue;
    private boolean mIsWritable;

    public Flag(String key, String value, boolean isWritable) {
        this.mKey = key;
        this.mValue = value;
        this.mIsWritable = isWritable;
    }

    public String getKey() {
        return mKey;
    }

    public String getValue() {
        return mValue;
    }

    public boolean isWritable() {
        return mIsWritable;
    }
}
