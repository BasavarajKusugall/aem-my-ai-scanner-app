package com.aem.ai.pm.dto;

public class BrokerException extends Exception {
    public final int httpStatus;
    public BrokerException(String msg, int status, Throwable cause){ super(msg,cause); this.httpStatus=status; }
    public BrokerException(String msg){ this(msg, -1, null); }
}
