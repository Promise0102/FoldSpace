package com.mkf.foldspace;

/** Private, narrowly scoped Binder contract; no arbitrary shell execution endpoint. */
public final class HostProtocol {
    public static final String TOKEN="com.mkf.foldspace.UpperHost.v1";
    public static final int SERVICE_VERSION=9;
    public static final int CREATE=1, LAUNCH=2, TOUCH=3, RESIZE=4, RELEASE=5, STATUS=6, SEEK=7, KEY=8,CURRENT_APP=9;
    private HostProtocol() {}
}
