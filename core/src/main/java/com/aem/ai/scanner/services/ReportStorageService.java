package com.aem.ai.scanner.services;

public interface ReportStorageService {
    void storeReport(String basePath,
                     String fileName,
                     String content,
                     String mimeType) throws Exception;
}
