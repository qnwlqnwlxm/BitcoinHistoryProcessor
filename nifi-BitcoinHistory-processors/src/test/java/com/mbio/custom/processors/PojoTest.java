package com.mbio.custom.processors;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import com.mbio.custom.processors.model.BitcoinHistoryModel;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PojoTest {

    Logger logger = LoggerFactory.getLogger(PojoTest.class);

    private static final String TEST_FILE = "test.csv";
    private static final String BAD_TEST_FILE = "bad.csv";

    @Test
    public void createPojoTest() throws Exception {
        try (InputStream in = ClassLoader.getSystemResourceAsStream(TEST_FILE);
                final BufferedReader reader = new BufferedReader(new InputStreamReader(in));) {
            final Iterable<CSVRecord> records = CSVFormat.RFC4180.withFirstRecordAsHeader().parse(reader);
            for (final CSVRecord record : records) {
                if (isValid(record)) {
                    final BitcoinHistoryModel history = createModel(record);
                    logger.debug("history : \n {}", new Object[] {history});
                }   
            }
        } catch (Exception e) {
            logger.debug("Error Processing input", e);
        }
    }

    @Test(expected = NumberFormatException.class)
    public void createPojoWithBadInputTest() throws Exception {
        try (InputStream in = ClassLoader.getSystemResourceAsStream(BAD_TEST_FILE);
                final BufferedReader reader = new BufferedReader(new InputStreamReader(in));) {
            final Iterable<CSVRecord> records = CSVFormat.RFC4180.withFirstRecordAsHeader().parse(reader);
            for (final CSVRecord record : records) {
                if (isValid(record)) {
                    final BitcoinHistoryModel history = createModel(record);
                    logger.debug("history : \n {}", new Object[] {history});
                } 
            }
        }
    }

    private BitcoinHistoryModel createModel(final CSVRecord record) {
        final BitcoinHistoryModel history = new BitcoinHistoryModel();

        final long unixTimestamp = Long.parseLong(record.get("Timestamp"));
        final Instant instant = Instant.ofEpochSecond(unixTimestamp);
        final ZonedDateTime timestamp = ZonedDateTime.ofInstant(instant, ZoneId.systemDefault());

        final double open = Double.parseDouble(record.get("Open"));
        final double high = Double.parseDouble(record.get("High"));
        final double low = Double.parseDouble(record.get("Low"));
        final double close = Double.parseDouble(record.get("Close"));
        final double btcVolume = Double.parseDouble(record.get("Volume_(BTC)"));
        final double usdVolume = Double.parseDouble(record.get("Volume_(Currency)"));
        final double weightedPrice = Double.parseDouble(record.get("Weighted_Price"));

        history.setTimestamp(timestamp);
        history.setOpen(open);
        history.setHigh(high);
        history.setLow(low);
        history.setClose(close);
        history.setBtcVolume(btcVolume);
        history.setUsdVolume(usdVolume);
        history.setWeightedPrice(weightedPrice);

        return history;
    }

    private boolean isValid(final CSVRecord record) {
        return !record.toString().contains("NaN");
    }

}
