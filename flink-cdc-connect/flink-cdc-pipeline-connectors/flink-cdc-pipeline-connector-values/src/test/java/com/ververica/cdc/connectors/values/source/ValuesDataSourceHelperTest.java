/*
 * Copyright 2023 Ververica Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ververica.cdc.connectors.values.source;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.CloseableIterator;

import com.ververica.cdc.common.data.GenericRecordData;
import com.ververica.cdc.common.data.GenericStringData;
import com.ververica.cdc.common.event.CreateTableEvent;
import com.ververica.cdc.common.event.DataChangeEvent;
import com.ververica.cdc.common.event.Event;
import com.ververica.cdc.common.event.SchemaChangeEvent;
import com.ververica.cdc.common.event.TableId;
import com.ververica.cdc.common.schema.Schema;
import com.ververica.cdc.common.source.FlinkSourceProvider;
import com.ververica.cdc.common.types.DataTypes;
import com.ververica.cdc.common.types.RowType;
import com.ververica.cdc.connectors.values.ValuesDatabase;
import com.ververica.cdc.connectors.values.factory.ValuesDataFactory;
import com.ververica.cdc.runtime.typeutils.EventTypeInfo;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** Integration tests for different enumeration situations of {@link ValuesDataSourceHelper}. */
public class ValuesDataSourceHelperTest {

    @Before
    public void before() {
        ValuesDatabase.clear();
    }

    @After
    public void after() {
        ValuesDatabase.clear();
    }

    @Test
    public void testSingleSplitSingleTable() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.enableCheckpointing(3000);
        env.setRestartStrategy(RestartStrategies.noRestart());
        FlinkSourceProvider sourceProvider =
                (FlinkSourceProvider)
                        new ValuesDataSource(
                                        ValuesDataSourceHelper.SourceEventType
                                                .SINGLE_SPLIT_SINGLE_TABLE)
                                .getEventSourceProvider();
        CloseableIterator<Event> events =
                env.fromSource(
                                sourceProvider.getSource(),
                                WatermarkStrategy.noWatermarks(),
                                ValuesDataFactory.IDENTIFIER,
                                new EventTypeInfo())
                        .executeAndCollect();
        events.forEachRemaining(
                (event) -> {
                    if (event instanceof DataChangeEvent) {
                        ValuesDatabase.applyDataChangeEvent((DataChangeEvent) event);
                    } else if (event instanceof SchemaChangeEvent) {
                        ValuesDatabase.applySchemaChangeEvent((SchemaChangeEvent) event);
                    }
                });

        List<String> results = new ArrayList<>();
        results.add("default.default.table1:col1=2;newCol3=x");
        results.add("default.default.table1:col1=3;newCol3=");
        Assert.assertEquals(
                results, ValuesDatabase.getResults(TableId.parse("default.default.table1")));
    }

    @Test
    public void testSingleSplitMultiTables() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.enableCheckpointing(3000);
        env.setRestartStrategy(RestartStrategies.noRestart());
        FlinkSourceProvider sourceProvider =
                (FlinkSourceProvider)
                        new ValuesDataSource(
                                        ValuesDataSourceHelper.SourceEventType
                                                .SINGLE_SPLIT_MULTI_TABLES)
                                .getEventSourceProvider();
        CloseableIterator<Event> events =
                env.fromSource(
                                sourceProvider.getSource(),
                                WatermarkStrategy.noWatermarks(),
                                ValuesDataFactory.IDENTIFIER,
                                new EventTypeInfo())
                        .executeAndCollect();
        events.forEachRemaining(
                (event) -> {
                    if (event instanceof DataChangeEvent) {
                        ValuesDatabase.applyDataChangeEvent((DataChangeEvent) event);
                    } else if (event instanceof SchemaChangeEvent) {
                        ValuesDatabase.applySchemaChangeEvent((SchemaChangeEvent) event);
                    }
                });

        List<String> results = new ArrayList<>();
        results.add("default.default.table1:col1=2;newCol3=x");
        results.add("default.default.table1:col1=3;newCol3=");
        Assert.assertEquals(
                results, ValuesDatabase.getResults(TableId.parse("default.default.table1")));

        results.clear();
        results.add("default.default.table2:col1=1;col2=1");
        results.add("default.default.table2:col1=2;col2=2");
        results.add("default.default.table2:col1=3;col2=3");
        Assert.assertEquals(
                results, ValuesDatabase.getResults(TableId.parse("default.default.table2")));
    }

    @Test
    public void testCustomSourceEvents() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.enableCheckpointing(3000);
        env.setRestartStrategy(RestartStrategies.noRestart());

        List<List<Event>> splits = new ArrayList<>();
        List<Event> split1 = new ArrayList<>();
        TableId table1 = TableId.tableId("default", "default", "table1");
        Schema schema =
                Schema.newBuilder()
                        .physicalColumn("col1", DataTypes.STRING())
                        .physicalColumn("col2", DataTypes.STRING())
                        .primaryKey("col1")
                        .build();
        CreateTableEvent createTableEvent = new CreateTableEvent(table1, schema);
        split1.add(createTableEvent);
        DataChangeEvent insertEvent1 =
                DataChangeEvent.insertEvent(
                        table1,
                        RowType.of(DataTypes.STRING(), DataTypes.STRING()),
                        GenericRecordData.of(
                                GenericStringData.fromString("1"),
                                GenericStringData.fromString("1")));
        split1.add(insertEvent1);
        DataChangeEvent insertEvent2 =
                DataChangeEvent.insertEvent(
                        table1,
                        RowType.of(DataTypes.STRING(), DataTypes.STRING()),
                        GenericRecordData.of(
                                GenericStringData.fromString("2"),
                                GenericStringData.fromString("2")));
        split1.add(insertEvent2);
        splits.add(split1);
        ValuesDataSourceHelper.setSourceEvents(splits);

        FlinkSourceProvider sourceProvider =
                (FlinkSourceProvider)
                        new ValuesDataSource(
                                        ValuesDataSourceHelper.SourceEventType.CUSTOM_SOURCE_EVENTS)
                                .getEventSourceProvider();
        CloseableIterator<Event> events =
                env.fromSource(
                                sourceProvider.getSource(),
                                WatermarkStrategy.noWatermarks(),
                                ValuesDataFactory.IDENTIFIER,
                                new EventTypeInfo())
                        .executeAndCollect();
        events.forEachRemaining(
                (event) -> {
                    if (event instanceof DataChangeEvent) {
                        ValuesDatabase.applyDataChangeEvent((DataChangeEvent) event);
                    } else if (event instanceof SchemaChangeEvent) {
                        ValuesDatabase.applySchemaChangeEvent((SchemaChangeEvent) event);
                    }
                });

        List<String> results = new ArrayList<>();
        results.add("default.default.table1:col1=1;col2=1");
        results.add("default.default.table1:col1=2;col2=2");
        Assert.assertEquals(
                results, ValuesDatabase.getResults(TableId.parse("default.default.table1")));
    }
}
