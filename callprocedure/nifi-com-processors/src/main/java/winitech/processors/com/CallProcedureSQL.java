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
package winitech.processors.com;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.nifi.annotation.behavior.EventDriven;
import org.apache.nifi.annotation.behavior.InputRequirement;
import org.apache.nifi.annotation.behavior.InputRequirement.Requirement;
import org.apache.nifi.annotation.behavior.ReadsAttribute;
import org.apache.nifi.annotation.behavior.ReadsAttributes;
import org.apache.nifi.annotation.behavior.WritesAttribute;
import org.apache.nifi.annotation.behavior.WritesAttributes;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.expression.ExpressionLanguageScope;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processors.standard.sql.DefaultAvroSqlWriter;
import org.apache.nifi.processors.standard.sql.SqlWriter;
import org.apache.nifi.processors.standard.util.JdbcCommon;
import org.apache.nifi.processors.standard.util.AvroUtil.CodecType;

import static org.apache.nifi.processors.standard.util.JdbcCommon.DEFAULT_PRECISION;
import static org.apache.nifi.processors.standard.util.JdbcCommon.DEFAULT_SCALE;
import static org.apache.nifi.processors.standard.util.JdbcCommon.NORMALIZE_NAMES_FOR_AVRO;
import static org.apache.nifi.processors.standard.util.JdbcCommon.USE_AVRO_LOGICAL_TYPES;

@EventDriven
@InputRequirement(Requirement.INPUT_ALLOWED)
@Tags({"sql", "select", "jdbc", "query", "database"})
@CapabilityDescription("Executes provided SQL select query. Query result will be converted to Avro format."
        + " Streaming is used so arbitrarily large result sets are supported. This processor can be scheduled to run on "
        + "a timer, or cron expression, using the standard scheduling methods, or it can be triggered by an incoming FlowFile. "
        + "If it is triggered by an incoming FlowFile, then attributes of that FlowFile will be available when evaluating the "
        + "select query, and the query may use the ? to escape parameters. In this case, the parameters to use must exist as FlowFile attributes "
        + "with the naming convention sql.args.N.type and sql.args.N.value, where N is a positive integer. The sql.args.N.type is expected to be "
        + "a number indicating the JDBC Type. The content of the FlowFile is expected to be in UTF-8 format. "
        + "FlowFile attribute 'executesql.row.count' indicates how many rows were selected.")
@ReadsAttributes({
        @ReadsAttribute(attribute = "sql.args.N.type", description = "Incoming FlowFiles are expected to be parametrized SQL statements. The type of each Parameter is specified as an integer "
                + "that represents the JDBC Type of the parameter."),
        @ReadsAttribute(attribute = "sql.args.N.value", description = "Incoming FlowFiles are expected to be parametrized SQL statements. The value of the Parameters are specified as "
                + "sql.args.1.value, sql.args.2.value, sql.args.3.value, and so on. The type of the sql.args.1.value Parameter is specified by the sql.args.1.type attribute."),
        @ReadsAttribute(attribute = "sql.args.N.format", description = "This attribute is always optional, but default options may not always work for your data. "
                + "Incoming FlowFiles are expected to be parametrized SQL statements. In some cases "
                + "a format option needs to be specified, currently this is only applicable for binary data types, dates, times and timestamps. Binary Data Types (defaults to 'ascii') - "
                + "ascii: each string character in your attribute value represents a single byte. This is the format provided by Avro Processors. "
                + "base64: the string is a Base64 encoded string that can be decoded to bytes. "
                + "hex: the string is hex encoded with all letters in upper case and no '0x' at the beginning. "
                + "Dates/Times/Timestamps - "
                + "Date, Time and Timestamp formats all support both custom formats or named format ('yyyy-MM-dd','ISO_OFFSET_DATE_TIME') "
                + "as specified according to java.time.format.DateTimeFormatter. "
                + "If not specified, a long value input is expected to be an unix epoch (milli seconds from 1970/1/1), or a string value in "
                + "'yyyy-MM-dd' format for Date, 'HH:mm:ss.SSS' for Time (some database engines e.g. Derby or MySQL do not support milliseconds and will truncate milliseconds), "
                + "'yyyy-MM-dd HH:mm:ss.SSS' for Timestamp is used.")
})
@WritesAttributes({
        @WritesAttribute(attribute = "executesql.row.count", description = "Contains the number of rows returned by the query. "
                + "If 'Max Rows Per Flow File' is set, then this number will reflect the number of rows in the Flow File instead of the entire result set."),
        @WritesAttribute(attribute = "executesql.query.duration", description = "Combined duration of the query execution time and fetch time in milliseconds. "
                + "If 'Max Rows Per Flow File' is set, then this number will reflect only the fetch time for the rows in the Flow File instead of the entire result set."),
        @WritesAttribute(attribute = "executesql.query.executiontime", description = "Duration of the query execution time in milliseconds. "
                + "This number will reflect the query execution time regardless of the 'Max Rows Per Flow File' setting."),
        @WritesAttribute(attribute = "executesql.query.fetchtime", description = "Duration of the result set fetch time in milliseconds. "
                + "If 'Max Rows Per Flow File' is set, then this number will reflect only the fetch time for the rows in the Flow File instead of the entire result set."),
        @WritesAttribute(attribute = "executesql.resultset.index", description = "Assuming multiple result sets are returned, "
                + "the zero based index of this result set."),
        @WritesAttribute(attribute = "executesql.error.message", description = "If processing an incoming flow file causes "
                + "an Exception, the Flow File is routed to failure and this attribute is set to the exception message."),
        @WritesAttribute(attribute = "fragment.identifier", description = "If 'Max Rows Per Flow File' is set then all FlowFiles from the same query result set "
                + "will have the same value for the fragment.identifier attribute. This can then be used to correlate the results."),
        @WritesAttribute(attribute = "fragment.count", description = "If 'Max Rows Per Flow File' is set then this is the total number of  "
                + "FlowFiles produced by a single ResultSet. This can be used in conjunction with the "
                + "fragment.identifier attribute in order to know how many FlowFiles belonged to the same incoming ResultSet. If Output Batch Size is set, then this "
                + "attribute will not be populated."),
        @WritesAttribute(attribute = "fragment.index", description = "If 'Max Rows Per Flow File' is set then the position of this FlowFile in the list of "
                + "outgoing FlowFiles that were all derived from the same result set FlowFile. This can be "
                + "used in conjunction with the fragment.identifier attribute to know which FlowFiles originated from the same query result set and in what order  "
                + "FlowFiles were produced")
})
public class CallProcedureSQL extends CustomAbstractExecuteSQL {

    public static final PropertyDescriptor COMPRESSION_FORMAT = new PropertyDescriptor.Builder()
            .name("compression-format")
            .displayName("Compression Format")
            .description("Compression type to use when writing Avro files. Default is None.")
            .allowableValues(CodecType.values())
            .defaultValue(CodecType.NONE.toString())
            .expressionLanguageSupported(ExpressionLanguageScope.NONE)
            .required(true)
            .build();

    public CallProcedureSQL() {
        final Set<Relationship> r = new HashSet<>();
        r.add(REL_SUCCESS);
        r.add(REL_FAILURE);
        relationships = Collections.unmodifiableSet(r);

        final List<PropertyDescriptor> pds = new ArrayList<>();
        pds.add(DBCP_SERVICE);
        pds.add(SQL_SELECT_QUERY);
        pds.add(QUERY_TIMEOUT);
        pds.add(NORMALIZE_NAMES_FOR_AVRO);
        pds.add(USE_AVRO_LOGICAL_TYPES);
        pds.add(COMPRESSION_FORMAT);
        pds.add(DEFAULT_PRECISION);
        pds.add(DEFAULT_SCALE);
        pds.add(MAX_ROWS_PER_FLOW_FILE);
        pds.add(OUTPUT_BATCH_SIZE);
        propDescriptors = Collections.unmodifiableList(pds);
    }

    @Override
    protected SqlWriter configureSqlWriter(ProcessSession session, ProcessContext context, FlowFile fileToProcess) {
        final boolean convertNamesForAvro = context.getProperty(NORMALIZE_NAMES_FOR_AVRO).asBoolean();
        final Boolean useAvroLogicalTypes = context.getProperty(USE_AVRO_LOGICAL_TYPES).asBoolean();
        final Integer maxRowsPerFlowFile = context.getProperty(MAX_ROWS_PER_FLOW_FILE).evaluateAttributeExpressions().asInteger();
        final Integer defaultPrecision = context.getProperty(DEFAULT_PRECISION).evaluateAttributeExpressions(fileToProcess).asInteger();
        final Integer defaultScale = context.getProperty(DEFAULT_SCALE).evaluateAttributeExpressions(fileToProcess).asInteger();
        final String codec = context.getProperty(COMPRESSION_FORMAT).getValue();

        final JdbcCommon.AvroConversionOptions options = JdbcCommon.AvroConversionOptions.builder()
                .convertNames(convertNamesForAvro)
                .useLogicalTypes(useAvroLogicalTypes)
                .defaultPrecision(defaultPrecision)
                .defaultScale(defaultScale)
                .maxRows(maxRowsPerFlowFile)
                .codecFactory(codec)
                .build();
        return new DefaultAvroSqlWriter(options);
    }
}
