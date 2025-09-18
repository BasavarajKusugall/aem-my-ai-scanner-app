package com.aem.ai.scanner.model;


public class PivotLevels {
    private final double pivot;
    private final double r1, r2, r3;
    private final double s1, s2, s3;

    public PivotLevels(double pivot, double r1, double r2, double r3, double s1, double s2, double s3) {
        this.pivot = pivot;
        this.r1 = r1;
        this.r2 = r2;
        this.r3 = r3;
        this.s1 = s1;
        this.s2 = s2;
        this.s3 = s3;
    }
    public PivotLevels(double high, double low, double close) {
        pivot = (high + low + close) / 3.0;
        r1 = (2 * pivot) - low;
        s1 = (2 * pivot) - high;
        r2 = pivot + (high - low);
        s2 = pivot - (high - low);
        r3 = high + 2 * (pivot - low);
        s3 = low - 2 * (high - pivot);
    }

    public double getPivot() { return pivot; }
    public double getR1() { return r1; }
    public double getR2() { return r2; }
    public double getR3() { return r3; }
    public double getS1() { return s1; }
    public double getS2() { return s2; }
    public double getS3() { return s3; }

    @Override
    public String toString() {
        return String.format("Pivot=%.2f, R1=%.2f, R2=%.2f, R3=%.2f, S1=%.2f, S2=%.2f, S3=%.2f",
                pivot, r1, r2, r3, s1, s2, s3);
    }
}
