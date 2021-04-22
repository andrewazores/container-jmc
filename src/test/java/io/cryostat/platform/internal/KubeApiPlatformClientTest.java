/*
 * Copyright The Cryostat Authors
 *
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
 */
package io.cryostat.platform.internal;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.management.remote.JMXServiceURL;

import io.cryostat.core.log.Logger;
import io.cryostat.core.net.JFRConnectionToolkit;
import io.cryostat.core.net.discovery.JvmDiscoveryClient.EventKind;
import io.cryostat.core.sys.Environment;
import io.cryostat.messaging.notifications.Notification;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.platform.ServiceRef;

import io.fabric8.kubernetes.api.model.EndpointAddress;
import io.fabric8.kubernetes.api.model.EndpointPort;
import io.fabric8.kubernetes.api.model.EndpointSubset;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EndpointsList;
import io.fabric8.kubernetes.api.model.ObjectReference;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.Watcher.Action;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
class KubeApiPlatformClientTest {

    static final String NAMESPACE = "foo-namespace";

    KubeApiPlatformClient platformClient;
    @Mock KubernetesClient k8sClient;
    @Mock JFRConnectionToolkit connectionToolkit;
    @Mock Environment env;
    @Mock NotificationFactory notificationFactory;
    @Mock Logger logger;

    @BeforeEach
    void setup() throws Exception {
        this.platformClient =
                new KubeApiPlatformClient(
                        NAMESPACE, k8sClient, () -> connectionToolkit, notificationFactory, logger);
    }

    @Test
    void shouldReturnEmptyListIfNoEndpointsFound() throws Exception {
        MixedOperation mockNamespaceOperation = Mockito.mock(MixedOperation.class);
        Mockito.when(k8sClient.endpoints()).thenReturn(mockNamespaceOperation);

        NonNamespaceOperation mockOperation = Mockito.mock(NonNamespaceOperation.class);
        Mockito.when(mockNamespaceOperation.inNamespace(Mockito.anyString()))
                .thenReturn(mockOperation);

        EndpointsList mockListable = Mockito.mock(EndpointsList.class);
        Mockito.when(mockOperation.list()).thenReturn(mockListable);

        List<Endpoints> mockEndpoints = Collections.emptyList();
        Mockito.when(mockListable.getItems()).thenReturn(mockEndpoints);

        platformClient.start();
        List<ServiceRef> result = platformClient.listDiscoverableServices();
        MatcherAssert.assertThat(result, Matchers.equalTo(Collections.emptyList()));
    }

    @Test
    void shouldReturnListOfMatchingEndpointRefs() throws Exception {
        MixedOperation mockNamespaceOperation = Mockito.mock(MixedOperation.class);
        Mockito.when(k8sClient.endpoints()).thenReturn(mockNamespaceOperation);

        NonNamespaceOperation mockOperation = Mockito.mock(NonNamespaceOperation.class);
        Mockito.when(mockNamespaceOperation.inNamespace(Mockito.anyString()))
                .thenReturn(mockOperation);

        EndpointsList mockListable = Mockito.mock(EndpointsList.class);
        Mockito.when(mockOperation.list()).thenReturn(mockListable);

        ObjectReference objRef1 = Mockito.mock(ObjectReference.class);
        // Mockito.when(objRef1.getName()).thenReturn("targetA");
        ObjectReference objRef2 = Mockito.mock(ObjectReference.class);
        Mockito.when(objRef2.getName()).thenReturn("targetB");
        ObjectReference objRef3 = Mockito.mock(ObjectReference.class);
        Mockito.when(objRef3.getName()).thenReturn("targetC");
        ObjectReference objRef4 = Mockito.mock(ObjectReference.class);
        Mockito.when(objRef4.getName()).thenReturn("targetD");

        EndpointAddress address1 = Mockito.mock(EndpointAddress.class);
        // Mockito.when(address1.getIp()).thenReturn("127.0.0.1");
        // Mockito.when(address1.getTargetRef()).thenReturn(objRef1);
        EndpointAddress address2 = Mockito.mock(EndpointAddress.class);
        Mockito.when(address2.getIp()).thenReturn("127.0.0.2");
        Mockito.when(address2.getTargetRef()).thenReturn(objRef2);
        EndpointAddress address3 = Mockito.mock(EndpointAddress.class);
        Mockito.when(address3.getIp()).thenReturn("127.0.0.3");
        Mockito.when(address3.getTargetRef()).thenReturn(objRef3);
        EndpointAddress address4 = Mockito.mock(EndpointAddress.class);
        Mockito.when(address4.getIp()).thenReturn("127.0.0.4");
        Mockito.when(address4.getTargetRef()).thenReturn(objRef4);

        EndpointPort port1 = Mockito.mock(EndpointPort.class);
        Mockito.when(port1.getPort()).thenReturn(80);
        Mockito.when(port1.getName()).thenReturn("tcp-80");
        EndpointPort port2 = Mockito.mock(EndpointPort.class);
        Mockito.when(port2.getPort()).thenReturn(9999);
        Mockito.when(port2.getName()).thenReturn("jfr-jmx");
        EndpointPort port3 = Mockito.mock(EndpointPort.class);
        Mockito.when(port3.getPort()).thenReturn(9091);
        Mockito.when(port3.getName()).thenReturn("tcp-9091");

        EndpointSubset subset1 = Mockito.mock(EndpointSubset.class);
        // Mockito.when(subset1.getAddresses()).thenReturn(Collections.singletonList(address1));
        Mockito.when(subset1.getPorts()).thenReturn(Collections.singletonList(port1));
        EndpointSubset subset2 = Mockito.mock(EndpointSubset.class);
        Mockito.when(subset2.getAddresses()).thenReturn(Arrays.asList(address2, address3));
        Mockito.when(subset2.getPorts()).thenReturn(Collections.singletonList(port2));
        EndpointSubset subset3 = Mockito.mock(EndpointSubset.class);
        Mockito.when(subset3.getAddresses()).thenReturn(Collections.singletonList(address4));
        Mockito.when(subset3.getPorts()).thenReturn(Collections.singletonList(port3));

        Endpoints endpoint = Mockito.mock(Endpoints.class);
        Mockito.when(endpoint.getSubsets()).thenReturn(Arrays.asList(subset1, subset2, subset3));

        Mockito.when(mockListable.getItems()).thenReturn(Collections.singletonList(endpoint));

        Mockito.when(connectionToolkit.createServiceURL(Mockito.anyString(), Mockito.anyInt()))
                .thenAnswer(
                        new Answer<>() {
                            @Override
                            public JMXServiceURL answer(InvocationOnMock args) throws Throwable {
                                String host = args.getArgument(0);
                                int port = args.getArgument(1);
                                return new JMXServiceURL(
                                        "rmi",
                                        "",
                                        0,
                                        "/jndi/rmi://" + host + ":" + port + "/jmxrmi");
                            }
                        });

        platformClient.start();
        List<ServiceRef> result = platformClient.listDiscoverableServices();
        ServiceRef serv1 =
                new ServiceRef(
                        connectionToolkit,
                        address2.getIp(),
                        port2.getPort(),
                        address2.getTargetRef().getName());
        ServiceRef serv2 =
                new ServiceRef(
                        connectionToolkit,
                        address3.getIp(),
                        port2.getPort(),
                        address3.getTargetRef().getName());
        ServiceRef serv3 =
                new ServiceRef(
                        connectionToolkit,
                        address4.getIp(),
                        port3.getPort(),
                        address4.getTargetRef().getName());

        MatcherAssert.assertThat(result, Matchers.equalTo(Arrays.asList(serv1, serv2, serv3)));
    }

    @Test
    public void shouldSubscribeWatchWhenStarted() throws Exception {
        MixedOperation op = Mockito.mock(MixedOperation.class);
        Mockito.when(k8sClient.endpoints()).thenReturn(op);
        Mockito.when(op.inNamespace(Mockito.anyString())).thenReturn(op);

        Mockito.verifyNoInteractions(k8sClient);

        platformClient.start();

        InOrder inOrder = Mockito.inOrder(k8sClient, op);
        inOrder.verify(k8sClient).endpoints();
        inOrder.verify(op).inNamespace(NAMESPACE);
        inOrder.verify(op).watch(Mockito.any(Watcher.class));
        Mockito.verifyNoMoreInteractions(k8sClient);
        Mockito.verifyNoMoreInteractions(op);
    }

    @Test
    public void shouldNotifyOnAsyncAdded() throws Exception {
        MixedOperation op = Mockito.mock(MixedOperation.class);
        Mockito.when(k8sClient.endpoints()).thenReturn(op);
        Mockito.when(op.inNamespace(Mockito.anyString())).thenReturn(op);

        Mockito.when(connectionToolkit.createServiceURL(Mockito.anyString(), Mockito.anyInt()))
                .thenAnswer(
                        new Answer<>() {
                            @Override
                            public JMXServiceURL answer(InvocationOnMock args) throws Throwable {
                                String host = args.getArgument(0);
                                int port = args.getArgument(1);
                                return new JMXServiceURL(
                                        "rmi",
                                        "",
                                        0,
                                        "/jndi/rmi://" + host + ":" + port + "/jmxrmi");
                            }
                        });

        ObjectReference objRef = Mockito.mock(ObjectReference.class);
        Mockito.when(objRef.getName()).thenReturn("targetA");
        EndpointAddress address = Mockito.mock(EndpointAddress.class);
        Mockito.when(address.getIp()).thenReturn("127.0.0.1");
        Mockito.when(address.getTargetRef()).thenReturn(objRef);
        EndpointPort port = Mockito.mock(EndpointPort.class);
        Mockito.when(port.getPort()).thenReturn(9999);
        Mockito.when(port.getName()).thenReturn("jfr-jmx");
        EndpointSubset subset = Mockito.mock(EndpointSubset.class);
        Mockito.when(subset.getAddresses()).thenReturn(Arrays.asList(address));
        Mockito.when(subset.getPorts()).thenReturn(Collections.singletonList(port));

        Endpoints endpoints = Mockito.mock(Endpoints.class);
        Mockito.when(endpoints.getSubsets()).thenReturn(Arrays.asList(subset));

        Notification notification = Mockito.mock(Notification.class);
        Notification.Builder builder = Mockito.mock(Notification.Builder.class);
        Mockito.when(builder.metaCategory(Mockito.any())).thenReturn(builder);
        Mockito.when(builder.message(Mockito.any())).thenReturn(builder);
        Mockito.when(builder.build()).thenReturn(notification);
        Mockito.when(notificationFactory.createBuilder()).thenReturn(builder);

        platformClient.start();

        ArgumentCaptor<Watcher> watcherCaptor = ArgumentCaptor.forClass(Watcher.class);
        Mockito.verify(op).watch(watcherCaptor.capture());
        Watcher watcher = watcherCaptor.getValue();
        MatcherAssert.assertThat(watcher, Matchers.notNullValue());

        watcher.eventReceived(Action.ADDED, endpoints);

        ServiceRef serviceRef =
                new ServiceRef(
                        connectionToolkit,
                        address.getIp(),
                        port.getPort(),
                        address.getTargetRef().getName());

        Mockito.verify(notificationFactory, Mockito.times(1)).createBuilder();
        Mockito.verify(builder).metaCategory("TargetJvmDiscovery");
        Mockito.verify(builder)
                .message(
                        Map.of("event", Map.of("kind", EventKind.FOUND, "serviceRef", serviceRef)));
        Mockito.verify(builder).build();
        Mockito.verify(notification, Mockito.times(1)).send();
    }

    @Test
    public void shouldNotifyOnAsyncDeleted() throws Exception {
        MixedOperation op = Mockito.mock(MixedOperation.class);
        Mockito.when(k8sClient.endpoints()).thenReturn(op);
        Mockito.when(op.inNamespace(Mockito.anyString())).thenReturn(op);

        Mockito.when(connectionToolkit.createServiceURL(Mockito.anyString(), Mockito.anyInt()))
                .thenAnswer(
                        new Answer<>() {
                            @Override
                            public JMXServiceURL answer(InvocationOnMock args) throws Throwable {
                                String host = args.getArgument(0);
                                int port = args.getArgument(1);
                                return new JMXServiceURL(
                                        "rmi",
                                        "",
                                        0,
                                        "/jndi/rmi://" + host + ":" + port + "/jmxrmi");
                            }
                        });

        ObjectReference objRef = Mockito.mock(ObjectReference.class);
        Mockito.when(objRef.getName()).thenReturn("targetA");
        EndpointAddress address = Mockito.mock(EndpointAddress.class);
        Mockito.when(address.getIp()).thenReturn("127.0.0.1");
        Mockito.when(address.getTargetRef()).thenReturn(objRef);
        EndpointPort port = Mockito.mock(EndpointPort.class);
        Mockito.when(port.getPort()).thenReturn(9999);
        Mockito.when(port.getName()).thenReturn("jfr-jmx");
        EndpointSubset subset = Mockito.mock(EndpointSubset.class);
        Mockito.when(subset.getAddresses()).thenReturn(Arrays.asList(address));
        Mockito.when(subset.getPorts()).thenReturn(Collections.singletonList(port));

        Endpoints endpoints = Mockito.mock(Endpoints.class);
        Mockito.when(endpoints.getSubsets()).thenReturn(Arrays.asList(subset));

        Notification notification = Mockito.mock(Notification.class);
        Notification.Builder builder = Mockito.mock(Notification.Builder.class);
        Mockito.when(builder.metaCategory(Mockito.any())).thenReturn(builder);
        Mockito.when(builder.message(Mockito.any())).thenReturn(builder);
        Mockito.when(builder.build()).thenReturn(notification);
        Mockito.when(notificationFactory.createBuilder()).thenReturn(builder);

        platformClient.start();

        ArgumentCaptor<Watcher> watcherCaptor = ArgumentCaptor.forClass(Watcher.class);
        Mockito.verify(op).watch(watcherCaptor.capture());
        Watcher watcher = watcherCaptor.getValue();
        MatcherAssert.assertThat(watcher, Matchers.notNullValue());

        watcher.eventReceived(Action.DELETED, endpoints);

        ServiceRef serviceRef =
                new ServiceRef(
                        connectionToolkit,
                        address.getIp(),
                        port.getPort(),
                        address.getTargetRef().getName());

        Mockito.verify(notificationFactory, Mockito.times(1)).createBuilder();
        Mockito.verify(builder).metaCategory("TargetJvmDiscovery");
        Mockito.verify(builder)
                .message(Map.of("event", Map.of("kind", EventKind.LOST, "serviceRef", serviceRef)));
        Mockito.verify(builder).build();
        Mockito.verify(notification, Mockito.times(1)).send();
    }

    @Test
    public void shouldNotifyOnAsyncModified() throws Exception {
        MixedOperation op = Mockito.mock(MixedOperation.class);
        Mockito.when(k8sClient.endpoints()).thenReturn(op);
        Mockito.when(op.inNamespace(Mockito.anyString())).thenReturn(op);

        Mockito.when(connectionToolkit.createServiceURL(Mockito.anyString(), Mockito.anyInt()))
                .thenAnswer(
                        new Answer<>() {
                            @Override
                            public JMXServiceURL answer(InvocationOnMock args) throws Throwable {
                                String host = args.getArgument(0);
                                int port = args.getArgument(1);
                                return new JMXServiceURL(
                                        "rmi",
                                        "",
                                        0,
                                        "/jndi/rmi://" + host + ":" + port + "/jmxrmi");
                            }
                        });

        ObjectReference objRef = Mockito.mock(ObjectReference.class);
        Mockito.when(objRef.getName()).thenReturn("targetA");
        EndpointAddress address = Mockito.mock(EndpointAddress.class);
        Mockito.when(address.getIp()).thenReturn("127.0.0.1");
        Mockito.when(address.getTargetRef()).thenReturn(objRef);
        EndpointPort port = Mockito.mock(EndpointPort.class);
        Mockito.when(port.getPort()).thenReturn(9999);
        Mockito.when(port.getName()).thenReturn("jfr-jmx");
        EndpointSubset subset = Mockito.mock(EndpointSubset.class);
        Mockito.when(subset.getAddresses()).thenReturn(Arrays.asList(address));
        Mockito.when(subset.getPorts()).thenReturn(Collections.singletonList(port));

        Endpoints endpoints = Mockito.mock(Endpoints.class);
        Mockito.when(endpoints.getSubsets()).thenReturn(Arrays.asList(subset));

        Notification notification = Mockito.mock(Notification.class);
        Notification.Builder builder = Mockito.mock(Notification.Builder.class);
        Mockito.when(builder.metaCategory(Mockito.any())).thenReturn(builder);
        Mockito.when(builder.message(Mockito.any())).thenReturn(builder);
        Mockito.when(builder.build()).thenReturn(notification);
        Mockito.when(notificationFactory.createBuilder()).thenReturn(builder);

        platformClient.start();

        ArgumentCaptor<Watcher> watcherCaptor = ArgumentCaptor.forClass(Watcher.class);
        Mockito.verify(op).watch(watcherCaptor.capture());
        Watcher watcher = watcherCaptor.getValue();
        MatcherAssert.assertThat(watcher, Matchers.notNullValue());

        watcher.eventReceived(Action.MODIFIED, endpoints);

        ServiceRef serviceRef =
                new ServiceRef(
                        connectionToolkit,
                        address.getIp(),
                        port.getPort(),
                        address.getTargetRef().getName());

        Mockito.verify(notificationFactory, Mockito.times(2)).createBuilder();
        Mockito.verify(builder, Mockito.times(2)).metaCategory("TargetJvmDiscovery");

        InOrder messageOrder = Mockito.inOrder(builder);
        messageOrder
                .verify(builder)
                .message(Map.of("event", Map.of("kind", EventKind.LOST, "serviceRef", serviceRef)));
        messageOrder
                .verify(builder)
                .message(
                        Map.of("event", Map.of("kind", EventKind.FOUND, "serviceRef", serviceRef)));
        Mockito.verify(builder, Mockito.times(2)).build();
        Mockito.verify(notification, Mockito.times(2)).send();
    }
}
