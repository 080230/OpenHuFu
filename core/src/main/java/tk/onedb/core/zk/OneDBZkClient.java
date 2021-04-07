package tk.onedb.core.zk;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.InvalidProtocolBufferException;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.data.ACL;

import tk.onedb.core.client.OneDBClient;
import tk.onedb.core.data.Header;
import tk.onedb.core.data.TableInfo;
import tk.onedb.core.table.OneDBTableInfo;
import tk.onedb.core.zk.watcher.EndpointWatcher;
import tk.onedb.core.zk.watcher.TableInfoWatcher;
import tk.onedb.rpc.OneDBCommon.HeaderProto;


public class OneDBZkClient extends ZkClient {

  private final OneDBClient client;
  private List<String> endpoints;
  private final String schemaRootPath;
  private final String schemaDirectoryPath;
  private final ReadWriteLock lock;
  private final List<ACL> ONEDB_AUTH;

  public OneDBZkClient(String servers, String zkRootPath, OneDBClient client, String directory, byte[] digest) throws IOException, KeeperException, InterruptedException {
    super(servers, zkRootPath);
    this.client = client;
    this.lock = new ReentrantReadWriteLock();
    this.schemaRootPath = zkRootPath + "/schema";
    this.schemaDirectoryPath = buildPath(schemaRootPath, directory);
    ONEDB_AUTH = new ArrayList<>();
    ONEDB_AUTH.addAll(Ids.READ_ACL_UNSAFE);
    ONEDB_AUTH.addAll(Ids.CREATOR_ALL_ACL);
    init();
  }

  private void init() throws KeeperException, InterruptedException {
    initRootPath();
    initSchemaPath();
    List<String> endpoints= zk.getChildren(endpointRootPath, new EndpointWatcher(client, zk, endpointRootPath));
    this.endpoints = endpoints;
  }

  private void initSchemaPath() throws KeeperException, InterruptedException {
    if (zk.exists(schemaRootPath, false) == null) {
      LOG.info("Create SchemaPath: {}", schemaRootPath);
      zk.create(schemaRootPath, null, Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
      LOG.info("Create Schema Directory: {}", schemaDirectoryPath);
      zk.create(schemaDirectoryPath, null, ONEDB_AUTH, CreateMode.PERSISTENT);
    } else {
      LOG.info("SchemaPath {} already exists", schemaRootPath);
      if (zk.exists(schemaDirectoryPath, false) == null)  {
        LOG.info("Create Schema Directory: {}", schemaDirectoryPath);
        zk.create(schemaDirectoryPath, null, ONEDB_AUTH, CreateMode.PERSISTENT);
      } else {
        LOG.info("Schema Directory {} already exists", schemaDirectoryPath);
      }
    }
  }

  public void watchEndpoint(String endpoint) throws KeeperException, InterruptedException {
    String path = buildPath(endpointRootPath, endpoint);
    List<String> tableNames = zk.getChildren(path, new TableInfoWatcher(client, zk, path));
    for (String tableName : tableNames) {
      System.out.println(tableName);
      String tablePath = buildPath(path, tableName);
      zk.getData(tablePath, new TableInfoWatcher(client, zk, path), null);
    }
  }

  public List<TableInfo> getTableInfoList(String endpoint) throws KeeperException, InterruptedException {
    String path = buildPath(endpointRootPath, endpoint);
    List<String> tableNames = zk.getChildren(path, new TableInfoWatcher(client, zk, path));
    List<TableInfo> result = new ArrayList<>();
    for (String tableName : tableNames) {
      System.out.println(tableName);
      String tablePath = buildPath(path, tableName);
      byte[] headerBytes = zk.getData(tablePath, false, null);
      try {
        HeaderProto headerProto = HeaderProto.parseFrom(headerBytes);
        result.add(TableInfo.of(tableName, Header.fromProto(headerProto)));
      } catch (InvalidProtocolBufferException e) {
        LOG.error(path, e);
        e.printStackTrace();
      }
    }
    return ImmutableList.copyOf(result);
  }

  public List<String> getEndpointTableNameList(String endpoint) throws KeeperException, InterruptedException {
    String path = buildPath(endpointRootPath, endpoint);
    return zk.getChildren(path, false, null);
  }

  public TableInfo watchTable(String endpoint, String tableName) throws KeeperException, InterruptedException, InvalidProtocolBufferException {
    String path = buildPath(buildPath(endpointRootPath, endpoint), tableName);
    byte[] header = zk.getData(path, new TableInfoWatcher(client, zk, path), null);
    return TableInfo.of(tableName, Header.fromProto(HeaderProto.parseFrom(header)));
  }

  public void createGlobalTable(TableInfo table) throws KeeperException, InterruptedException {
    String path = buildPath(schemaDirectoryPath, table.getName());
    zk.create(path, table.getHeader().toProto().toByteArray(), ONEDB_AUTH, CreateMode.PERSISTENT);
  }

  public void addLocalTable(String globalTableName, String endpoint, String localTableName) {
    OneDBTableInfo globalTable = client.getTable(globalTableName);
    if (globalTable == null) {
      LOG.error("Gloabl table {} not exists", globalTableName);
      return;
    }
    try {
      TableInfo localTable = watchTable(endpoint, localTableName);
      if (localTable.getHeader().equals(globalTable.getHeader())) {
        addLocalTable(globalTableName, endpoint, localTableName);
      }
    } catch (InvalidProtocolBufferException e) {
      LOG.error("Failed to parse header of table {} in {}", localTableName, endpoint);
    } catch (KeeperException | InterruptedException e) {
      LOG.error("Error when get data of table {} in {}: {}", localTableName, endpoint, e.getMessage());
    }
  }

  public void removeLocalTable(String globalTableName, String endpoint, String localTableName) {
    client.removeLocalTable(globalTableName, endpoint, localTableName);
  }

  public void removeDB(String endpoint) {
    client.removeDB(endpoint);
  }

  public void removeLocalTable(String endpoint, String localTableName) {
    client.removeLocalTable(endpoint, localTableName);
  }

  public static void main(String[] args) {
    try {
      OneDBZkClient client = new OneDBZkClient("localhost:12349", "/onedb", null, "user1", "user1:user1".getBytes());
      Thread.sleep(1000000);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
