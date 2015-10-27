package com.github.ambry.store;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;


/**
 * The component used by the store to hard delete entries in the log.
 */
public interface MessageStoreHardDelete {
  /**
   * Returns an iterator over the HardDeleteInfo of the messages in the readSet.
   * @param readSet The set of messages to be replaced.
   * @param factory the store key factory.
   * @param recoveryInfoList An optional list of recoveryInfo messages.
   * @return iterator over the HardDeleteInfo for the messages in the readSet.
   */
  public Iterator<HardDeleteInfo> getHardDeleteMessages(MessageReadSet readSet, StoreKeyFactory factory,
      List<byte[]> recoveryInfoList)
      throws IOException;

  /**
   * Returns the message info of message at the given offset from the given Read interface.
   * @param read The read interface from which the message info is to be read.
   * @param offset The start offset of the message.
   * @param factory the store key factory.
   * @return a MessageInfo object for the message at the offset.
   */
  public MessageInfo getMessageInfo(Read read, long offset, StoreKeyFactory factory)
      throws IOException;
}