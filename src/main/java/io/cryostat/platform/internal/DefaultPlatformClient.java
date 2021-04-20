/*-
 * #%L
 * Cryostat
 * %%
 * Copyright (C) 2020 - 2021 The Cryostat Authors
 * %%
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software (each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 * The above copyright notice and either this complete permission notice or at
 * a minimum a reference to the UPL must be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * #L%
 */
package io.cryostat.platform.internal;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import io.cryostat.core.log.Logger;
import io.cryostat.core.net.discovery.JvmDiscoveryClient;
import io.cryostat.core.net.discovery.JvmDiscoveryClient.JvmDiscoveryEvent;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.platform.ServiceRef;

class DefaultPlatformClient extends AbstractPlatformClient implements Consumer<JvmDiscoveryEvent> {

    private final Logger logger;
    private final JvmDiscoveryClient discoveryClient;

    DefaultPlatformClient(
            Logger logger,
            JvmDiscoveryClient discoveryClient,
            NotificationFactory notificationFactory) {
        super(notificationFactory);
        this.logger = logger;
        this.discoveryClient = discoveryClient;
    }

    @Override
    public void start() throws IOException {
        discoveryClient.addListener(this);
        discoveryClient.start();
    }

    @Override
    public void accept(JvmDiscoveryEvent evt) {
        try {
            ServiceRef serviceRef =
                    new ServiceRef(
                            evt.getJvmDescriptor().getJmxServiceUrl(),
                            evt.getJvmDescriptor().getMainClass());
            notifyAsyncTargetDiscovery(evt.getEventKind(), serviceRef);
        } catch (MalformedURLException e) {
            logger.warn(e);
        }
    }

    @Override
    public List<ServiceRef> listDiscoverableServices() {
        return discoveryClient.getDiscoveredJvmDescriptors().stream()
                .map(
                        u -> {
                            try {
                                return new ServiceRef(u.getJmxServiceUrl(), u.getMainClass());
                            } catch (MalformedURLException e) {
                                logger.warn(e);
                                return null;
                            }
                        })
                .filter(s -> s != null)
                .collect(Collectors.toList());
    }
}
