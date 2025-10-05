package com.aem.ai.exception;
// ApiException
public class ApiException extends Exception {
    public ApiException(String msg) { super(msg); }
    public ApiException(String msg, Throwable cause) { super(msg, cause); }
}
