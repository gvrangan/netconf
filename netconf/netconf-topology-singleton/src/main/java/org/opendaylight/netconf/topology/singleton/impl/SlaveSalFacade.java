/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.util.Timeout;
import java.util.concurrent.atomic.AtomicBoolean;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceServices;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceSalProvider;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SlaveSalFacade {

    private static final Logger LOG = LoggerFactory.getLogger(SlaveSalFacade.class);

    private final RemoteDeviceId id;
    private final NetconfDeviceSalProvider salProvider;
    private final ActorSystem actorSystem;
    private final Timeout actorResponseWaitTime;
    private final AtomicBoolean registered = new AtomicBoolean(false);

    public SlaveSalFacade(final RemoteDeviceId id,
                          final ActorSystem actorSystem,
                          final Timeout actorResponseWaitTime,
                          final DOMMountPointService mountPointService) {
        this.id = id;
        salProvider = new NetconfDeviceSalProvider(id, mountPointService);
        this.actorSystem = actorSystem;
        this.actorResponseWaitTime = actorResponseWaitTime;
    }

    public void registerSlaveMountPoint(final EffectiveModelContext remoteSchemaContext, final ActorRef masterActorRef,
            final RemoteDeviceServices services) {
        if (!registered.compareAndSet(false, true)) {
            return;
        }

        final ProxyDOMDataBroker netconfDeviceDataBroker = new ProxyDOMDataBroker(id, masterActorRef,
            actorSystem.dispatcher(), actorResponseWaitTime);
        final NetconfDataTreeService proxyNetconfService = new ProxyNetconfDataTreeService(id, masterActorRef,
            actorSystem.dispatcher(), actorResponseWaitTime);

        salProvider.getMountInstance().onTopologyDeviceConnected(remoteSchemaContext, services, netconfDeviceDataBroker,
            proxyNetconfService);

        LOG.info("{}: Slave mount point registered.", id);
    }

    public void close() {
        if (!registered.compareAndSet(true, false)) {
            return;
        }

        salProvider.getMountInstance().onTopologyDeviceDisconnected();

        LOG.info("{}: Slave mount point unregistered.", id);
    }
}
