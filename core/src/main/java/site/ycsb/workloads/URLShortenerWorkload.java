/**
 * Copyright (c) 2010 Yahoo! Inc., Copyright (c) 2016-2017 YCSB contributors. All rights reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You
 * may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License. See accompanying
 * LICENSE file.
 */

package site.ycsb.workloads;

import site.ycsb.*;
import site.ycsb.generator.*;
import site.ycsb.measurements.Measurements;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.util.*;

/**
 * Properties to control the client:
 * <UL>
 * <LI><b>fieldlength</b>: the size of each field (default: 100)
 * <LI><b>minfieldlength</b>: the minimum size of each field (default: 1)
 * <LI><b>readproportion</b>: what proportion of operations should be reads (default: 0.99)
 * <LI><b>insertproportion</b>: what proportion of operations should be inserts (default: 0.01)
 * <LI><b>requestdistribution</b>: what distribution should be used to select the records to operate
 * on - uniform, zipfian, hotspot, sequential, exponential or latest (default: zipfian)
 * <LI><b>insertstart</b>: for parallel loads and runs, defines the starting record for this
 * YCSB instance (default: 0)
 * <LI><b>insertcount</b>: for parallel loads and runs, defines the number of records for this
 * YCSB instance (default: recordcount)
 * <LI><b>insertorder</b>: should records be inserted in order by key ("ordered"), or in hashed
 * order ("hashed") (default: hashed)
 * </ul>
 */
public class URLShortenerWorkload extends Workload {
  /**
   * The name of the database table to run queries against.
   */
  public static final String TABLENAME_PROPERTY = "table";

  /**
   * The default name of the database table to run queries against.
   */
  public static final String TABLENAME_PROPERTY_DEFAULT = "aliases";

  protected String table;

  private List<String> fieldnames;

  /**
   * The name of the property for the field length distribution. Options are "uniform", "zipfian"
   * (favouring short records), "constant", and "histogram".
   * <p>
   * If "uniform", "zipfian" or "constant", the maximum field length will be that specified by the
   * fieldlength property. If "histogram", then the histogram will be read from the filename
   * specified in the "fieldlengthhistogram" property.
   */
  public static final String FIELD_LENGTH_DISTRIBUTION_PROPERTY = "fieldlengthdistribution";

  /**
   * The default field length distribution.
   */
  public static final String FIELD_LENGTH_DISTRIBUTION_PROPERTY_DEFAULT = "constant";

  /**
   * The name of the property for the length of a field in bytes.
   */
  public static final String FIELD_LENGTH_PROPERTY = "fieldlength";

  /**
   * The default maximum length of a field in bytes.
   */
  public static final String FIELD_LENGTH_PROPERTY_DEFAULT = "100";

  /**
   * The name of the property for the minimum length of a field in bytes.
   */
  public static final String MIN_FIELD_LENGTH_PROPERTY = "minfieldlength";

  /**
   * The default minimum length of a field in bytes.
   */
  public static final String MIN_FIELD_LENGTH_PROPERTY_DEFAULT = "1";

  /**
   * The name of a property that specifies the filename containing the field length histogram (only
   * used if fieldlengthdistribution is "histogram").
   */
  public static final String FIELD_LENGTH_HISTOGRAM_FILE_PROPERTY = "fieldlengthhistogram";

  /**
   * The default filename containing a field length histogram.
   */
  public static final String FIELD_LENGTH_HISTOGRAM_FILE_PROPERTY_DEFAULT = "hist.txt";

  /**
   * Generator object that produces field lengths.  The value of this depends on the properties that
   * start with "FIELD_LENGTH_".
   */
  protected NumberGenerator fieldlengthgenerator;

  /**
   * The name of the property for deciding whether to check all returned
   * data against the formation template to ensure data integrity.
   */
  public static final String DATA_INTEGRITY_PROPERTY = "dataintegrity";

  /**
   * The default value for the dataintegrity property.
   */
  public static final String DATA_INTEGRITY_PROPERTY_DEFAULT = "false";

  /**
   * Set to true if want to check correctness of reads. Must also
   * be set to true during loading phase to function.
   */
  private boolean dataintegrity;

  /**
   * The name of the property for the proportion of transactions that are reads.
   */
  public static final String READ_PROPORTION_PROPERTY = "readproportion";

  /**
   * The default proportion of transactions that are reads.
   */
  public static final String READ_PROPORTION_PROPERTY_DEFAULT = "0.99";

  /**
   * The name of the property for the proportion of transactions that are inserts.
   */
  public static final String INSERT_PROPORTION_PROPERTY = "insertproportion";

  /**
   * The default proportion of transactions that are inserts.
   */
  public static final String INSERT_PROPORTION_PROPERTY_DEFAULT = "0.01";

  /**
   * The name of the property for the the distribution of requests across the keyspace. Options are
   * "uniform", "zipfian" and "latest"
   */
  public static final String REQUEST_DISTRIBUTION_PROPERTY = "requestdistribution";

  /**
   * The default distribution of requests across the keyspace.
   */
  public static final String REQUEST_DISTRIBUTION_PROPERTY_DEFAULT = "zipfian";

  /**
   * The name of the property for the order to insert records. Options are "ordered" or "hashed"
   */
  public static final String INSERT_ORDER_PROPERTY = "insertorder";

  /**
   * Default insert order.
   */
  public static final String INSERT_ORDER_PROPERTY_DEFAULT = "hashed";

  /**
   * Percentage data items that constitute the hot set.
   */
  public static final String HOTSPOT_DATA_FRACTION = "hotspotdatafraction";

  /**
   * Default value of the size of the hot set.
   */
  public static final String HOTSPOT_DATA_FRACTION_DEFAULT = "0.2";

  /**
   * Percentage operations that access the hot set.
   */
  public static final String HOTSPOT_OPN_FRACTION = "hotspotopnfraction";

  /**
   * Default value of the percentage operations accessing the hot set.
   */
  public static final String HOTSPOT_OPN_FRACTION_DEFAULT = "0.8";

  public static final String URL_FIELD_NAME = "url";

  public static final String TIMESTAMP_FIELD_NAME = "expires";

  public static final int KEY_LENGTH = 6;

  protected NumberGenerator keysequence;
  protected DiscreteGenerator operationchooser;
  protected NumberGenerator keychooser;
  protected NumberGenerator fieldchooser;
  protected AcknowledgedCounterGenerator transactioninsertkeysequence;
  protected boolean orderedinserts;
  protected long fieldcount;
  protected long recordcount;

  private Measurements measurements = Measurements.getMeasurements();

  protected static NumberGenerator getFieldLengthGenerator(Properties p) throws WorkloadException {
    NumberGenerator fieldlengthgenerator;
    String fieldlengthdistribution = p.getProperty(
        FIELD_LENGTH_DISTRIBUTION_PROPERTY, FIELD_LENGTH_DISTRIBUTION_PROPERTY_DEFAULT);
    int fieldlength =
        Integer.parseInt(p.getProperty(FIELD_LENGTH_PROPERTY, FIELD_LENGTH_PROPERTY_DEFAULT));
    int minfieldlength =
        Integer.parseInt(p.getProperty(MIN_FIELD_LENGTH_PROPERTY, MIN_FIELD_LENGTH_PROPERTY_DEFAULT));
    String fieldlengthhistogram = p.getProperty(
        FIELD_LENGTH_HISTOGRAM_FILE_PROPERTY, FIELD_LENGTH_HISTOGRAM_FILE_PROPERTY_DEFAULT);
    if (fieldlengthdistribution.compareTo("constant") == 0) {
      fieldlengthgenerator = new ConstantIntegerGenerator(fieldlength);
    } else if (fieldlengthdistribution.compareTo("uniform") == 0) {
      fieldlengthgenerator = new UniformLongGenerator(minfieldlength, fieldlength);
    } else if (fieldlengthdistribution.compareTo("zipfian") == 0) {
      fieldlengthgenerator = new ZipfianGenerator(minfieldlength, fieldlength);
    } else if (fieldlengthdistribution.compareTo("histogram") == 0) {
      try {
        fieldlengthgenerator = new HistogramGenerator(fieldlengthhistogram);
      } catch (IOException e) {
        throw new WorkloadException(
            "Couldn't read field length histogram file: " + fieldlengthhistogram, e);
      }
    } else {
      throw new WorkloadException(
          "Unknown field length distribution \"" + fieldlengthdistribution + "\"");
    }
    return fieldlengthgenerator;
  }

  /**
   * Initialize the scenario.
   * Called once, in the main client thread, before any operations are started.
   */
  @Override
  public void init(Properties p) throws WorkloadException {
    table = p.getProperty(TABLENAME_PROPERTY, TABLENAME_PROPERTY_DEFAULT);

    fieldnames = new ArrayList<>();
    fieldnames.add(URL_FIELD_NAME);
    fieldnames.add(TIMESTAMP_FIELD_NAME);

    fieldlengthgenerator = URLShortenerWorkload.getFieldLengthGenerator(p);

    recordcount =
        Long.parseLong(p.getProperty(Client.RECORD_COUNT_PROPERTY, Client.DEFAULT_RECORD_COUNT));
    if (recordcount == 0) {
      recordcount = Integer.MAX_VALUE;
    }
    String requestdistrib =
        p.getProperty(REQUEST_DISTRIBUTION_PROPERTY, REQUEST_DISTRIBUTION_PROPERTY_DEFAULT);

    long insertstart =
        Long.parseLong(p.getProperty(INSERT_START_PROPERTY, INSERT_START_PROPERTY_DEFAULT));
    long insertcount=
        Integer.parseInt(p.getProperty(INSERT_COUNT_PROPERTY, String.valueOf(recordcount - insertstart)));
    // Confirm valid values for insertstart and insertcount in relation to recordcount
    if (recordcount < (insertstart + insertcount)) {
      System.err.println("Invalid combination of insertstart, insertcount and recordcount.");
      System.err.println("recordcount must be bigger than insertstart + insertcount.");
      System.exit(-1);
    }

    dataintegrity = Boolean.parseBoolean(
        p.getProperty(DATA_INTEGRITY_PROPERTY, DATA_INTEGRITY_PROPERTY_DEFAULT));
    // Confirm that fieldlengthgenerator returns a constant if data
    // integrity check requested.
    if (dataintegrity && !(p.getProperty(
        FIELD_LENGTH_DISTRIBUTION_PROPERTY,
        FIELD_LENGTH_DISTRIBUTION_PROPERTY_DEFAULT)).equals("constant")) {
      System.err.println("Must have constant field size to check data integrity.");
      System.exit(-1);
    }

    if (p.getProperty(INSERT_ORDER_PROPERTY, INSERT_ORDER_PROPERTY_DEFAULT).compareTo("hashed") == 0) {
      orderedinserts = false;
    } else {
      orderedinserts = true;
    }

    keysequence = new CounterGenerator(insertstart);
    operationchooser = createOperationGenerator(p);

    transactioninsertkeysequence = new AcknowledgedCounterGenerator(recordcount);
    if (requestdistrib.compareTo("uniform") == 0) {
      keychooser = new UniformLongGenerator(insertstart, insertstart + insertcount - 1);
    } else if (requestdistrib.compareTo("exponential") == 0) {
      double percentile = Double.parseDouble(p.getProperty(
          ExponentialGenerator.EXPONENTIAL_PERCENTILE_PROPERTY,
          ExponentialGenerator.EXPONENTIAL_PERCENTILE_DEFAULT));
      double frac = Double.parseDouble(p.getProperty(
          ExponentialGenerator.EXPONENTIAL_FRAC_PROPERTY,
          ExponentialGenerator.EXPONENTIAL_FRAC_DEFAULT));
      keychooser = new ExponentialGenerator(percentile, recordcount * frac);
    } else if (requestdistrib.compareTo("sequential") == 0) {
      keychooser = new SequentialGenerator(insertstart, insertstart + insertcount - 1);
    } else if (requestdistrib.compareTo("zipfian") == 0) {
      // it does this by generating a random "next key" in part by taking the modulus over the
      // number of keys.
      // If the number of keys changes, this would shift the modulus, and we don't want that to
      // change which keys are popular so we'll actually construct the scrambled zipfian generator
      // with a keyspace that is larger than exists at the beginning of the test. that is, we'll predict
      // the number of inserts, and tell the scrambled zipfian generator the number of existing keys
      // plus the number of predicted keys as the total keyspace. then, if the generator picks a key
      // that hasn't been inserted yet, will just ignore it and pick another key. this way, the size of
      // the keyspace doesn't change from the perspective of the scrambled zipfian generator
      final double insertproportion = Double.parseDouble(
          p.getProperty(INSERT_PROPORTION_PROPERTY, INSERT_PROPORTION_PROPERTY_DEFAULT));
      int opcount = Integer.parseInt(p.getProperty(Client.OPERATION_COUNT_PROPERTY));
      int expectednewkeys = (int) ((opcount) * insertproportion * 2.0); // 2 is fudge factor

      keychooser = new ScrambledZipfianGenerator(insertstart, insertstart + insertcount + expectednewkeys);
    } else if (requestdistrib.compareTo("latest") == 0) {
      keychooser = new SkewedLatestGenerator(transactioninsertkeysequence);
    } else if (requestdistrib.equals("hotspot")) {
      double hotsetfraction =
          Double.parseDouble(p.getProperty(HOTSPOT_DATA_FRACTION, HOTSPOT_DATA_FRACTION_DEFAULT));
      double hotopnfraction =
          Double.parseDouble(p.getProperty(HOTSPOT_OPN_FRACTION, HOTSPOT_OPN_FRACTION_DEFAULT));
      keychooser = new HotspotIntegerGenerator(insertstart, insertstart + insertcount - 1,
          hotsetfraction, hotopnfraction);
    } else {
      throw new WorkloadException("Unknown request distribution \"" + requestdistrib + "\"");
    }

    fieldchooser = new UniformLongGenerator(0, fieldcount - 1);
  }

  private byte[] longToBytes(long x) {
    ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
    buffer.putLong(x);
    return buffer.array();
  }

  protected String buildKeyName(long keynum) {
    String value = Long.toString(keynum);
    if (!orderedinserts) {
      try {
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        byte[] hash = md5.digest(longToBytes(keynum));

        String key = Base64.getEncoder().encodeToString(hash);
        value = key.substring(KEY_LENGTH);
      } catch (NoSuchAlgorithmException e) {
        throw new RuntimeException("Needs MD5 hash installed.");
      }
    }
    return value;
  }

  /**
   * Builds values for all fields.
   */
  private HashMap<String, ByteIterator> buildValues(String key) {
    HashMap<String, ByteIterator> values = new HashMap<>();

    for (String fieldkey : fieldnames) {
      ByteIterator data;

      if (fieldkey == TIMESTAMP_FIELD_NAME) {
        // TODO generate timestamps correctly
        data = new StringByteIterator(new Timestamp(System.currentTimeMillis()).toString());
      } else {
        if (dataintegrity) {
          data = new StringByteIterator(buildDeterministicValue(key, fieldkey));
        } else {
          // fill with random data
          data = new RandomByteIterator(fieldlengthgenerator.nextValue().longValue());
        }
      }

      values.put(fieldkey, data);
    }
    return values;
  }

  /**
   * Build a deterministic value given the key information.
   */
  private String buildDeterministicValue(String key, String fieldkey) {
    int size = fieldlengthgenerator.nextValue().intValue();
    StringBuilder sb = new StringBuilder(size);
    sb.append(key);
    sb.append(':');
    sb.append(fieldkey);
    while (sb.length() < size) {
      sb.append(':');
      sb.append(sb.toString().hashCode());
    }
    sb.setLength(size);

    return sb.toString();
  }

  /**
   * Do one insert operation. Because it will be called concurrently from multiple client threads,
   * this function must be thread safe. However, avoid synchronized, or the threads will block waiting
   * for each other, and it will be difficult to reach the target throughput. Ideally, this function would
   * have no side effects other than DB operations.
   */
  @Override
  public boolean doInsert(DB db, Object threadstate) {
    int keynum = keysequence.nextValue().intValue();
    String dbkey = buildKeyName(keynum);
    HashMap<String, ByteIterator> values = buildValues(dbkey);

    Status status = db.insert(table, dbkey, values);
    if (status == null || !status.isOk()) {
      System.err.println("Error inserting");
    }
    return null != status && status.isOk();
  }

  /**
   * Do one transaction operation. Because it will be called concurrently from multiple client
   * threads, this function must be thread safe. However, avoid synchronized, or the threads will block waiting
   * for each other, and it will be difficult to reach the target throughput. Ideally, this function would
   * have no side effects other than DB operations.
   */
  @Override
  public boolean doTransaction(DB db, Object threadstate) {
    String operation = operationchooser.nextString();
    if(operation == null) {
      return false;
    }

    switch (operation) {
    case "READ":
      doTransactionRead(db);
      break;
    case "INSERT":
      doTransactionInsert(db);
      break;
    default:
      throw new RuntimeException("This workload supports only read and insert operations.");
    }
    return true;
  }

  /**
   * Results are reported in the first three buckets of the histogram under
   * the label "VERIFY".
   * Bucket 0 means the expected data was returned.
   * Bucket 1 means incorrect data was returned.
   * Bucket 2 means null data was returned when some data was expected.
   */
  protected void verifyRow(String key, HashMap<String, ByteIterator> cells) {
    Status verifyStatus = Status.OK;
    long startTime = System.nanoTime();
    if (!cells.isEmpty()) {
      for (Map.Entry<String, ByteIterator> entry : cells.entrySet()) {
        if (!entry.getValue().toString().equals(buildDeterministicValue(key, entry.getKey()))) {
          verifyStatus = Status.UNEXPECTED_STATE;
          break;
        }
      }
    } else {
      // This assumes that null data is never valid
      verifyStatus = Status.ERROR;
    }
    long endTime = System.nanoTime();
    measurements.measure("VERIFY", (int) (endTime - startTime) / 1000);
    measurements.reportStatus("VERIFY", verifyStatus);
  }

  long nextKeynum() {
    long keynum;
    if (keychooser instanceof ExponentialGenerator) {
      do {
        keynum = transactioninsertkeysequence.lastValue() - keychooser.nextValue().intValue();
      } while (keynum < 0);
    } else {
      do {
        keynum = keychooser.nextValue().intValue();
      } while (keynum > transactioninsertkeysequence.lastValue());
    }
    return keynum;
  }

  public void doTransactionRead(DB db) {
    // choose a random key
    long keynum = nextKeynum();
    String keyname = buildKeyName(keynum);

    HashSet<String> fields = null;


    if (dataintegrity) {
      fields = new HashSet<String>();
      fields.add(URL_FIELD_NAME);
    }

    HashMap<String, ByteIterator> cells = new HashMap<String, ByteIterator>();
    db.read(table, keyname, fields, cells);

    if (dataintegrity) {
      verifyRow(keyname, cells);
    }
  }

  public void doTransactionInsert(DB db) {
    // choose the next key
    long keynum = transactioninsertkeysequence.nextValue();

    try {
      String dbkey = buildKeyName(keynum);

      HashMap<String, ByteIterator> values = buildValues(dbkey);
      db.insert(table, dbkey, values);
    } finally {
      transactioninsertkeysequence.acknowledge(keynum);
    }
  }

  /**
   * @param p The properties list to pull weights from.
   * @return A generator that can be used to determine the next operation to perform.
   * @throws IllegalArgumentException if the properties object was null.
   */
  protected static DiscreteGenerator createOperationGenerator(final Properties p) {
    if (p == null) {
      throw new IllegalArgumentException("Properties object cannot be null");
    }
    final double readproportion = Double.parseDouble(
        p.getProperty(READ_PROPORTION_PROPERTY, READ_PROPORTION_PROPERTY_DEFAULT));
    final double insertproportion = Double.parseDouble(
        p.getProperty(INSERT_PROPORTION_PROPERTY, INSERT_PROPORTION_PROPERTY_DEFAULT));

    final DiscreteGenerator operationchooser = new DiscreteGenerator();
    if (readproportion > 0) {
      operationchooser.addValue(readproportion, "READ");
    }
    if (insertproportion > 0) {
      operationchooser.addValue(insertproportion, "INSERT");
    }

    return operationchooser;
  }
}
