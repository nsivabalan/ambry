/**
 * Copyright 2016 LinkedIn Corp. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 */
package com.github.ambry.store;

import com.github.ambry.clustermap.ClusterMap;
import com.github.ambry.clustermap.ClusterMapManager;
import com.github.ambry.clustermap.PartitionId;
import com.github.ambry.config.ClusterMapConfig;
import com.github.ambry.config.VerifiableProperties;
import com.github.ambry.utils.Utils;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Tool to assist in dumping data from replica token file in ambry
 */
public class DumpReplicaTokenTool {

  private final ClusterMap clusterMap;
  // Refers to replicatoken file that needs to be dumped
  public final String fileToRead;
  // File path referring to the hardware layout
  public final String hardwareLayoutFilePath;
  // File path referring to the partition layout
  public final String partitionLayoutFilePath;

  private static final Logger logger = LoggerFactory.getLogger(DumpDataTool.class);

  public DumpReplicaTokenTool(VerifiableProperties verifiableProperties) throws IOException, JSONException {

    fileToRead = verifiableProperties.getString("file.to.read");
    hardwareLayoutFilePath = verifiableProperties.getString("hardware.layout.file.path");
    partitionLayoutFilePath = verifiableProperties.getString("partition.layout.file.path");

    if (!new File(hardwareLayoutFilePath).exists() || !new File(partitionLayoutFilePath).exists()) {
      throw new IllegalArgumentException("Hardware or Partition Layout file does not exist");
    }
    clusterMap = new ClusterMapManager(hardwareLayoutFilePath, partitionLayoutFilePath,
        new ClusterMapConfig(new VerifiableProperties(new Properties())));
  }

  public static void main(String args[]) {
    try {
      VerifiableProperties verifiableProperties = StoreToolsUtil.getVerifiableProperties(args);
      DumpReplicaTokenTool dumpReplicaTokenTool = new DumpReplicaTokenTool(verifiableProperties);
      dumpReplicaTokenTool.dumpReplicaToken();
    } catch (Exception e) {
      logger.error("Closed with exception ", e);
    }
  }

  /**
   * Dumps replica token file
   * @throws Exception
   */
  private void dumpReplicaToken() throws Exception {
    logger.info("Dumping replica token file " + fileToRead);
    DataInputStream stream = new DataInputStream(new FileInputStream(fileToRead));
    short version = stream.readShort();
    switch (version) {
      case 0:
        int Crc_Size = 8;
        StoreKeyFactory storeKeyFactory = Utils.getObj("com.github.ambry.commons.BlobIdFactory", clusterMap);
        FindTokenFactory findTokenFactory =
            Utils.getObj("com.github.ambry.store.StoreFindTokenFactory", storeKeyFactory);
        while (stream.available() > Crc_Size) {
          // read partition id
          PartitionId partitionId = clusterMap.getPartitionIdFromStream(stream);
          // read remote node host name
          String hostname = Utils.readIntString(stream);
          // read remote replica path
          String replicaPath = Utils.readIntString(stream);
          // read remote port
          int port = stream.readInt();
          // read total bytes read from local store
          long totalBytesReadFromLocalStore = stream.readLong();
          // read replica token
          FindToken token = findTokenFactory.getFindToken(stream);
          logger.info(
              "partitionId " + partitionId + " hostname " + hostname + " replicaPath " + replicaPath + " port " + port
                  + " totalBytesReadFromLocalStore " + totalBytesReadFromLocalStore + " token " + token);
        }
        logger.info("crc " + stream.readLong());
    }
  }
}
