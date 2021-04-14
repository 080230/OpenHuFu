package tk.onedb.server;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.grpc.stub.StreamObserver;
import tk.onedb.OneDBService.GeneralRequest;
import tk.onedb.OneDBService.GeneralResponse;
import tk.onedb.ServiceGrpc;
import tk.onedb.core.client.DBClient;
import tk.onedb.core.data.DataSet;
import tk.onedb.core.data.Header;
import tk.onedb.core.data.StreamObserverDataSet;
import tk.onedb.core.data.TableInfo;
import tk.onedb.core.sql.expression.OneDBQuery;
import tk.onedb.core.zk.DBZkClient;
import tk.onedb.rpc.OneDBCommon.DataSetProto;
import tk.onedb.rpc.OneDBCommon.HeaderProto;
import tk.onedb.rpc.OneDBCommon.OneDBQueryProto;
import tk.onedb.server.data.ServerConfig;
import tk.onedb.server.data.ServerConfig.Mapping;

public abstract class DBService extends ServiceGrpc.ServiceImplBase {
  private static final Logger LOG = LoggerFactory.getLogger(DBService.class);
  private final Map<String, TableInfo> tableInfoMap;
  protected final Map<String, DBClient> dbClientMap;
  protected final Lock clientLock;
  // private final ExecutorService executorService;
  private final DBZkClient zkClient;
  protected final String endpoint;

  protected DBService(String zkServers, String zkRootPath, String endpoint, String digest) {
    this.tableInfoMap = new HashMap<>();
    this.dbClientMap = new HashMap<>();
    this.clientLock = new ReentrantLock();
    // this.executorService = Executors.newFixedThreadPool(OneDBConfig.SERVER_THREAD_NUM);
    this.endpoint = endpoint;
    if (zkServers == null || zkRootPath == null || digest == null) {
      zkClient = null;
    } else {
      DBZkClient client;
      try {
         client = new DBZkClient(zkServers, zkRootPath, endpoint, digest.getBytes());
      } catch (Exception e) {
        LOG.error("Error when init DBZkClient: {}", e.getMessage());
        client = null;
      }
      this.zkClient = client;
    }
  }

  @Override
  public void oneDBQuery(OneDBQueryProto request, StreamObserver<DataSetProto> responseObserver) {
    OneDBQuery query = OneDBQuery.fromProto(request);
    Header header = query.generateHeader();
    StreamObserverDataSet obDataSet = new StreamObserverDataSet(responseObserver, header);
    try {
      oneDBQueryInternal(query, obDataSet);
    } catch (SQLException e) {
      LOG.error("error when query table [{}]", request.getTableName());
      e.printStackTrace();
    }
    obDataSet.close();
  }

  @Override
  public void addClient(GeneralRequest request, StreamObserver<GeneralResponse> responseObserver) {
    super.addClient(request, responseObserver);
  }

  @Override
  public void getTableHeader(GeneralRequest request, StreamObserver<HeaderProto> responseObserver) {
    HeaderProto headerProto = getTableHeader(request.getValue()).toProto();
    LOG.info("Get header of table {}", request.getValue());
    responseObserver.onNext(headerProto);
    responseObserver.onCompleted();
  }

  protected Header getTableHeader(String name) {
    TableInfo info = tableInfoMap.get(name);
    if (info == null) {
      return Header.newBuilder().build();
    } else {
      return info.getHeader();
    }
  }


  final protected void addTable(ServerConfig.Table table) throws SQLException {
    TableInfo tableInfo = loadTableInfo(table);
    LOG.info("add {}", tableInfo.toString());
    addTableInfo(tableInfo);
    for (Mapping m : table.mappings) {
      registerTable(m.schema, m.name, tableInfo.getName());
    }
  }

  final protected void addTableInfo(TableInfo tableInfo) {
    zkClient.addTableInfo(tableInfo);
    tableInfoMap.put(tableInfo.getName(), tableInfo);
  }

  final protected TableInfo getTableInfo(String tableName) {
    return tableInfoMap.get(tableName);
  }

  final protected boolean registerTable(String schema, String globalName, String localName) {
    if (zkClient == null) {
      LOG.warn("DBZkClient is not initialized, fail to register {} to {}/{}", localName, schema, globalName);
      return true;
    }
    return zkClient.registerTable2Schema(schema, globalName, endpoint, localName);
  }

  protected abstract TableInfo loadTableInfo(ServerConfig.Table table);

  protected abstract void oneDBQueryInternal(OneDBQuery query, DataSet dataSet) throws SQLException;
}
