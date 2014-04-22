package com.hpcloud.mon.infrastructure.persistence;

import java.sql.Timestamp;
import java.util.*;

import javax.inject.Inject;
import javax.inject.Named;

import com.hpcloud.mon.domain.model.statistic.Statistics;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.DBI;

import com.hpcloud.mon.domain.model.statistic.StatisticRepository;

import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.Query;

/**
 * Vertica statistic repository implementation.
 */
public class StatisticRepositoryImpl implements StatisticRepository {
  private final DBI db;

  private static final String FIND_BY_METRIC_DEF_SQL = "select dd.id, def.name, d.name as dname, d.value as dvalue "
      + "from MonMetrics.Definitions def, MonMetrics.DefinitionDimensions dd "
      + "left outer join MonMetrics.Dimensions d on d.dimension_set_id = dd.dimension_set_id%s "
      + "where def.id = dd.definition_id and def.tenant_id = :tenantId%s order by dd.id";

  @Inject
  public StatisticRepositoryImpl(@Named("vertica") DBI db) {
    this.db = db;
  }

  @Override
  public List<Statistics> find(String tenantId, String name, Map<String, String> dimensions,
      DateTime startTime, DateTime endTime, List<String> statistics, int period) {
    Handle h = db.open();
    List<Statistics> listStats = new ArrayList<>();
    List<String> copyStatistics = createColumns(statistics);

    try {
      Map<byte[], Statistics> byteMap = findDefIds(h, tenantId, name, dimensions, startTime, endTime);

      for (byte[] bufferId : byteMap.keySet()) {

        Query<Map<String, Object>> query = h.createQuery(
            createQuery(period, startTime, endTime, statistics))
            .bind("definition_id", bufferId)
            .bind("start_time", startTime)
            .bind("end_time", endTime);

        // Execute
        List<Map<String, Object>> rows = query.list();
        List<Object> statisticsRow = new ArrayList<Object>();

        for (Map<String, Object> row : rows) {
          Double sum = (Double) row.get("sum");
          Double average = (Double) row.get("avg");
          Double min = (Double) row.get("min");
          Double max = (Double) row.get("max");
          Long count = (Long) row.get("count");
          Timestamp time_stamp = (Timestamp) row.get("time_interval");

          if (time_stamp != null) {
            statisticsRow.add(time_stamp.getTime());
          }

          if (average != null) {
            statisticsRow.add(average);
          }
          if (count != null) {
            statisticsRow.add(count);
          }
          if (max != null) {
            statisticsRow.add(max);
          }
          if (min != null) {
            statisticsRow.add(min);
          }
          if (sum != null) {
            statisticsRow.add(sum);
          }
          byteMap.get(bufferId).addValues(statisticsRow);
          statisticsRow = new ArrayList<>();
        }

        byteMap.get(bufferId).setColumns(copyStatistics);
        listStats.add(byteMap.get(bufferId));
      }

    } finally {
      h.close();
    }
    return listStats;
  }

  private Map<byte[], Statistics> findDefIds(Handle h, String tenantId, String name,
      Map<String, String> dimensions, DateTime startTime, DateTime endTime) {
    List<byte[]> bytes = new ArrayList<>();

    // Build query
    StringBuilder sbWhere = new StringBuilder();

    if (name != null)
      sbWhere.append(" and def.name = :name");

    if (endTime != null) {
      sbWhere.append(" and m.time_stamp <= :endTime");
    }

    String sql = String.format(FIND_BY_METRIC_DEF_SQL,
        MetricQueries.buildJoinClauseFor(dimensions), sbWhere);

    Query<Map<String, Object>> query = h.createQuery(sql)
        .bind("tenantId", tenantId)
        .bind("startTime", startTime);

    if (name != null) {
      query.bind("name", name);
    }

    if (endTime != null) {
      query.bind("endTime", new Timestamp(endTime.getMillis()));
    }

    MetricQueries.bindDimensionsToQuery(query, dimensions);

    // Execute
    List<Map<String, Object>> rows = query.list();

    Map<byte[], Statistics> byteIdMap = new HashMap<>();

    // Build results
    byte[] currentId = null;
    Map<String, String> dims = null;
    for (Map<String, Object> row : rows) {
      byte[] defId = (byte[]) row.get("id");
      String defName = (String) row.get("name");
      String demName = (String) row.get("dname");
      String demValue = (String) row.get("dvalue");

      if (defId == null || !Arrays.equals(currentId, defId)) {
        currentId = defId;
        dims = new HashMap<>();
        dims.put(demName, demValue);

        Statistics statistics = new Statistics();
        statistics.setName(defName);
        statistics.setDimensions(dims);
        byteIdMap.put(currentId, statistics);
      } else
        dims.put(demName, demValue);
    }

    bytes.add(currentId);

    return byteIdMap;
  }

  List<String> createColumns(List<String> list) {
    List<String> copy = new ArrayList<>();
    for (String string : list) {
      copy.add(string);
    }
    Collections.sort(copy);
    copy.add(0, "timestamp");

    return copy;
  }

  private String createQuery(int period, DateTime startTime, DateTime endTime,
      List<String> statistics) {
    StringBuilder builder = new StringBuilder();

    builder.append("SELECT " + getColumns(statistics));

    if (period >= 1) {
      builder.append(",MIN(time_stamp) as time_interval ");
      builder.append(" FROM (Select FLOOR((EXTRACT('epoch' from time_stamp) - ");
      builder.append(createOffset(period, startTime, endTime));
      builder.append(" AS time_slice, time_stamp, value ");
    }

    builder.append(" FROM MonMetrics.Measurements ");
    builder.append("WHERE definition_dimensions_id = :definition_id ");
    builder.append(createWhereClause(startTime, endTime));

    if (period >= 1) {
      builder.append(") as TimeSlices group by time_slice order by time_slice");
    }
    return builder.toString();
  }

  private String createWhereClause(DateTime startTime, DateTime endTime) {
    String clause = "";
    if (startTime != null && endTime != null) {
      clause = "AND time_stamp >= :start_time AND time_stamp <= :end_time ";
    } else if (startTime != null) {
      clause = "AND time_stamp >= :start_time ";
    }
    return clause;
  }

  private String createOffset(int period, DateTime startTime, DateTime endTime) {

    StringBuilder offset = new StringBuilder();
    offset.append("(select mod((select extract('epoch' from time_stamp)  from MonMetrics.Measurements ");
    offset.append("WHERE definition_dimensions_id = :definition_id ");
    offset.append(createWhereClause(startTime, endTime));
    offset.append("order by time_stamp limit 1");
    offset.append("),");
    offset.append(period + ")))/" + period + ")");

    return offset.toString();
  }

  private String getColumns(List<String> statistics) {
    StringBuilder buildColumns = new StringBuilder();

    int size = statistics.size();
    int count = 0;
    for (String statistic : statistics) {
      if (statistic.equals("average")) {
        buildColumns.append("avg(value) as average ");
      } else {
        buildColumns.append(statistic + "(value) as " + statistic + " ");
      }

      if (size - 1 > count) {
        buildColumns.append(",");
      }
      count++;
    }
    return buildColumns.toString();
  }
}