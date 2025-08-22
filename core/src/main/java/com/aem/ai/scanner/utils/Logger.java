package com.aem.ai.scanner.utils;


public class Logger {
    public static final String RESET="\033[0m", RED="\033[31m", GREEN="\033[32m", YELLOW="\033[33m";

    public static void info(String msg){ System.out.println(GREEN+"INFO: "+msg+RESET); }
    public static void warn(String msg){ System.out.println(YELLOW+"WARN: "+msg+RESET); }
    public static void error(String msg){ System.out.println(RED+"ERROR: "+msg+RESET); }
}
