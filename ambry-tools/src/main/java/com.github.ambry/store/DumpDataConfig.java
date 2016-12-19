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

import com.github.ambry.config.Config;
import com.github.ambry.config.VerifiableProperties;


/**
 * Configuration parameters required by {@link DumpDataTool}
 */
public class DumpDataConfig {

  /**
   * "The index file that needs to be dumped for comparison purposes
   */
  @Config("file.to.read")
  public final String fileToRead;

  /**
   * File path referring to the hardware layout
   */
  @Config("hardware.layout.file.path")
  public final String hardwareLayoutFilePath;

  /**
   * File path referring to the partition layout
   */
  @Config("partition.layout.file.path")
  public final String partitionLayoutFilePath;

  /**
   * The type of operation to perform
   */
  @Config("type.of.operation")
  public final String typeOfOperation;

  /**
   * Path referring to replica root directory
   */
  @Config("replica.root.directory")
  public final String replicaRootDirecotry;

  /**
   * Create a {@link DumpDataConfig} instance.
   * @param verifiableProperties the properties map to refer to.
   */
  public DumpDataConfig(VerifiableProperties verifiableProperties) {
    fileToRead = verifiableProperties.getString("file.to.read");
    hardwareLayoutFilePath = verifiableProperties.getString("hardware.layout.file.path");
    partitionLayoutFilePath = verifiableProperties.getString("partition.layout.file.path");
    typeOfOperation = verifiableProperties.getString("type.of.operation");
    replicaRootDirecotry = verifiableProperties.getString("replica.root.directory");
  }
}
