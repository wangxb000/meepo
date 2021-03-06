
package org.feisoft.jta.logging.deserializer;

import org.apache.commons.lang3.StringUtils;
import org.feisoft.common.utils.ByteUtils;
import org.feisoft.transaction.archive.TransactionArchive;
import org.feisoft.transaction.archive.XAResourceArchive;
import org.feisoft.transaction.logging.ArchiveDeserializer;
import org.feisoft.transaction.xa.TransactionXid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.List;

public class TransactionArchiveDeserializer implements ArchiveDeserializer {
	static final Logger logger = LoggerFactory.getLogger(TransactionArchiveDeserializer.class);

	private ArchiveDeserializer resourceArchiveDeserializer;

	public byte[] serialize(TransactionXid xid, Object obj) {
		TransactionArchive archive = (TransactionArchive) obj;

		String propagatedBy = String.valueOf(archive.getPropagatedBy());
		String[] address = propagatedBy.split("\\s*\\:\\s*");
		byte[] hostByteArray = new byte[4];
		byte[] nameByteArray = new byte[0];
		byte[] portByteArray = new byte[2];
		if (address.length == 3) {
			String hostStr = address[0];
			String nameStr = address[1];
			String portStr = address[2];

			String[] hostArray = hostStr.split("\\s*\\.\\s*");
			for (int i = 0; hostArray.length == 4 && i < hostArray.length; i++) {
				try {
					int value = Integer.valueOf(hostArray[i]);
					hostByteArray[i] = (byte) (value - 128);
				} catch (RuntimeException rex) {
					logger.debug(rex.getMessage(), rex);
				}
			}

			nameByteArray = StringUtils.isBlank(nameStr) ? new byte[0] : nameStr.getBytes();

			try {
				short port = (short) (Integer.valueOf(portStr) - 32768);
				byte[] byteArray = ByteUtils.shortToByteArray(port);
				System.arraycopy(byteArray, 0, portByteArray, 0, 2);
			} catch (RuntimeException rex) {
				logger.debug(rex.getMessage(), rex);
			}
		}

		XAResourceArchive optimizedArchive = archive.getOptimizedResource();

		List<XAResourceArchive> nativeArchiveList = archive.getNativeResources();
		List<XAResourceArchive> remoteArchiveList = archive.getRemoteResources();

		int optimizedArchiveNumber = optimizedArchive == null ? 0 : 1;

		int nativeArchiveNumber = nativeArchiveList.size();
		int remoteArchiveNumber = remoteArchiveList.size();

		int transactionStrategy = archive.getTransactionStrategyType();

		int length = 3 + 3 + 1 + 4 + 1 + nameByteArray.length + 2;
		byte[][] nativeByteArray = new byte[nativeArchiveNumber][];
		for (int i = 0; i < nativeArchiveNumber; i++) {
			XAResourceArchive resourceArchive = nativeArchiveList.get(i);

			byte[] resourceByteArray = this.resourceArchiveDeserializer.serialize(xid, resourceArchive);
			byte[] lengthByteArray = ByteUtils.shortToByteArray((short) resourceByteArray.length);

			byte[] elementByteArray = new byte[resourceByteArray.length + 2];
			System.arraycopy(lengthByteArray, 0, elementByteArray, 0, lengthByteArray.length);
			System.arraycopy(resourceByteArray, 0, elementByteArray, 2, resourceByteArray.length);

			nativeByteArray[i] = elementByteArray;
			length = length + elementByteArray.length;
		}

		byte[] optimizedByteArray = new byte[0];
		if (optimizedArchiveNumber > 0) {
			byte[] resourceByteArray = this.resourceArchiveDeserializer.serialize(xid, optimizedArchive);
			byte[] lengthByteArray = ByteUtils.shortToByteArray((short) resourceByteArray.length);

			byte[] elementByteArray = new byte[resourceByteArray.length + 2];
			System.arraycopy(lengthByteArray, 0, elementByteArray, 0, lengthByteArray.length);
			System.arraycopy(resourceByteArray, 0, elementByteArray, 2, resourceByteArray.length);

			optimizedByteArray = elementByteArray;
			length = length + elementByteArray.length;
		}

		byte[][] remoteByteArray = new byte[remoteArchiveNumber][];
		for (int i = 0; i < remoteArchiveNumber; i++) {
			XAResourceArchive resourceArchive = remoteArchiveList.get(i);

			byte[] resourceByteArray = this.resourceArchiveDeserializer.serialize(xid, resourceArchive);
			byte[] lengthByteArray = ByteUtils.shortToByteArray((short) resourceByteArray.length);

			byte[] elementByteArray = new byte[resourceByteArray.length + 2];
			System.arraycopy(lengthByteArray, 0, elementByteArray, 0, lengthByteArray.length);
			System.arraycopy(resourceByteArray, 0, elementByteArray, 2, resourceByteArray.length);

			remoteByteArray[i] = elementByteArray;
			length = length + elementByteArray.length;
		}

		int position = 0;

		byte[] byteArray = new byte[length];
		byteArray[position++] = (byte) archive.getStatus();
		byteArray[position++] = (byte) archive.getVote();
		byteArray[position++] = archive.isCoordinator() ? (byte) 0x1 : (byte) 0x0;

		byteArray[position++] = (byte) nativeArchiveNumber;
		byteArray[position++] = (byte) optimizedArchiveNumber;
		byteArray[position++] = (byte) remoteArchiveNumber;

		byteArray[position++] = (byte) transactionStrategy;

		System.arraycopy(hostByteArray, 0, byteArray, position, 4);
		position = position + 4;

		byteArray[position++] = (byte) (nameByteArray.length - 128);
		System.arraycopy(nameByteArray, 0, byteArray, position, nameByteArray.length);
		position = position + nameByteArray.length;

		System.arraycopy(portByteArray, 0, byteArray, position, 2);
		position = position + 2;

		for (int i = 0; i < nativeArchiveNumber; i++) {
			byte[] elementByteArray = nativeByteArray[i];
			System.arraycopy(elementByteArray, 0, byteArray, position, elementByteArray.length);
			position = position + elementByteArray.length;
		}

		if (optimizedArchiveNumber > 0) {
			System.arraycopy(optimizedByteArray, 0, byteArray, position, optimizedByteArray.length);
			position = position + optimizedByteArray.length;
		}

		for (int i = 0; i < remoteArchiveNumber; i++) {
			byte[] elementByteArray = remoteByteArray[i];
			System.arraycopy(elementByteArray, 0, byteArray, position, elementByteArray.length);
			position = position + elementByteArray.length;
		}

		return byteArray;
	}

	public Object deserialize(TransactionXid xid, byte[] array) {

		ByteBuffer buffer = ByteBuffer.wrap(array);

		TransactionArchive archive = new TransactionArchive();
		archive.setXid(xid);

		int status = buffer.get();
		int vote = buffer.get();
		int coordinatorValue = buffer.get();

		archive.setStatus(status);
		archive.setVote(vote);
		archive.setCoordinator(coordinatorValue != 0);

		int nativeArchiveNumber = buffer.get();
		int optimizedArchiveNumber = buffer.get();
		int remoteArchiveNumber = buffer.get();

		int transactionStrategyType = buffer.get();
		archive.setTransactionStrategyType(transactionStrategyType);

		byte[] hostByteArray = new byte[4];
		buffer.get(hostByteArray);
		StringBuilder ber = new StringBuilder();
		for (int i = 0; i < hostByteArray.length; i++) {
			int value = hostByteArray[i] + 128;
			if (i == 0) {
				ber.append(value);
			} else {
				ber.append(".");
				ber.append(value);
			}
		}
		String host = ber.toString();

		int sizeOfName = 128 + buffer.get();
		byte[] nameByteArray = new byte[sizeOfName];
		buffer.get(nameByteArray);
		String name = new String(nameByteArray);

		int port = 32768 + buffer.getShort();
		archive.setPropagatedBy(String.format("%s:%s:%s", host, name, port));

		for (int i = 0; i < nativeArchiveNumber; i++) {
			int length = buffer.getShort();
			byte[] resourceByteArray = new byte[length];
			buffer.get(resourceByteArray);

			XAResourceArchive resourceArchive = //
					(XAResourceArchive) this.resourceArchiveDeserializer.deserialize(xid, resourceByteArray);

			archive.getNativeResources().add(resourceArchive);
		}

		if (optimizedArchiveNumber > 0) {
			int length = buffer.getShort();
			byte[] resourceByteArray = new byte[length];
			buffer.get(resourceByteArray);

			XAResourceArchive resourceArchive = //
					(XAResourceArchive) this.resourceArchiveDeserializer.deserialize(xid, resourceByteArray);

			archive.setOptimizedResource(resourceArchive);
		}

		for (int i = 0; i < remoteArchiveNumber; i++) {
			int length = buffer.getShort();
			byte[] resourceByteArray = new byte[length];
			buffer.get(resourceByteArray);

			XAResourceArchive resourceArchive = //
					(XAResourceArchive) this.resourceArchiveDeserializer.deserialize(xid, resourceByteArray);

			archive.getRemoteResources().add(resourceArchive);
		}

		return archive;
	}

	public ArchiveDeserializer getResourceArchiveDeserializer() {
		return resourceArchiveDeserializer;
	}

	public void setResourceArchiveDeserializer(ArchiveDeserializer resourceArchiveDeserializer) {
		this.resourceArchiveDeserializer = resourceArchiveDeserializer;
	}

}
