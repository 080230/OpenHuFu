package tk.onedb.core.data;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.google.protobuf.ByteString;

import org.apache.commons.lang3.SerializationUtils;

import tk.onedb.rpc.OneDBCommon.DataSetProto;
import tk.onedb.rpc.OneDBCommon.RowsProto;

public class BasicDataSet extends EnumerableDataSet {
  List<Row> rows;
  int cursor = 0;
  Row current = null;

  BasicDataSet(Header header, List<Row> rows) {
    super(header);
    this.rows = rows;
  }

  BasicDataSet(Header header) {
    super(header);
    rows = new ArrayList<>();
  }

  public DataSetProto toProto() {
    DataSetProto.Builder proto = DataSetProto.newBuilder();
    proto.setHeader(header.toProto());
    RowsProto.Builder rowsProto = RowsProto.newBuilder();
    rows.stream().map(row -> rowsProto.addRow(ByteString.copyFrom(SerializationUtils.serialize(row))));
    return proto.setRows(rowsProto).build();
  }

  public static DataSet fromProto(DataSetProto proto) {
    Header header = Header.fromProto(proto.getHeader());
    RowsProto rowsProto = proto.getRows();
    List<Row> rows = rowsProto.getRowList().stream()
        .map(bytes -> (Row) SerializationUtils.deserialize(bytes.toByteArray())).collect(Collectors.toList());
    return new BasicDataSet(header, rows);
  }

  @Override
  public Row current() {
    return current;
  }

  @Override
  public boolean moveNext() {
    if (cursor >= rows.size()) {
      return false;
    } else {
      current = rows.get(cursor);
      cursor++;
      return true;
    }
  }

  @Override
  public void reset() {
    cursor = 0;
    current = null;
  }

  @Override
  public void close() {
    // do nothing
  }

  @Override
  public int getRowCount() {
    return rows.size();
  }

  @Override
  public void addRow(Row row) {
    rows.add(row);
  }

  @Override
  public void addRows(List<Row> rows) {
    rows.addAll(rows);
  }

  @Override
  public void mergeDataSet(DataSet dataSet) {
    rows.addAll(dataSet.getRows());
  }

  @Override
  List<Row> getRows() {
    return rows;
  }
}
