package com.aem.ai.scanner.services;


import com.aem.ai.scanner.model.TradeAnalysis;

public interface GeminiService {

    /**
     * Perform Gemini-based fundamental analysis on a given signal message.
     *
     * @return TradeAnalysis object containing analysis result
     * @throws Exception if Gemini API call or parsing fails
     */
    TradeAnalysis tradeSignalAnalysis(String signalMsg) throws Exception;
    String todayNewsUpdates() throws Exception;
    String analyzePortfolio(String portfolioJson) throws Exception;
}
