package com.hufudb.openhufu.benchmark;

import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.hufudb.openhufu.benchmark.OpenHuFuBenchmark;
import com.hufudb.openhufu.benchmark.enums.TPCHTableName;
import com.hufudb.openhufu.core.table.GlobalTableConfig;
import com.hufudb.openhufu.data.schema.Schema;
import com.hufudb.openhufu.data.storage.DataSet;
import com.hufudb.openhufu.data.storage.DataSetIterator;
import com.hufudb.openhufu.expression.ExpressionFactory;
import com.hufudb.openhufu.owner.user.OpenHuFuUser;
import com.hufudb.openhufu.plan.BinaryPlan;
import com.hufudb.openhufu.plan.LeafPlan;
import com.hufudb.openhufu.proto.OpenHuFuData;
import com.hufudb.openhufu.proto.OpenHuFuPlan.JoinCondition;
import com.hufudb.openhufu.proto.OpenHuFuPlan.JoinType;
import com.hufudb.openhufu.proto.OpenHuFuData.Modifier;
import com.hufudb.openhufu.proto.OpenHuFuData.ColumnType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.hufudb.openhufu.plan.Plan;
import com.hufudb.openhufu.proto.OpenHuFuPlan;
import org.junit.Test;
import org.junit.Before; 
import org.junit.After;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.Warmup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 
* OpenHuFuBenchmark Tester. 
* 
* @author <Authors name> 
* @since <pre>2月 15, 2023</pre> 
* @version 1.0 
*/ 
public class OpenHuFuBenchmarkTest {
  private static final Logger LOG = LoggerFactory.getLogger(OpenHuFuBenchmark.class);
  private static final OpenHuFuUser user = new OpenHuFuUser();

  @Before
  public void setUp() throws IOException {

    List<String> endpoints =
        new Gson().fromJson(Files.newBufferedReader(
                Path.of(OpenHuFuBenchmark.class.getClassLoader().getResource("endpoints.json")
                    .getPath())),
            new TypeToken<ArrayList<String>>() {
            }.getType());
    List<GlobalTableConfig> globalTableConfigs =
        new Gson().fromJson(Files.newBufferedReader(
                Path.of(OpenHuFuBenchmark.class.getClassLoader().getResource("tables.json")
                    .getPath())),
            new TypeToken<ArrayList<GlobalTableConfig>>() {
            }.getType());
    LOG.info("Init benchmark of OpenHuFu...");
    for (String endpoint : endpoints) {
      user.addOwner(endpoint, null);
    }

    for (GlobalTableConfig config : globalTableConfigs) {
      user.createOpenHuFuTable(config);
    }
    LOG.info("Init finish");
  }

  @Test
  public void testSelect() {
    String tableName = TPCHTableName.NATION.getName();
    LeafPlan plan = new LeafPlan();
    plan.setTableName(tableName);
    plan.setSelectExps(ExpressionFactory
        .createInputRef(user.getOpenHuFuTableSchema(tableName).getSchema()));
    DataSet dataset = user.executeQuery(plan);
    DataSetIterator it = dataset.getIterator();
    int count = 0;
    while (it.next()) {
      for (int i = 0; i < it.size(); i++) {
        System.out.print(it.get(i) + "|");
      }
      System.out.println();
      ++count;
    }
    assertEquals(75, count);
    dataset.close();
  }

  @Test
  public void testEqualJoin() {
    String leftTableName = TPCHTableName.SUPPLIER.getName();
    LeafPlan leftPlan = new LeafPlan();
    leftPlan.setTableName(leftTableName);
    leftPlan.setSelectExps(ExpressionFactory
            .createInputRef(user.getOpenHuFuTableSchema(leftTableName).getSchema()));

    String rightTableName = TPCHTableName.NATION.getName();
    LeafPlan rightPlan = new LeafPlan();
    rightPlan.setTableName(rightTableName);
    rightPlan.setSelectExps(ExpressionFactory
            .createInputRef(user.getOpenHuFuTableSchema(rightTableName).getSchema()));

    BinaryPlan plan = new BinaryPlan(leftPlan, rightPlan);
    JoinCondition joinCondition = JoinCondition.newBuilder().setType(JoinType.INNER).addLeftKey(3)
            .addRightKey(0).setModifier(Modifier.PUBLIC).build();
    plan.setJoinInfo(joinCondition);
    plan.setSelectExps(ExpressionFactory
            .createInputRef(Schema.merge(user.getOpenHuFuTableSchema(leftTableName).getSchema(), user.getOpenHuFuTableSchema(rightTableName).getSchema())));
    DataSet dataset = user.executeQuery(plan);
    DataSetIterator it = dataset.getIterator();
    int count = 0;
    while (it.next()) {
      for (int i = 0; i < it.size(); i++) {
        System.out.print(it.get(i) + "|");
      }
      System.out.println();
      ++count;
    }
    assertEquals(90, count);
    dataset.close();
  }
@Test
  public void testCount() {
    String tableName = TPCHTableName.SUPPLIER.getName();
    LeafPlan plan = new LeafPlan();
    plan.setTableName(tableName);
  plan.setAggExps(ImmutableList.of(
          ExpressionFactory.createAggFunc(OpenHuFuData.ColumnType.DOUBLE, Modifier.PUBLIC, 1,
                  ImmutableList.of(ExpressionFactory.createInputRef(0, ColumnType.LONG, Modifier.PUBLIC)))));

    DataSet dataset = user.executeQuery(plan);
    DataSetIterator it = dataset.getIterator();
    it.next();
    assertEquals(30, ((Number) it.get(0)).longValue());
    dataset.close();
  }

  @Test
  public void testAvg() {
    String tableName = TPCHTableName.SUPPLIER.getName();
    LeafPlan plan = new LeafPlan();
    plan.setTableName(tableName);
    plan.setAggExps(ImmutableList.of(
            ExpressionFactory.createAggFunc(OpenHuFuData.ColumnType.DOUBLE, Modifier.PUBLIC, 2,
                    ImmutableList.of(ExpressionFactory.createInputRef(0, ColumnType.LONG, Modifier.PUBLIC)))));

    DataSet dataset = user.executeQuery(plan);
    DataSetIterator it = dataset.getIterator();
    it.next();
    assertEquals(5.5, ((Number) it.get(0)).doubleValue(), 0.1);
    dataset.close();
  }

  @Test
  public void testSum() {
    String tableName = TPCHTableName.SUPPLIER.getName();
    LeafPlan plan = new LeafPlan();
    plan.setTableName(tableName);
    plan.setAggExps(ImmutableList.of(
            ExpressionFactory.createAggFunc(OpenHuFuData.ColumnType.DOUBLE, Modifier.PUBLIC, 5,
                    ImmutableList.of(ExpressionFactory.createInputRef(0, ColumnType.LONG, Modifier.PUBLIC)))));

    DataSet dataset = user.executeQuery(plan);
    DataSetIterator it = dataset.getIterator();
    it.next();
    assertEquals(165, ((Number) it.get(0)).longValue());
    dataset.close();
  }
} 
