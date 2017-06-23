/**
 * Copyright 2017 LinkedIn Corp. All rights reserved.
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
package com.github.ambry.messageformat;

import com.github.ambry.utils.SystemTime;
import com.github.ambry.utils.Utils;

import static com.github.ambry.messageformat.BlobProperties.*;


/**
 * Utility class to generate {@link BlobProperties} based on the args passed with random values for missed out fields
 */
public class BlobPropertiesUtils {

  /**
   * @param blobSize The size of the blob in bytes
   * @param serviceId The service id that is creating this blob
   */
  public static BlobProperties getBlobProperties(long blobSize, String serviceId) {
    return new BlobProperties(blobSize, serviceId, null, null, false, Utils.Infinite_Time,
        SystemTime.getInstance().milliseconds(), BlobProperties.LEGACY_ACCOUNT_ID, BlobProperties.LEGACY_CONTAINER_ID,
        BlobProperties.LEGACY_ACCOUNT_ID);
  }

  /**
   * @param blobSize The size of the blob in bytes
   * @param serviceId The service id that is creating this blob
   * @param ownerId The owner of the blob (For example , memberId or groupId)
   * @param contentType The content type of the blob (eg: mime). Can be Null
   * @param isPrivate Is the blob secure
   * @param timeToLiveInSeconds The time to live, in seconds, relative to blob creation time.
   */
  public static BlobProperties getBlobProperties(long blobSize, String serviceId, String ownerId, String contentType,
      boolean isPrivate, long timeToLiveInSeconds) {
    return new BlobProperties(blobSize, serviceId, ownerId, contentType, isPrivate, timeToLiveInSeconds,
        SystemTime.getInstance().milliseconds(), BlobProperties.LEGACY_ACCOUNT_ID, BlobProperties.LEGACY_CONTAINER_ID,
        BlobProperties.LEGACY_ACCOUNT_ID);
  }

  /**
   * @param blobSize The size of the blob in bytes
   * @param serviceId The service id that is creating this blob
   * @param ownerId The owner of the blob (For example , memberId or groupId)
   * @param contentType The content type of the blob (eg: mime). Can be Null
   * @param isPrivate Is the blob secure
   * @param timeToLiveInSeconds The time to live, in seconds, relative to blob creation time.
   * @param creationTimeInMs The time at which the blob is created.
   */
  public static BlobProperties getBlobProperties(long blobSize, String serviceId, String ownerId, String contentType,
      boolean isPrivate, long timeToLiveInSeconds, long creationTimeInMs) {
    return new BlobProperties(blobSize, serviceId, ownerId, contentType, isPrivate, timeToLiveInSeconds,
        creationTimeInMs, LEGACY_ACCOUNT_ID, LEGACY_CONTAINER_ID, LEGACY_ACCOUNT_ID);
  }
}
