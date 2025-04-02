package io.github.dot166.libaconfig;

public class writableFlag {
    private String mKey;
    private boolean mDefVal;
    public writableFlag(String key, boolean defVal) {
        mKey = key;
        mDefVal = defVal;
    }

    public boolean getFlagValue() {
        return mDefVal; // temp until i can get a normal jvm config implementation TODO: use a proper implementation
    }
}