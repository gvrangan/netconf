/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.messages.HelloMessage;
import org.opendaylight.netconf.api.messages.NetconfHelloMessageAdditionalHeader;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * Customized NetconfXMLToMessageDecoder that reads additional header with
 * session metadata from
 * {@link HelloMessage}*
 * This handler should be replaced in pipeline by regular message handler as last step of negotiation.
 * It serves as a message barrier and halts all non-hello netconf messages.
 * Netconf messages after hello should be processed once the negotiation succeeded.
 *
 */
public final class NetconfXMLToHelloMessageDecoder extends ByteToMessageDecoder {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfXMLToHelloMessageDecoder.class);

    private static final List<byte[]> POSSIBLE_ENDS = ImmutableList.of(
            new byte[] { ']', '\n' },
            new byte[] { ']', '\r', '\n' });
    private static final List<byte[]> POSSIBLE_STARTS = ImmutableList.of(
            new byte[] { '[' },
            new byte[] { '\r', '\n', '[' },
            new byte[] { '\n', '[' });

    // State variables do not have to by synchronized
    // Netty uses always the same (1) thread per pipeline
    // We use instance of this per pipeline
    private final List<NetconfMessage> nonHelloMessages = new ArrayList<>();
    private boolean helloReceived = false;

    @Override
    @VisibleForTesting
    public void decode(final ChannelHandlerContext ctx, final ByteBuf in, final List<Object> out)
            throws IOException, SAXException {
        if (in.readableBytes() == 0) {
            LOG.debug("No more content in incoming buffer.");
            return;
        }

        in.markReaderIndex();
        try {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Received to decode: {}", ByteBufUtil.hexDump(in));
            }

            byte[] bytes = new byte[in.readableBytes()];
            in.readBytes(bytes);

            logMessage(bytes);

            // Extract bytes containing header with additional metadata
            String additionalHeader = null;
            if (startsWithAdditionalHeader(bytes)) {
                // Auth information containing username, ip address... extracted for monitoring
                int endOfAuthHeader = getAdditionalHeaderEndIndex(bytes);
                if (endOfAuthHeader > -1) {
                    byte[] additionalHeaderBytes = Arrays.copyOfRange(bytes, 0, endOfAuthHeader);
                    additionalHeader = additionalHeaderToString(additionalHeaderBytes);
                    bytes = Arrays.copyOfRange(bytes, endOfAuthHeader, bytes.length);
                }
            }

            Document doc = XmlUtil.readXmlToDocument(new ByteArrayInputStream(bytes));

            final NetconfMessage message = getNetconfMessage(additionalHeader, doc);
            if (message instanceof HelloMessage) {
                Preconditions.checkState(!helloReceived,
                        "Multiple hello messages received, unexpected hello: %s", message);
                out.add(message);
                helloReceived = true;
            // Non hello message, suspend the message and insert into cache
            } else {
                Preconditions.checkState(helloReceived, "Hello message not received, instead received: %s", message);
                LOG.debug("Netconf message received during negotiation, caching {}", message);
                nonHelloMessages.add(message);
            }
        } finally {
            in.discardReadBytes();
        }
    }

    private static NetconfMessage getNetconfMessage(final String additionalHeader, final Document doc) {
        NetconfMessage msg = new NetconfMessage(doc);
        if (HelloMessage.isHelloMessage(msg)) {
            if (additionalHeader != null) {
                return new HelloMessage(doc, NetconfHelloMessageAdditionalHeader.fromString(additionalHeader));
            } else {
                return new HelloMessage(doc);
            }
        }

        return msg;
    }

    private static int getAdditionalHeaderEndIndex(final byte[] bytes) {
        for (byte[] possibleEnd : POSSIBLE_ENDS) {
            int idx = findByteSequence(bytes, possibleEnd);

            if (idx != -1) {
                return idx + possibleEnd.length;
            }
        }

        return -1;
    }

    private static int findByteSequence(final byte[] bytes, final byte[] sequence) {
        if (bytes.length < sequence.length) {
            throw new IllegalArgumentException("Sequence to be found is longer than the given byte array.");
        }
        if (bytes.length == sequence.length) {
            if (Arrays.equals(bytes, sequence)) {
                return 0;
            } else {
                return -1;
            }
        }
        int index = 0;
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] == sequence[index]) {
                index++;
                if (index == sequence.length) {
                    return i - index + 1;
                }
            } else {
                index = 0;
            }
        }
        return -1;
    }

    private static void logMessage(final byte[] bytes) {
        if (LOG.isDebugEnabled()) {
            String string = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(bytes)).toString();
            LOG.debug("Parsing message \n{}", string);
        }
    }

    private static boolean startsWithAdditionalHeader(final byte[] bytes) {
        for (byte[] possibleStart : POSSIBLE_STARTS) {
            int index = 0;
            for (byte b : possibleStart) {
                if (bytes[index++] != b) {
                    break;
                }

                if (index == possibleStart.length) {
                    return true;
                }
            }
        }

        return false;
    }

    private static String additionalHeaderToString(final byte[] bytes) {
        return StandardCharsets.UTF_8.decode(ByteBuffer.wrap(bytes)).toString();
    }

    /**
     * Get netconf messages received during negotiation.
     *
     * @return Collection of NetconfMessages that were not hello, but were received during negotiation.
     */
    public Iterable<NetconfMessage> getPostHelloNetconfMessages() {
        return nonHelloMessages;
    }
}
