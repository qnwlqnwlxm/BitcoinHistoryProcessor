/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mbio.custom.processors;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mbio.custom.processors.model.BitcoinHistoryModel;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.nifi.annotation.behavior.InputRequirement;
import org.apache.nifi.annotation.behavior.InputRequirement.Requirement;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.dbcp.DBCPService;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.flowfile.attributes.CoreAttributes;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.io.InputStreamCallback;
import org.apache.nifi.processor.io.OutputStreamCallback;

@Tags({ "Bitcoin", "JSON", "XML", "CSV" })
@CapabilityDescription("Process Bitcoin transactions into XML or JSON format or both")
@InputRequirement(Requirement.INPUT_REQUIRED)
public class BitcoinHistory extends AbstractProcessor {

    private enum Output {
        ALL, JSON, XML, DB
    };

    private static ObjectMapper mapper = new ObjectMapper();
    private AtomicReference<JAXBContext> jaxb = new AtomicReference<>();
    private AtomicReference<PreparedStatement> stmt = new AtomicReference<>();

    private AtomicReference<Output> output = new AtomicReference<>();

    @Override
    public Set<Relationship> getRelationships() {
        return ConfigUtil.getRelaltionships();
    }

    @Override
    public final List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return ConfigUtil.getProperties();
    }

    @OnScheduled
    public void onScheduled(final ProcessContext context) {
        final String output = context.getProperty(ConfigUtil.OUTPUT).getValue();
        this.output.set(Output.valueOf(output));

        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        try {
            jaxb.set(JAXBContext.newInstance(BitcoinHistoryModel.class));
        } catch (Exception e) {
            getLogger().error("Could not create JAXB context", e);
        }

        DBCPService dbcpService = context.getProperty(ConfigUtil.DS_PROP).asControllerService(DBCPService.class);
    try {
      final PreparedStatement stmt = dbcpService.getConnection().prepareStatement(
          "insert into bitcoin_history (history_time, open_price, high, low, close_price, btc_volume, usd_volume, weighted_price, rgs_dtm) values (?,?,?,?,?,?,?,?,?)");
      this.stmt.set(stmt);
    } catch (ProcessException | SQLException e) {
      getLogger().error("Could not create PreparedStatment", e);
    }
    }

    @Override
    public void onTrigger(final ProcessContext context, final ProcessSession session) throws ProcessException {
        FlowFile flowFile = session.get();
        if (flowFile == null) {
            return;
        }

        final AtomicInteger jsonCounter = new AtomicInteger();
        final AtomicInteger xmlCounter = new AtomicInteger();
        final AtomicInteger recordsCounter = new AtomicInteger();
        final AtomicInteger dbCounter = new AtomicInteger();

        final Set<BitcoinHistoryModel> jsonRecords = ConcurrentHashMap.newKeySet();
        final Set<BitcoinHistoryModel> xmlRecords = ConcurrentHashMap.newKeySet();
        final Set<BitcoinHistoryModel> dbRecords = ConcurrentHashMap.newKeySet();
        final AtomicBoolean success = new AtomicBoolean(Boolean.TRUE);

        session.read(flowFile, new InputStreamCallback() {
            @Override
            public void process(InputStream in) throws IOException {

                try (final BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {

                    final Iterable<CSVRecord> records = CSVFormat.RFC4180.withFirstRecordAsHeader().parse(reader);

                    for (final CSVRecord record : records) {

                        recordsCounter.incrementAndGet();

                        if (isValid(record)) {
                            final BitcoinHistoryModel history = createModel(record);
                            if (isOutputJson()) {
                                jsonRecords.add(history);
                                
                            }

                            if (isOutputXml()) {
                                xmlRecords.add(history);
                            }

                            if (isOutputDb()) {
                                dbRecords.add(history);
                              }

                        }
                    }
                } catch (Exception e) {
                    getLogger().error("Error Processing input", e);
                    success.set(Boolean.FALSE);
                }
            }
        });

        if (isOutputJson()) {
            writeJson(session, flowFile, jsonRecords, jsonCounter);
        }

        if (isOutputXml()) {
            writeXml(session, flowFile, xmlRecords, xmlCounter);
        }

        if (isOutputDb()) {
            writeDb(dbRecords, dbCounter);
          }

        session.adjustCounter(ConfigUtil.RECORDS_READ, recordsCounter.get(), true);
        session.adjustCounter(ConfigUtil.JSON_RECORDS, jsonCounter.get(), true);
        session.adjustCounter(ConfigUtil.XML_RECORDS, xmlCounter.get(), true);
        session.adjustCounter(ConfigUtil.DB_RECORDS, dbCounter.get(), true);

        if (!success.get()) {
            session.transfer(flowFile, ConfigUtil.FAILURE);
        } else {
            session.remove(flowFile);
        }

    }

    private void writeJson(final ProcessSession session, final FlowFile flowFile,
            final Set<BitcoinHistoryModel> jsonRecords, final AtomicInteger count) {

        for (final BitcoinHistoryModel history : jsonRecords) {
            final FlowFile createdFlowFile = session.create(flowFile);

            final FlowFile jsonFlowFile = session.write(createdFlowFile, new OutputStreamCallback() {

                @Override
                public void process(OutputStream out) throws IOException {
                    try (final BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out))) {
                        mapper.writeValue(writer, history);
                    } catch (Exception e) {
                        getLogger().error("Colud not write record to JSON: {}", new Object[] { history }, e);
                    }
                }
            });

            final Map<String, String> attrs = new ConcurrentHashMap<String, String>();
            final String fileName = getFilename(jsonFlowFile) + count.incrementAndGet() + ".json";
            attrs.put(CoreAttributes.FILENAME.key(), fileName);
            attrs.put(CoreAttributes.MIME_TYPE.key(), ConfigUtil.JSON_MIME_TYPE);
            attrs.put(ConfigUtil.JSON_RECORDS, String.valueOf(count));

            final FlowFile updatedFlowFile = session.putAllAttributes(jsonFlowFile, attrs);

            session.transfer(updatedFlowFile, ConfigUtil.JSON);
            getLogger().debug("Wrote {} to JSON", new Object[] { history });
        }

    }

    private void writeXml(final ProcessSession session, final FlowFile flowFile,
            final Set<BitcoinHistoryModel> xmlRecords, final AtomicInteger count) {
        for (final BitcoinHistoryModel history : xmlRecords) {
            final FlowFile createdFlowFile = session.create(flowFile);
            Marshaller marshaller;
            try {
                marshaller = jaxb.get().createMarshaller();
                marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
                final FlowFile xmlFlowFile = session.write(createdFlowFile, new OutputStreamCallback() {

                    @Override
                    public void process(OutputStream out) throws IOException {
                        try (final BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out))) {
                            marshaller.marshal(history, writer);
                        } catch (Exception e) {
                            getLogger().error("Colud not write record to XML: {}", new Object[] { history }, e);
                        }
                    }
                });

                final Map<String, String> attrs = new ConcurrentHashMap<String, String>();
                final String fileName = getFilename(xmlFlowFile) + count.incrementAndGet() + ".xml";
                attrs.put(CoreAttributes.FILENAME.key(), fileName);
                attrs.put(CoreAttributes.MIME_TYPE.key(), ConfigUtil.XML_MIME_TYPE);
                attrs.put(ConfigUtil.XML_RECORDS, String.valueOf(count));

                final FlowFile updatedFlowFile = session.putAllAttributes(xmlFlowFile, attrs);

                session.transfer(updatedFlowFile, ConfigUtil.XML);
                getLogger().debug("Wrote {} to XML", new Object[] { history });
            } catch (JAXBException e) {
                getLogger().error("JAXBException");
                session.remove(flowFile);
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

    private void writeDb(final Set<BitcoinHistoryModel> dbRecords, final AtomicInteger count) {

        final PreparedStatement stmt = this.stmt.get();
    
        try {
          for (final BitcoinHistoryModel history : dbRecords) {
            stmt.setTimestamp(1, Timestamp.from(history.getTimestamp().toInstant()));
            stmt.setDouble(2, history.getOpen());
            stmt.setDouble(3, history.getHigh());
            stmt.setDouble(4, history.getLow());
            stmt.setDouble(5, history.getClose());
            stmt.setDouble(6, history.getBtcVolume());
            stmt.setDouble(7, history.getUsdVolume());
            stmt.setDouble(8, history.getWeightedPrice());
            stmt.setTimestamp(9, Timestamp.from(ZonedDateTime.now().toInstant()));
            stmt.execute();
            count.incrementAndGet();
            getLogger().debug("Wrote {} to DB", new Object[] {history});
          }
        } catch (Exception e) {
          getLogger().error("Could not insert into DB", e);
        }
      }

    private String getFilename(final FlowFile flowFile) {
        final String fileName = flowFile.getAttribute(CoreAttributes.FILENAME.key());
        return fileName.substring(0, fileName.lastIndexOf("."));
    }

    private boolean isValid(final CSVRecord record) {
        return !record.toString().contains("NaN");
    }

    private boolean isOutputJson() {
        return output.get() == Output.ALL || output.get() == Output.JSON;
    }

    private boolean isOutputXml() {
        return output.get() == Output.ALL || output.get() == Output.XML;
    }

    private boolean isOutputDb() {
        return output.get() == Output.ALL || output.get() == Output.DB;
      }
    
}
