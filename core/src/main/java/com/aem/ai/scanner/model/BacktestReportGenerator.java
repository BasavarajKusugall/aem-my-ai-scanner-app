package com.aem.ai.scanner.model;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.reports.ReportGenerator;
import org.ta4j.core.reports.TradingStatement;

import java.io.IOException;
import java.io.StringWriter;
import java.time.Duration;
import java.util.List;

/**
 * Custom report generator for backtest results.
 */
public class BacktestReportGenerator implements ReportGenerator<String> {

    private final List<TradingStatement> tradingStatements;
    private final ObjectMapper mapper;

    public BacktestReportGenerator(List<TradingStatement> tradingStatements) {
        this.tradingStatements = tradingStatements;

        // configure mapper once
        this.mapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addSerializer(Duration.class, new JsonSerializer<Duration>() {
            @Override
            public void serialize(Duration value, JsonGenerator gen, SerializerProvider serializers)
                    throws IOException {
                // Convert Duration to readable string
                gen.writeString(value.toString()); // e.g. PT1H → “PT1H”
            }
        });

        mapper.registerModule(module);
    }

    @Override
    public String generate(Strategy strategy, TradingRecord tradingRecord, BarSeries barSeries) {
        return String.format("Report for %s with %d trades",
                strategy.getName(), tradingRecord.getTrades().size());
    }

    public String generateCsv() {
        StringWriter writer = new StringWriter();
        writer.append("Strategy,TotalProfit%,ProfitTrades,LossTrades\n");
        for (TradingStatement ts : tradingStatements) {
            writer.append(ts.getStrategy().getName()).append(",")
                    .append(ts.getPerformanceReport().getTotalProfitLossPercentage().toString()).append(",")
                    .append(ts.getPositionStatsReport().getProfitCount().toString()).append(",")
                    .append(ts.getPositionStatsReport().getLossCount().toString())
                    .append("\n");
        }
        return writer.toString();
    }

    public String generateJson() {
        try {
            return mapper
                    .writeValueAsString(tradingStatements);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate JSON", e);
        }
    }

    public String generateHtml() {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><head><title>Backtest Report</title></head><body>");
        sb.append("<h1>Backtest Report</h1>");
        sb.append("<table border='1' cellpadding='5' cellspacing='0'>");
        sb.append("<tr><th>Strategy</th><th>Total Profit %</th><th>Profit Trades</th><th>Loss Trades</th></tr>");
        for (TradingStatement ts : tradingStatements) {
            sb.append("<tr>")
                    .append("<td>").append(ts.getStrategy().getName()).append("</td>")
                    .append("<td>").append(ts.getPerformanceReport().getTotalProfitLossPercentage().toString()).append("</td>")
                    .append("<td>").append(ts.getPositionStatsReport().getProfitCount().toString()).append("</td>")
                    .append("<td>").append(ts.getPositionStatsReport().getLossCount().toString()).append("</td>")
                    .append("</tr>");
        }
        sb.append("</table>");
        sb.append("</body></html>");
        return sb.toString();
    }
}
