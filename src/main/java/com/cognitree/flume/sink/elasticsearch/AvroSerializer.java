/*
 * Copyright 2017 Cognitree Technologies
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.cognitree.flume.sink.elasticsearch;

import com.google.common.base.Throwables;
import org.apache.avro.Schema;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.flume.Context;
import org.apache.flume.Event;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.elasticsearch.common.xcontent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static com.cognitree.flume.sink.elasticsearch.Constants.ES_AVRO_SCHEMA_FILE;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

/**
 * Created by prashant
 * <p>
 * This Serializer assumes the event body to be in avro binary format
 */
public class AvroSerializer implements Serializer {

    private static final Logger logger = LoggerFactory.getLogger(AvroSerializer.class);

    private DatumReader<GenericRecord> datumReader;

    private static final ObjectMapper objectMapper= new ObjectMapper();

    /**
     * Converts the avro binary data to the json format
     */
    @Override
    public XContentBuilder serialize(Event event) {
        XContentBuilder builder = null;

        try {
            Decoder decoder = new DecoderFactory().binaryDecoder(event.getBody(), null);
            GenericRecord data;
            if(event.getHeaders().containsKey("flume.avro.schema.literal")) {
                Schema schema = new Schema.Parser().parse((event.getHeaders().get("flume.avro.schema.literal")));
                DatumReader<GenericRecord> datumReader = new GenericDatumReader<GenericRecord>(schema);
                data = datumReader.read(null, decoder);
            } else {
                data = datumReader.read(null, decoder);
            }

            logger.trace("Record in event " + data);
            XContentParser parser = XContentFactory
                    .xContent(XContentType.JSON)
                    .createParser(NamedXContentRegistry.EMPTY, data.toString());
            builder = jsonBuilder().copyCurrentStructure(parser);

            JsonNode dataNode = objectMapper.readTree(builder.string());

            System.out.println("BUILDER STRING: " + builder.string());
            event.getHeaders().put("index", dataNode.get("es_index").getTextValue());
            event.getHeaders().put("type", dataNode.get("es_type").getTextValue());

            parser.close();
        } catch (IOException e) {
            logger.error("Exception in parsing avro format data but continuing serialization to process further records",
                    e.getMessage(), e);
        }

        return builder;
    }

    @Override
    public void configure(Context context) {
        String file = context.getString(ES_AVRO_SCHEMA_FILE);
        if (file == null) {
            Throwables.propagate(new Exception("Schema file is not configured, " +
                    "please configure the property " + ES_AVRO_SCHEMA_FILE));
        }
        try {
            Schema schema = new Schema.Parser().parse(new File(file));
            datumReader = new GenericDatumReader<GenericRecord>(schema);
        } catch (IOException e) {
            logger.error("Error in parsing schema file ", e.getMessage(), e);
            Throwables.propagate(e);
        }
    }
}
