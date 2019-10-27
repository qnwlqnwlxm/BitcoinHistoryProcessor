package com.mbio.custom.processors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.dbcp.DBCPService;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.util.StandardValidators;

public enum ConfigUtil {

  INSTANCE;

  private static List<PropertyDescriptor> properties;
  private static Set<Relationship> relationships;

  public static final String JSON_RECORDS = "JSON records created";
  public static final String XML_RECORDS = "XML records created";
  public static final String RECORDS_READ = "CSV records read";
  public static final String DB_RECORDS = "DB records inserted";

  public static final String JSON_MIME_TYPE = "application/json";
  public static final String XML_MIME_TYPE = "text/xml";

  public static final PropertyDescriptor OUTPUT = new PropertyDescriptor.Builder().name("output")
      .displayName("Output format").description("Format of output FlowFiles")
      .allowableValues("XML", "JSON", "ALL").defaultValue("ALL")
      .addValidator(StandardValidators.NON_EMPTY_VALIDATOR).required(true).build();
  
      public static final PropertyDescriptor DS_PROP =
      new PropertyDescriptor.Builder().name("dbcp").description("Database Connection Pool")
          .identifiesControllerService(DBCPService.class).required(true).build();

  public static final Relationship XML =
      new Relationship.Builder().name("xml").description("Files process as JSON successfully routed here").build();

  public static final Relationship JSON =
      new Relationship.Builder().name("json").description("Files process as XML successfully routed here").build();
  
  public static final Relationship FAILURE =
      new Relationship.Builder().name("failure").description("Files routed here after processing failure").build();
  
  static {
    List<PropertyDescriptor> properties = new ArrayList<>();
    properties.add(OUTPUT);
    properties.add(DS_PROP);
    ConfigUtil.properties = Collections.unmodifiableList(properties);
    
    Set<Relationship> relationships = new HashSet<>();
    relationships.add(JSON);
    relationships.add(XML);
    relationships.add(FAILURE);
    ConfigUtil.relationships = Collections.unmodifiableSet(relationships);
  }
  
  public static List<PropertyDescriptor> getProperties(){
    return properties;
  }
  
  public static Set<Relationship> getRelaltionships(){
    return relationships;
  }
  
}
