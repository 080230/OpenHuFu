package tk.onedb.core.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.calcite.linq4j.Enumerator;
import org.apache.calcite.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tk.onedb.core.config.OneDBConfig;
import tk.onedb.core.data.BasicDataSet;
import tk.onedb.core.data.Header;
import tk.onedb.core.data.Row;
import tk.onedb.core.data.StreamBuffer;
import tk.onedb.core.sql.enumerator.RowEnumerator;
import tk.onedb.core.table.OneDBTableInfo;
import tk.onedb.core.zk.OneDBZkClient;
import tk.onedb.core.zk.ZkConfig;
import tk.onedb.rpc.OneDBCommon.DataSetProto;
import tk.onedb.rpc.OneDBCommon.OneDBQueryProto;

/*
* client for all DB
*/
public class OneDBClient {
  private static final Logger LOG = LoggerFactory.getLogger(OneDBClient.class);

  private final Map<String, DBClient> dbClientMap;
  private final Map<String, OneDBTableInfo> tableMap;
  private final ExecutorService executorService;
  private final OneDBZkClient zkClient;

  public OneDBClient() {
    dbClientMap = new ConcurrentHashMap<>();
    tableMap = new ConcurrentHashMap<>();
    zkClient = null;
    this.executorService = Executors.newFixedThreadPool(OneDBConfig.CLIENT_THREAD_NUM);
  }

  public OneDBClient(ZkConfig zkConfig) {
    dbClientMap = new ConcurrentHashMap<>();
    tableMap = new ConcurrentHashMap<>();
    zkClient = new OneDBZkClient(zkConfig, this);
    this.executorService = Executors.newFixedThreadPool(OneDBConfig.CLIENT_THREAD_NUM);
  }

  private boolean hasZk() {
    return zkClient != null;
  }

  Map<String, OneDBTableInfo> getTableMap() {
    return tableMap;
  }

  public ExecutorService getExecutorService() {
    return executorService;
  }

  public boolean addDB(String endpoint) {
    if (hasDB(endpoint)) {
      return false;
    }
    DBClient client = new DBClient(endpoint);
    for (Map.Entry<String, DBClient> entry : dbClientMap.entrySet()) {
      entry.getValue().addClient(endpoint);
      client.addClient(endpoint);
    }
    dbClientMap.put(endpoint, client);
    return true;
  }

  public boolean hasDB(String endpoint) {
    return dbClientMap.containsKey(endpoint);
  }

  public DBClient getDBClient(String endpoint) {
    return dbClientMap.get(endpoint);
  }

  // for global table
  public void addTable(String tableName, OneDBTableInfo table) {
    this.tableMap.put(tableName, table);
  }

  public void dropTable(String tableName) {
    tableMap.remove(tableName);
  }

  public OneDBTableInfo getTable(String tableName) {
    return tableMap.get(tableName);
  }

  public boolean hasTable(String tableName) {
    return tableMap.containsKey(tableName);
  }

  // for local table
  public void addLocalTable(String globalTableName, String endpoint, String localTableName) {
    OneDBTableInfo table = getTable(globalTableName);
    if (table == null) {
      LOG.error("Gloabl table {} not exists", globalTableName);
      return;
    }
    DBClient client = getDBClient(endpoint);
    if (client == null) {
      LOG.error("Endpoint {} not exists", endpoint);
      return;
    }
    table.addLocalTable(client, localTableName);
  }

  public void removeLocalTable(String globalTableName, String endpoint, String localTableName) {
    OneDBTableInfo table = getTable(globalTableName);
    if (table == null) {
      LOG.error("Gloabl table {} not exists", globalTableName);
      return;
    }
    DBClient client = getDBClient(endpoint);
    if (client == null) {
      LOG.error("Endpoint {} not exists", endpoint);
    }
    table.removeLocalTable(client, localTableName);
  }

  public void removeLocalTable(String endpoint, String tableName) {
    for (OneDBTableInfo info : tableMap.values()) {
      info.removeLocalTable(dbClientMap.get(endpoint), tableName);
    }
  }

  public void removeDB(String endpoint) {
    for (OneDBTableInfo info : tableMap.values()) {
      info.removeDB(dbClientMap.get(endpoint));
    }
  }

  public Header getHeader(String tableName) {
    OneDBTableInfo table = getTable(tableName);
    return table != null ? table.getHeader() : null;
  }

  public List<Pair<DBClient, String>> getTableClients(String tableName) {
    OneDBTableInfo table = getTable(tableName);
    return table != null ? table.getTableList() : null;
  }

  /*
  * onedb query
  */
  public Enumerator<Row> oneDBQuery(String tableName, OneDBQueryProto query) {
    List<Pair<DBClient, String>> tableClients = getTableClients(tableName);
    StreamBuffer<DataSetProto> streamProto = oneDBQuery(getHeader(tableName), query, tableClients);
    Header header = Header.fromProto(query.getHeader());
    if (query.getAggExpCount() > 0) {
      BasicDataSet localDataSet = BasicDataSet.of(header);
      while (streamProto.hasNext()) {
        localDataSet.mergeDataSet(BasicDataSet.fromProto(streamProto.next()));
      }
      return localDataSet;
    } else {
      return new RowEnumerator(streamProto, query.getFetch());
    }
  }

  private StreamBuffer<DataSetProto> oneDBQuery(Header header, OneDBQueryProto query, List<Pair<DBClient, String>> tableClients) {
    StreamBuffer<DataSetProto> iterator = new StreamBuffer<>(tableClients.size());
    List<Callable<Boolean>> tasks = new ArrayList<Callable<Boolean>>();
    for (Pair<DBClient, String> entry : tableClients) {
      tasks.add(() -> {
        try {
          Iterator<DataSetProto> it = entry.getKey().oneDBQuery(query);
          while (it.hasNext()) {
            iterator.add(it.next());
          }
          return true;
        } catch (Exception e) {
          e.printStackTrace();
          return false;
        } finally {
          iterator.finish();
        }
      });
    }
    try {
      List<Future<Boolean>> statusList = executorService.invokeAll(tasks);
      for (Future<Boolean> status : statusList) {
        if (!status.get()) {
          LOG.error("error in oneDBQuery");
        }
      }
    } catch (InterruptedException | ExecutionException e) {
      e.printStackTrace();
    }
    return iterator;
  }
}
