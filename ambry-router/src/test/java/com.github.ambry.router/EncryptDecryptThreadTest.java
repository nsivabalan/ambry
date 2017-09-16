/*
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
package com.github.ambry.router;

import com.github.ambry.clustermap.ClusterMap;
import com.github.ambry.clustermap.MockClusterMap;
import com.github.ambry.commons.BlobId;
import com.github.ambry.config.CryptoServiceConfig;
import com.github.ambry.config.KMSConfig;
import com.github.ambry.config.VerifiableProperties;
import com.github.ambry.utils.Pair;
import com.github.ambry.utils.TestUtils;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.crypto.spec.SecretKeySpec;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import static com.github.ambry.router.CryptoTestUtils.*;
import static com.github.ambry.utils.Utils.*;


/**
 * Tests {@link EncryptDecryptThread}
 */
public class EncryptDecryptThreadTest {

  private static final int MAX_DATA_SIZE = 10000;
  private static final int DEFAULT_KEY_SIZE = 64;
  private static final int RANDOM_KEY_SIZE_IN_BITS = 256;
  private static final String ENCRYPT_JOB_TYPE = "encrypt";
  private static final String DECRYPT_JOB_TYPE = "decrypt";
  private final BlockingQueue<EncryptDecryptThread.JobInfo> jobQueue;
  private final CryptoService<SecretKeySpec> cryptoService;
  private final KeyManagementService<SecretKeySpec> kms;
  private final ClusterMap referenceClusterMap;
  private final String defaultKey;
  private final VerifiableProperties verifiableProperties;
  private EncryptDecryptThread encryptDecryptThread;

  public EncryptDecryptThreadTest() throws IOException, GeneralSecurityException {
    jobQueue = new LinkedBlockingQueue<>();
    defaultKey = getRandomKey(DEFAULT_KEY_SIZE);
    Properties props = getKMSProperties(defaultKey, RANDOM_KEY_SIZE_IN_BITS);
    verifiableProperties = new VerifiableProperties((props));
    kms = new SingleKeyManagementServiceFactory(verifiableProperties).getKeyManagementService();
    cryptoService = new GCMCryptoServiceFactory(verifiableProperties).getCryptoService();
    encryptDecryptThread = new EncryptDecryptThread(jobQueue, cryptoService, kms);
    referenceClusterMap = new MockClusterMap();
    new Thread(encryptDecryptThread).start();
  }

  @After
  public void cleanup() {
    encryptDecryptThread.close();
    jobQueue.clear();
  }

  /**
   * Tests {@link EncryptDecryptThread} for happy path
   * @throws InterruptedException
   * @throws GeneralSecurityException
   */
  @Test
  public void testEncryptDecryptThread() throws InterruptedException, GeneralSecurityException {
    int totalDataCount = 10;
    CountDownLatch encryptCallBackCount = new CountDownLatch(totalDataCount);
    CountDownLatch decryptCallBackCount = new CountDownLatch(totalDataCount);
    SecretKeySpec perBlobKey = kms.getRandomKey();
    for (int i = 0; i < totalDataCount; i++) {
      testEncryptDecryptFlow(perBlobKey, encryptCallBackCount, decryptCallBackCount);
    }
    awaitCountDownLatch(encryptCallBackCount, ENCRYPT_JOB_TYPE);
    awaitCountDownLatch(decryptCallBackCount, DECRYPT_JOB_TYPE);
  }

  /**
   * Tests {@link EncryptDecryptThread} for failures during encryption
   * @throws InterruptedException
   * @throws GeneralSecurityException
   */
  @Test
  public void testEncryptionFailure() throws InterruptedException, GeneralSecurityException {
    encryptDecryptThread.close();
    jobQueue.clear();
    MockCryptoService mockCryptoService = new MockCryptoService(new CryptoServiceConfig(verifiableProperties));
    mockCryptoService.exceptionOnEncryption =
        new GeneralSecurityException("Exception to test", new IllegalStateException());
    encryptDecryptThread = new EncryptDecryptThread(jobQueue, mockCryptoService, kms);
    new Thread(encryptDecryptThread).start();
    SecretKeySpec perBlobSecretKey = kms.getRandomKey();
    testFailureOnEncryption(perBlobSecretKey);
    mockCryptoService.clearStates();
    encryptDecryptThread.close();
    jobQueue.clear();
    MockKeyManagementService mockKms = new MockKeyManagementService(new KMSConfig(verifiableProperties), defaultKey);
    mockKms.exceptionToThrow = new GeneralSecurityException("Exception to test", new IllegalStateException());
    encryptDecryptThread = new EncryptDecryptThread(jobQueue, cryptoService, mockKms);
    new Thread(encryptDecryptThread).start();
    testFailureOnEncryption(perBlobSecretKey);
  }

  /**
   * Tests {@link EncryptDecryptThread} for failures during decryption
   * @throws InterruptedException
   * @throws GeneralSecurityException
   */
  @Test
  public void testDecryptionFailure() throws InterruptedException, GeneralSecurityException {
    encryptDecryptThread.close();
    jobQueue.clear();
    MockCryptoService mockCryptoService = new MockCryptoService(new CryptoServiceConfig(verifiableProperties));
    mockCryptoService.exceptionOnDecryption =
        new GeneralSecurityException("Exception to test", new IllegalStateException());
    encryptDecryptThread = new EncryptDecryptThread(jobQueue, mockCryptoService, kms);
    new Thread(encryptDecryptThread).start();
    SecretKeySpec perBlobSecretKey = kms.getRandomKey();
    testFailureOnDecryption(perBlobSecretKey, null, false);
    mockCryptoService.clearStates();
    encryptDecryptThread.close();
    jobQueue.clear();
    MockKeyManagementService mockKms = new MockKeyManagementService(new KMSConfig(verifiableProperties), defaultKey);
    encryptDecryptThread = new EncryptDecryptThread(jobQueue, cryptoService, mockKms);
    new Thread(encryptDecryptThread).start();
    testFailureOnDecryption(perBlobSecretKey, mockKms, true);
  }

  /**
   * Tests {@link EncryptDecryptThread} for pending encrypt jobs callback after closing the thread
   * @throws InterruptedException
   * @throws GeneralSecurityException
   */
  @Test
  public void testPendingEncryptJobs() throws InterruptedException, GeneralSecurityException {
    int testDataCount = 10;
    int closeOnCount = 4;
    final AtomicBoolean exceptionSeen = new AtomicBoolean(false);
    final AtomicBoolean closeIssued = new AtomicBoolean(false);
    CountDownLatch encryptCallBackCount = new CountDownLatch(testDataCount);
    SecretKeySpec perBlobKey = kms.getRandomKey();
    for (int i = 0; i < testDataCount; i++) {
      Pair<BlobId, ByteBuffer> randomData = getRandomBlob();
      jobQueue.add(new EncryptDecryptThread.EncryptJobInfo(randomData.getFirst(), randomData.getSecond(), perBlobKey,
          new EncryptDecryptThread.EncryptCallBack() {
            @Override
            public void onCompletion(BlobId callbackBlobId, ByteBuffer encryptedKey, ByteBuffer encryptedContent,
                GeneralSecurityException exception) {
              encryptCallBackCount.countDown();
              Assert.assertEquals("BlobId mismatch ", randomData.getFirst(), callbackBlobId);
              if (!exceptionSeen.get() && exception == null) {
                Assert.assertNotNull("Encrypted content should not be null", encryptedContent);
                Assert.assertNotNull("Encrypted key should not be null", encryptedKey);
              } else {
                exceptionSeen.set(true);
                Assert.assertNotNull(
                    "Exception should have been thrown to encrypt contents for " + randomData.getFirst(), exception);
                Assert.assertNull("Encrypted contents should have been null", encryptedContent);
                Assert.assertNull("Encrypted key should have been null", encryptedKey);
              }
              if (!closeIssued.get() && encryptCallBackCount.getCount() == closeOnCount) {
                new Thread(new ThreadToCloseEncryptDecryptThread(encryptDecryptThread)).start();
                closeIssued.set(true);
              }
            }
          }));
    }
    awaitCountDownLatch(encryptCallBackCount, ENCRYPT_JOB_TYPE);
  }

  /**
   * Tests {@link EncryptDecryptThread} for pending decrypt jobs callback after closing the thread
   * @throws InterruptedException
   * @throws GeneralSecurityException
   */
  @Test
  public void testPendingDecryptJobs() throws InterruptedException, GeneralSecurityException {
    int testDataCount = 10;
    final AtomicBoolean closeIssued = new AtomicBoolean(false);
    CountDownLatch encryptCallBackCount = new CountDownLatch(testDataCount);
    CountDownLatch decryptCallBackCount = new CountDownLatch(testDataCount);
    SecretKeySpec perBlobKey = kms.getRandomKey();
    List<EncryptDecryptThread.DecryptJobInfo> decryptJobInfos = new ArrayList<>();
    for (int i = 0; i < testDataCount; i++) {
      Pair<BlobId, ByteBuffer> randomData = getRandomBlob();
      jobQueue.add(new EncryptDecryptThread.EncryptJobInfo(randomData.getFirst(), randomData.getSecond(), perBlobKey,
          new EncryptDecryptThread.EncryptCallBack() {
            @Override
            public void onCompletion(BlobId callbackBlobId, ByteBuffer encryptedKey, ByteBuffer encryptedContent,
                GeneralSecurityException exception) {
              Assert.assertEquals("BlobId mismatch ", randomData.getFirst(), callbackBlobId);
              Assert.assertNotNull("Encrypted content should not be null", encryptedContent);
              Assert.assertNotNull("Encrypted key should not be null", encryptedKey);
              encryptCallBackCount.countDown();
              decryptJobInfos.add(
                  new EncryptDecryptThread.DecryptJobInfo(randomData.getFirst(), encryptedKey, encryptedContent,
                      new EncryptDecryptThread.DecryptCallBack() {
                        @Override
                        public void onCompletion(BlobId blobId, ByteBuffer decryptedContent,
                            GeneralSecurityException e) {
                          Assert.assertEquals("BlobId mismatch ", blobId, callbackBlobId);
                          if (e == null) {
                            Assert.assertNull("Exception shouldn't have been thrown to decrypt contents for " + blobId,
                                exception);
                            Assert.assertNotNull("Decrypted contents should not be null", decryptedContent);
                            Assert.assertArrayEquals("Decrypted bytes and plain bytes should match",
                                randomData.getSecond().array(), decryptedContent.array());
                          } else {
                            Assert.assertNotNull("Exception should have been thrown to decrypt contents for " + blobId,
                                e);
                            Assert.assertNull("Decrypted contents should have been null", decryptedContent);
                          }
                          decryptCallBackCount.countDown();
                        }
                      }));
            }
          }));
    }

    // add special job that will close the thread. Add all the decrypt jobs to the queue before closing the thread.
    Pair<BlobId, ByteBuffer> probeData = getRandomBlob();
    jobQueue.add(new EncryptDecryptThread.EncryptJobInfo(probeData.getFirst(), probeData.getSecond(), perBlobKey,
        new EncryptDecryptThread.EncryptCallBack() {
          @Override
          public void onCompletion(BlobId callbackBlobId, ByteBuffer encryptedKey, ByteBuffer encryptedContent,
              GeneralSecurityException exception) {
            Iterator<EncryptDecryptThread.DecryptJobInfo> iterator = decryptJobInfos.iterator();
            while (iterator.hasNext()) {
              EncryptDecryptThread.DecryptJobInfo decryptJobInfo = iterator.next();
              jobQueue.add(decryptJobInfo);
              iterator.remove();
            }
            new Thread(new ThreadToCloseEncryptDecryptThread(encryptDecryptThread)).start();
            closeIssued.set(true);
          }
        }));
    awaitCountDownLatch(encryptCallBackCount, ENCRYPT_JOB_TYPE);
    awaitCountDownLatch(decryptCallBackCount, DECRYPT_JOB_TYPE);
  }

  /**
   * Tests {@link EncryptDecryptThread} for encrypt and decrypt calls after closing the thread
   * @throws GeneralSecurityException
   */
  @Test
  public void testEncryptDecryptThreadAfterClose() throws GeneralSecurityException {
    SecretKeySpec perBlobKey = kms.getRandomKey();
    Pair<BlobId, ByteBuffer> randomData = getRandomBlob();
    encryptDecryptThread.close();

    jobQueue.add(new EncryptDecryptThread.EncryptJobInfo(randomData.getFirst(), randomData.getSecond(), perBlobKey,
        new EncryptDecryptThread.EncryptCallBack() {
          @Override
          public void onCompletion(BlobId callbackBlobId, ByteBuffer encryptedKey, ByteBuffer encryptedContent,
              GeneralSecurityException exception) {
            Assert.fail("Callback should not have been called since EncryptDecryptThread is closed");
          }
        }));

    jobQueue.add(
        new EncryptDecryptThread.DecryptJobInfo(randomData.getFirst(), randomData.getSecond(), randomData.getSecond(),
            new EncryptDecryptThread.DecryptCallBack() {
              @Override
              public void onCompletion(BlobId callbackBlobId, ByteBuffer encryptedContent,
                  GeneralSecurityException exception) {
                Assert.fail("Callback should not have been called since EncryptDecryptThread is closed");
              }
            }));
  }

  /**
   * Tests encryption and decryption flow for happy path
   * @param perBlobKey the {@link SecretKeySpec} representing the per blob key
   * @param encryptCallBackCount {@link CountDownLatch} to track encryption callbacks
   * @param decryptCallBackCount {@link CountDownLatch} to track decryption callbacks
   */
  private void testEncryptDecryptFlow(SecretKeySpec perBlobKey, CountDownLatch encryptCallBackCount,
      CountDownLatch decryptCallBackCount) {
    Pair<BlobId, ByteBuffer> randomData = getRandomBlob();
    jobQueue.add(new EncryptDecryptThread.EncryptJobInfo(randomData.getFirst(), randomData.getSecond(), perBlobKey,
        new EncryptCallBackVerifier(randomData.getFirst(), false, encryptCallBackCount,
            new DecryptCallBackVerifier(randomData.getFirst(), randomData.getSecond(), false, decryptCallBackCount))));
  }

  /**
   * Encrypt callback verifier. Verifies non null for arguments and adds a decrypt job to the jobQueue on successful completion.
   * Else, verifies the exception is set correctly.
   */
  private class EncryptCallBackVerifier implements EncryptDecryptThread.EncryptCallBack {
    BlobId blobId;
    boolean expectException;
    CountDownLatch countDownLatch;
    DecryptCallBackVerifier decryptCallBackVerifier;

    EncryptCallBackVerifier(BlobId blobId, boolean expectException, CountDownLatch encryptCountDownLatch,
        DecryptCallBackVerifier decryptCallBackVerifier) {
      this.blobId = blobId;
      this.expectException = expectException;
      this.countDownLatch = encryptCountDownLatch;
      this.decryptCallBackVerifier = decryptCallBackVerifier;
    }

    @Override
    public void onCompletion(BlobId callbackBlobId, ByteBuffer encryptedKey, ByteBuffer encryptedContent,
        GeneralSecurityException exception) {
      Assert.assertEquals("BlobId mismatch ", blobId, callbackBlobId);
      if (countDownLatch != null) {
        countDownLatch.countDown();
      }
      if (!expectException) {
        Assert.assertNull("Exception shouldn't have been thrown to encrypt contents for " + blobId, exception);
        Assert.assertNotNull("Encrypted content should not be null", encryptedContent);
        Assert.assertNotNull("Encrypted key should not be null", encryptedKey);
        jobQueue.add(
            new EncryptDecryptThread.DecryptJobInfo(blobId, encryptedKey, encryptedContent, decryptCallBackVerifier));
      } else {
        Assert.assertNotNull("Exception should have been thrown to encrypt contents for " + blobId, exception);
        Assert.assertNull("Encrypted contents should have been null", encryptedContent);
        Assert.assertNull("Encrypted key should have been null", encryptedKey);
      }
    }
  }

  /**
   * Decrypt callback verifier. Verifies the decrypted content matches raw content on successful completion.
   * Else, verifies the exception is set correctly.
   */
  private class DecryptCallBackVerifier implements EncryptDecryptThread.DecryptCallBack {
    BlobId blobId;
    boolean expectException;
    ByteBuffer unencryptedContent;
    CountDownLatch countDownLatch;

    DecryptCallBackVerifier(BlobId blobId, ByteBuffer unencryptedContent, boolean expectException,
        CountDownLatch countDownLatch) {
      this.blobId = blobId;
      this.unencryptedContent = unencryptedContent;
      this.expectException = expectException;
      this.countDownLatch = countDownLatch;
    }

    @Override
    public void onCompletion(BlobId callbackBlobId, ByteBuffer decryptedContent, GeneralSecurityException exception) {
      if (countDownLatch != null) {
        countDownLatch.countDown();
      }
      Assert.assertEquals("BlobId mismatch ", blobId, callbackBlobId);
      if (!expectException) {
        Assert.assertNull("Exception shouldn't have been thrown to decrypt contents for " + blobId, exception);
        Assert.assertNotNull("Decrypted contents should not be null", decryptedContent);
        Assert.assertArrayEquals("Decrypted bytes and plain bytes should match", unencryptedContent.array(),
            decryptedContent.array());
      } else {
        Assert.assertNotNull("Exception should have been thrown to decrypt contents for " + blobId, exception);
        Assert.assertNull("Decrypted contents should have been null", decryptedContent);
      }
    }
  }

  /**
   * Test failure during encryption
   * @param perBlobKey the {@link SecretKeySpec} representing the per blob key
   * @throws InterruptedException
   */
  private void testFailureOnEncryption(SecretKeySpec perBlobKey) throws InterruptedException {
    Pair<BlobId, ByteBuffer> randomData = getRandomBlob();
    CountDownLatch encryptCallBackCount = new CountDownLatch(1);
    jobQueue.add(new EncryptDecryptThread.EncryptJobInfo(randomData.getFirst(), randomData.getSecond(), perBlobKey,
        new EncryptCallBackVerifier(randomData.getFirst(), true, encryptCallBackCount, null)));
    awaitCountDownLatch(encryptCallBackCount, ENCRYPT_JOB_TYPE);
  }

  /**
   * Test failure during decryption
   * @param perBlobKey the {@link SecretKeySpec} representing the per blob key
   * @param mockKMS {@link MockKeyManagementService} that mocks {@link KeyManagementService}
   * @param setExceptionForKMS {@code true} if exception needs to be set using {@link MockKeyManagementService}
   *                           {@code false} otherwise
   * @throws InterruptedException
   */
  private void testFailureOnDecryption(SecretKeySpec perBlobKey, MockKeyManagementService mockKMS,
      boolean setExceptionForKMS) throws InterruptedException {
    Pair<BlobId, ByteBuffer> randomData = getRandomBlob();
    CountDownLatch encryptCallBackCount = new CountDownLatch(1);
    CountDownLatch decryptCallBackCount = new CountDownLatch(1);
    jobQueue.add(new EncryptDecryptThread.EncryptJobInfo(randomData.getFirst(), randomData.getSecond(), perBlobKey,
        new EncryptDecryptThread.EncryptCallBack() {
          @Override
          public void onCompletion(BlobId callbackBlobId, ByteBuffer encryptedKey, ByteBuffer encryptedContent,
              GeneralSecurityException exception) {
            encryptCallBackCount.countDown();
            Assert.assertNull("Exception shouldn't have been thrown to encrypt contents for " + randomData.getFirst(),
                exception);
            Assert.assertNotNull("Encrypted contents should not be null", encryptedContent);
            Assert.assertNotNull("Encrypted key should not be null", encryptedKey);
            Assert.assertEquals("BlobId mismatch", randomData.getFirst(), callbackBlobId);

            // set exception using MockKMS
            if (setExceptionForKMS) {
              mockKMS.exceptionToThrow = new GeneralSecurityException("Exception to test", new IllegalStateException());
            }
            jobQueue.add(new EncryptDecryptThread.DecryptJobInfo(randomData.getFirst(), encryptedKey, encryptedContent,
                new EncryptDecryptThread.DecryptCallBack() {
                  @Override
                  public void onCompletion(BlobId callbackBlobId, ByteBuffer decryptedContent,
                      GeneralSecurityException exception) {
                    decryptCallBackCount.countDown();
                    Assert.assertNotNull(
                        "Exception should have been thrown to decrypt contents for " + randomData.getFirst(),
                        exception);
                    Assert.assertNull("Decrypted contents should have been null", decryptedContent);
                    Assert.assertEquals("BlobId mismatch", randomData.getFirst(), callbackBlobId);
                  }
                }));
          }
        }));
    awaitCountDownLatch(decryptCallBackCount, DECRYPT_JOB_TYPE);
  }

  /**
   * Awaits for {@code countDownLatch} to count down to 0.
   * @param countDownLatch the {@link CountDownLatch} that needs to be awaited against
   * @param jobType the job type of the count down latch
   * @throws InterruptedException
   */
  private void awaitCountDownLatch(CountDownLatch countDownLatch, String jobType) throws InterruptedException {
    if (!countDownLatch.await(30, TimeUnit.SECONDS)) {
      Assert.fail("Not all " + jobType + " callbacks have been returned. Pending count: " + countDownLatch.getCount());
    }
  }

  /**
   * Generate and return random data (i.e. BlobId and ByteBuffer)
   * @return a Pair of BlobId and ByteBuffer with random data
   */
  private Pair<BlobId, ByteBuffer> getRandomBlob() {
    BlobId blobId = getNewBlobId();
    int size = TestUtils.RANDOM.nextInt(MAX_DATA_SIZE);
    byte[] data = new byte[size];
    TestUtils.RANDOM.nextBytes(data);
    ByteBuffer toEncrypt = ByteBuffer.wrap(data);
    return new Pair<>(blobId, toEncrypt);
  }

  /**
   * Generate new {@link BlobId}
   * @return newly generated {@link BlobId}
   */
  private BlobId getNewBlobId() {
    byte[] bytes = new byte[2];
    TestUtils.RANDOM.nextBytes(bytes);
    return new BlobId(bytes[0], bytes[1], getRandomShort(TestUtils.RANDOM), getRandomShort(TestUtils.RANDOM),
        referenceClusterMap.getWritablePartitionIds().get(0));
  }

  /**
   * Thread to close the {@link EncryptDecryptThread} asynchronously
   */
  class ThreadToCloseEncryptDecryptThread implements Runnable {
    private final EncryptDecryptThread thread;

    ThreadToCloseEncryptDecryptThread(EncryptDecryptThread thread) {
      this.thread = thread;
    }

    @Override
    public void run() {
      thread.close();
    }
  }

  /**
   * MockCryptoService to assist in testing exception cases
   */
  class MockCryptoService extends GCMCryptoService {

    GeneralSecurityException exceptionOnEncryption = null;
    GeneralSecurityException exceptionOnDecryption = null;

    MockCryptoService(CryptoServiceConfig cryptoServiceConfig) {
      super(cryptoServiceConfig);
    }

    @Override
    public ByteBuffer encrypt(ByteBuffer toEncrypt, SecretKeySpec key) throws GeneralSecurityException {
      if (exceptionOnEncryption != null) {
        throw exceptionOnEncryption;
      }
      return super.encrypt(toEncrypt, key);
    }

    @Override
    public ByteBuffer decrypt(ByteBuffer toDecrypt, SecretKeySpec key) throws GeneralSecurityException {
      if (exceptionOnDecryption != null) {
        throw exceptionOnDecryption;
      }
      return super.decrypt(toDecrypt, key);
    }

    void clearStates() {
      exceptionOnEncryption = null;
      exceptionOnDecryption = null;
    }
  }

  /**
   * MockKeyManagementService to assist in testing exception cases
   */
  class MockKeyManagementService extends SingleKeyManagementService {

    volatile GeneralSecurityException exceptionToThrow;

    MockKeyManagementService(KMSConfig KMSConfig, String defaultKey) throws GeneralSecurityException {
      super(KMSConfig, defaultKey);
    }

    @Override
    public SecretKeySpec getKey(short accountId, short containerId) throws GeneralSecurityException {
      if (exceptionToThrow != null) {
        throw exceptionToThrow;
      } else {
        return super.getKey(accountId, containerId);
      }
    }
  }
}
