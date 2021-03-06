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
package io.cryostat.recordings;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import javax.management.remote.JMXServiceURL;

import org.openjdk.jmc.common.unit.IQuantity;
import org.openjdk.jmc.common.unit.QuantityConversionException;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import io.cryostat.core.net.JFRConnection;
import io.cryostat.core.sys.Clock;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.reports.ReportService;
import io.cryostat.platform.PlatformClient;
import io.cryostat.platform.ServiceRef;
import io.cryostat.util.URIUtil;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
class RecordingArchiveHelperTest {

    RecordingArchiveHelper recordingArchiveHelper;
    @Mock FileSystem fs;
    @Mock Path recordingsPath;
    @Mock TargetConnectionManager targetConnectionManager;
    @Mock Clock clock;
    @Mock PlatformClient platformClient;
    @Mock ReportService reportService;

    @Mock JFRConnection connection;
    @Mock IFlightRecorderService service;

    String targetId = "fooTarget";
    String recordingName = "someRecording";

    @BeforeEach
    void setup() {
        this.recordingArchiveHelper =
                new RecordingArchiveHelper(
                        fs,
                        recordingsPath,
                        targetConnectionManager,
                        clock,
                        platformClient,
                        reportService);
    }

    @Test
    void saveRecordingShouldThrowIfNoMatchingRecordingFound() throws Exception {
        Mockito.when(
                        targetConnectionManager.executeConnectedTask(
                                Mockito.any(),
                                Mockito.any(TargetConnectionManager.ConnectedTask.class)))
                .thenAnswer(
                        new Answer<>() {
                            @Override
                            public Object answer(InvocationOnMock invocation) throws Throwable {
                                TargetConnectionManager.ConnectedTask task =
                                        (TargetConnectionManager.ConnectedTask)
                                                invocation.getArgument(1);
                                return task.execute(connection);
                            }
                        });
        Mockito.when(connection.getService()).thenReturn(service);
        Mockito.when(service.getAvailableRecordings()).thenReturn(List.of());

        Assertions.assertThrows(
                RecordingNotFoundException.class,
                () ->
                        recordingArchiveHelper.saveRecording(
                                new ConnectionDescriptor(targetId), recordingName));
    }

    @Test
    void shouldSaveRecordingWithAlias() throws Exception {

        Mockito.when(
                        targetConnectionManager.executeConnectedTask(
                                Mockito.any(),
                                Mockito.any(TargetConnectionManager.ConnectedTask.class)))
                .thenAnswer(
                        new Answer<>() {
                            @Override
                            public Object answer(InvocationOnMock invocation) throws Throwable {
                                TargetConnectionManager.ConnectedTask task =
                                        (TargetConnectionManager.ConnectedTask)
                                                invocation.getArgument(1);
                                return task.execute(connection);
                            }
                        });
        Mockito.when(connection.getService()).thenReturn(service);
        IRecordingDescriptor descriptor = Mockito.mock(IRecordingDescriptor.class);
        Mockito.when(descriptor.getName()).thenReturn(recordingName);
        Mockito.when(service.getAvailableRecordings()).thenReturn(List.of(descriptor));

        ServiceRef serviceRef1 =
                new ServiceRef(
                        URIUtil.convert(
                                new JMXServiceURL(
                                        "service:jmx:rmi:///jndi/rmi://cryostat:9091/jmxrmi")),
                        "some.Alias.1");
        ServiceRef serviceRef2 =
                new ServiceRef(
                        URIUtil.convert(
                                new JMXServiceURL(
                                        "service:jmx:rmi:///jndi/rmi://cryostat:9092/jmxrmi")),
                        "some.Alias.2");
        ServiceRef serviceRef3 =
                new ServiceRef(
                        URIUtil.convert(
                                new JMXServiceURL(
                                        "service:jmx:rmi:///jndi/rmi://cryostat:9093/jmxrmi")),
                        "some.Alias.3");

        Mockito.when(platformClient.listDiscoverableServices())
                .thenReturn(List.of(serviceRef1, serviceRef2, serviceRef3));
        Mockito.when(connection.getJMXURL())
                .thenReturn(
                        (new JMXServiceURL("service:jmx:rmi:///jndi/rmi://cryostat:9092/jmxrmi")));

        Instant now = Instant.now();
        Mockito.when(clock.now()).thenReturn(now);
        Mockito.when(fs.exists(Mockito.any())).thenReturn(false);
        InputStream stream = Mockito.mock(InputStream.class);
        Mockito.when(service.openStream(descriptor, false)).thenReturn(stream);
        Path destination = Mockito.mock(Path.class);
        Mockito.when(recordingsPath.resolve(Mockito.anyString())).thenReturn(destination);

        String saveName =
                recordingArchiveHelper.saveRecording(
                        new ConnectionDescriptor(targetId), recordingName);

        String timestamp = now.truncatedTo(ChronoUnit.SECONDS).toString().replaceAll("[-:]+", "");
        MatcherAssert.assertThat(
                saveName, Matchers.equalTo("some-Alias-2_someRecording_" + timestamp + ".jfr"));
        Mockito.verify(fs).copy(Mockito.eq(stream), Mockito.eq(destination));
    }

    @Test
    void shouldSaveRecordingWithoutAlias() throws Exception {
        Mockito.when(
                        targetConnectionManager.executeConnectedTask(
                                Mockito.any(),
                                Mockito.any(TargetConnectionManager.ConnectedTask.class)))
                .thenAnswer(
                        new Answer<>() {
                            @Override
                            public Object answer(InvocationOnMock invocation) throws Throwable {
                                TargetConnectionManager.ConnectedTask task =
                                        (TargetConnectionManager.ConnectedTask)
                                                invocation.getArgument(1);
                                return task.execute(connection);
                            }
                        });
        Mockito.when(connection.getService()).thenReturn(service);
        IRecordingDescriptor descriptor = Mockito.mock(IRecordingDescriptor.class);
        Mockito.when(descriptor.getName()).thenReturn(recordingName);
        Mockito.when(service.getAvailableRecordings()).thenReturn(List.of(descriptor));

        ServiceRef serviceRef1 =
                new ServiceRef(
                        URIUtil.convert(
                                new JMXServiceURL(
                                        "service:jmx:rmi:///jndi/rmi://cryostat:9091/jmxrmi")),
                        "some.Alias.1");
        ServiceRef serviceRef2 =
                new ServiceRef(
                        URIUtil.convert(
                                new JMXServiceURL(
                                        "service:jmx:rmi:///jndi/rmi://cryostat:9092/jmxrmi")),
                        null);
        ServiceRef serviceRef3 =
                new ServiceRef(
                        URIUtil.convert(
                                new JMXServiceURL(
                                        "service:jmx:rmi:///jndi/rmi://cryostat:9093/jmxrmi")),
                        "some.Alias.3");

        Mockito.when(platformClient.listDiscoverableServices())
                .thenReturn(List.of(serviceRef1, serviceRef2, serviceRef3));
        Mockito.when(connection.getJMXURL())
                .thenReturn(
                        (new JMXServiceURL("service:jmx:rmi:///jndi/rmi://cryostat:9092/jmxrmi")));
        Mockito.when(connection.getHost()).thenReturn("some-hostname.local");

        Instant now = Instant.now();
        Mockito.when(clock.now()).thenReturn(now);
        Mockito.when(fs.exists(Mockito.any())).thenReturn(false);
        InputStream stream = Mockito.mock(InputStream.class);
        Mockito.when(service.openStream(descriptor, false)).thenReturn(stream);
        Path destination = Mockito.mock(Path.class);
        Mockito.when(recordingsPath.resolve(Mockito.anyString())).thenReturn(destination);

        String saveName =
                recordingArchiveHelper.saveRecording(
                        new ConnectionDescriptor(targetId), recordingName);

        String timestamp = now.truncatedTo(ChronoUnit.SECONDS).toString().replaceAll("[-:]+", "");
        MatcherAssert.assertThat(
                saveName,
                Matchers.equalTo("some-hostname-local_someRecording_" + timestamp + ".jfr"));
        Mockito.verify(fs).copy(Mockito.eq(stream), Mockito.eq(destination));
    }

    @Test
    void shouldSaveRecordingWithoutServiceRef() throws Exception {
        Mockito.when(
                        targetConnectionManager.executeConnectedTask(
                                Mockito.any(),
                                Mockito.any(TargetConnectionManager.ConnectedTask.class)))
                .thenAnswer(
                        new Answer<>() {
                            @Override
                            public Object answer(InvocationOnMock invocation) throws Throwable {
                                TargetConnectionManager.ConnectedTask task =
                                        (TargetConnectionManager.ConnectedTask)
                                                invocation.getArgument(1);
                                return task.execute(connection);
                            }
                        });
        Mockito.when(connection.getService()).thenReturn(service);
        IRecordingDescriptor descriptor = Mockito.mock(IRecordingDescriptor.class);
        Mockito.when(descriptor.getName()).thenReturn(recordingName);
        Mockito.when(service.getAvailableRecordings()).thenReturn(List.of(descriptor));

        ServiceRef serviceRef1 =
                new ServiceRef(
                        URIUtil.convert(
                                new JMXServiceURL(
                                        "service:jmx:rmi:///jndi/rmi://cryostat:9091/jmxrmi")),
                        "some.Alias.1");
        ServiceRef serviceRef3 =
                new ServiceRef(
                        URIUtil.convert(
                                new JMXServiceURL(
                                        "service:jmx:rmi:///jndi/rmi://cryostat:9093/jmxrmi")),
                        "some.Alias.3");

        Mockito.when(platformClient.listDiscoverableServices())
                .thenReturn(List.of(serviceRef1, serviceRef3));
        Mockito.when(connection.getJMXURL())
                .thenReturn(
                        (new JMXServiceURL("service:jmx:rmi:///jndi/rmi://cryostat:9092/jmxrmi")));
        Mockito.when(connection.getHost()).thenReturn("some-hostname.local");

        Instant now = Instant.now();
        Mockito.when(clock.now()).thenReturn(now);
        Mockito.when(fs.exists(Mockito.any())).thenReturn(false);
        InputStream stream = Mockito.mock(InputStream.class);
        Mockito.when(service.openStream(descriptor, false)).thenReturn(stream);
        Path destination = Mockito.mock(Path.class);
        Mockito.when(recordingsPath.resolve(Mockito.anyString())).thenReturn(destination);

        String saveName =
                recordingArchiveHelper.saveRecording(
                        new ConnectionDescriptor(targetId), recordingName);

        String timestamp = now.truncatedTo(ChronoUnit.SECONDS).toString().replaceAll("[-:]+", "");
        MatcherAssert.assertThat(
                saveName,
                Matchers.equalTo("some-hostname-local_someRecording_" + timestamp + ".jfr"));
        Mockito.verify(fs).copy(Mockito.eq(stream), Mockito.eq(destination));
    }

    @Test
    void shouldSaveRecordingThatEndsWithJfr() throws Exception {
        String recordingName = "someRecording.jfr";
        Mockito.when(
                        targetConnectionManager.executeConnectedTask(
                                Mockito.any(),
                                Mockito.any(TargetConnectionManager.ConnectedTask.class)))
                .thenAnswer(
                        new Answer<>() {
                            @Override
                            public Object answer(InvocationOnMock invocation) throws Throwable {
                                TargetConnectionManager.ConnectedTask task =
                                        (TargetConnectionManager.ConnectedTask)
                                                invocation.getArgument(1);
                                return task.execute(connection);
                            }
                        });
        Mockito.when(connection.getService()).thenReturn(service);
        IRecordingDescriptor descriptor = Mockito.mock(IRecordingDescriptor.class);
        Mockito.when(descriptor.getName()).thenReturn(recordingName);
        Mockito.when(service.getAvailableRecordings()).thenReturn(List.of(descriptor));

        ServiceRef serviceRef1 =
                new ServiceRef(
                        URIUtil.convert(
                                new JMXServiceURL(
                                        "service:jmx:rmi:///jndi/rmi://cryostat:9091/jmxrmi")),
                        "some.Alias.1");
        ServiceRef serviceRef2 =
                new ServiceRef(
                        URIUtil.convert(
                                new JMXServiceURL(
                                        "service:jmx:rmi:///jndi/rmi://cryostat:9092/jmxrmi")),
                        "some.Alias.2");
        ServiceRef serviceRef3 =
                new ServiceRef(
                        URIUtil.convert(
                                new JMXServiceURL(
                                        "service:jmx:rmi:///jndi/rmi://cryostat:9093/jmxrmi")),
                        "some.Alias.3");

        Mockito.when(platformClient.listDiscoverableServices())
                .thenReturn(List.of(serviceRef1, serviceRef2, serviceRef3));
        Mockito.when(connection.getJMXURL())
                .thenReturn(
                        (new JMXServiceURL("service:jmx:rmi:///jndi/rmi://cryostat:9092/jmxrmi")));

        Instant now = Instant.now();
        Mockito.when(clock.now()).thenReturn(now);
        Mockito.when(fs.exists(Mockito.any())).thenReturn(false);
        InputStream stream = Mockito.mock(InputStream.class);
        Mockito.when(service.openStream(descriptor, false)).thenReturn(stream);
        Path destination = Mockito.mock(Path.class);
        Mockito.when(recordingsPath.resolve(Mockito.anyString())).thenReturn(destination);

        String saveName =
                recordingArchiveHelper.saveRecording(
                        new ConnectionDescriptor(targetId), recordingName);

        String timestamp = now.truncatedTo(ChronoUnit.SECONDS).toString().replaceAll("[-:]+", "");
        MatcherAssert.assertThat(
                saveName, Matchers.equalTo("some-Alias-2_someRecording_" + timestamp + ".jfr"));
        Mockito.verify(fs).copy(Mockito.eq(stream), Mockito.eq(destination));
    }

    @Test
    void shouldSaveRecordingNumberedCopy() throws Exception {
        Mockito.when(
                        targetConnectionManager.executeConnectedTask(
                                Mockito.any(),
                                Mockito.any(TargetConnectionManager.ConnectedTask.class)))
                .thenAnswer(
                        new Answer<>() {
                            @Override
                            public Object answer(InvocationOnMock invocation) throws Throwable {
                                TargetConnectionManager.ConnectedTask task =
                                        (TargetConnectionManager.ConnectedTask)
                                                invocation.getArgument(1);
                                return task.execute(connection);
                            }
                        });
        Mockito.when(connection.getService()).thenReturn(service);
        IRecordingDescriptor descriptor = Mockito.mock(IRecordingDescriptor.class);
        Mockito.when(descriptor.getName()).thenReturn(recordingName);
        Mockito.when(service.getAvailableRecordings()).thenReturn(List.of(descriptor));

        ServiceRef serviceRef1 =
                new ServiceRef(
                        URIUtil.convert(
                                new JMXServiceURL(
                                        "service:jmx:rmi:///jndi/rmi://cryostat:9091/jmxrmi")),
                        "some.Alias.1");
        ServiceRef serviceRef2 =
                new ServiceRef(
                        URIUtil.convert(
                                new JMXServiceURL(
                                        "service:jmx:rmi:///jndi/rmi://cryostat:9092/jmxrmi")),
                        "some.Alias.2");
        ServiceRef serviceRef3 =
                new ServiceRef(
                        URIUtil.convert(
                                new JMXServiceURL(
                                        "service:jmx:rmi:///jndi/rmi://cryostat:9093/jmxrmi")),
                        "some.Alias.3");

        Mockito.when(platformClient.listDiscoverableServices())
                .thenReturn(List.of(serviceRef1, serviceRef2, serviceRef3));
        Mockito.when(connection.getJMXURL())
                .thenReturn(
                        (new JMXServiceURL("service:jmx:rmi:///jndi/rmi://cryostat:9092/jmxrmi")));

        Instant now = Instant.now();
        Mockito.when(clock.now()).thenReturn(now);
        Mockito.when(fs.exists(Mockito.any())).thenReturn(true).thenReturn(false);
        InputStream stream = Mockito.mock(InputStream.class);
        Mockito.when(service.openStream(descriptor, false)).thenReturn(stream);
        Path destination = Mockito.mock(Path.class);
        Mockito.when(recordingsPath.resolve(Mockito.anyString())).thenReturn(destination);

        String saveName =
                recordingArchiveHelper.saveRecording(
                        new ConnectionDescriptor(targetId), recordingName);

        String timestamp = now.truncatedTo(ChronoUnit.SECONDS).toString().replaceAll("[-:]+", "");
        MatcherAssert.assertThat(
                saveName, Matchers.equalTo("some-Alias-2_someRecording_" + timestamp + ".1.jfr"));
        Mockito.verify(fs).copy(Mockito.eq(stream), Mockito.eq(destination));
    }

    @Test
    void shouldDeleteRecording() throws Exception {

        Mockito.when(targetConnectionManager.executeConnectedTask(Mockito.any(), Mockito.any()))
                .thenAnswer(
                        new Answer() {
                            @Override
                            public Object answer(InvocationOnMock invocation) throws Throwable {
                                TargetConnectionManager.ConnectedTask task =
                                        (TargetConnectionManager.ConnectedTask)
                                                invocation.getArgument(1);
                                return task.execute(connection);
                            }
                        });
        Mockito.when(connection.getService()).thenReturn(service);
        IRecordingDescriptor descriptor = createDescriptor("someRecording");
        ConnectionDescriptor connectionDescriptor = new ConnectionDescriptor("fooTarget");
        Mockito.when(descriptor.getName()).thenReturn(recordingName);
        Mockito.when(service.getAvailableRecordings()).thenReturn(List.of(descriptor));

        recordingArchiveHelper.deleteRecording(connectionDescriptor, recordingName);

        Mockito.verify(service).close(descriptor);

        Mockito.verify(reportService)
                .delete(
                        Mockito.argThat(
                                arg ->
                                        arg.getTargetId()
                                                .equals(connectionDescriptor.getTargetId())),
                        Mockito.eq("someRecording"));
    }

    @Test
    void deleteRecordingShouldHandleRecordingNotFound() throws Exception {
        Mockito.when(targetConnectionManager.executeConnectedTask(Mockito.any(), Mockito.any()))
                .thenAnswer(
                        new Answer() {
                            @Override
                            public Object answer(InvocationOnMock invocation) throws Throwable {
                                TargetConnectionManager.ConnectedTask task =
                                        (TargetConnectionManager.ConnectedTask)
                                                invocation.getArgument(1);
                                return task.execute(connection);
                            }
                        });

        Mockito.when(connection.getService()).thenReturn(service);
        ConnectionDescriptor connectionDescriptor = new ConnectionDescriptor("fooTarget");
        Mockito.when(service.getAvailableRecordings()).thenReturn(List.of());

        Assertions.assertThrows(
                RecordingNotFoundException.class,
                () -> recordingArchiveHelper.deleteRecording(connectionDescriptor, recordingName));
    }

    private static IRecordingDescriptor createDescriptor(String name)
            throws QuantityConversionException {
        IQuantity zeroQuantity = Mockito.mock(IQuantity.class);
        IRecordingDescriptor descriptor = Mockito.mock(IRecordingDescriptor.class);
        Mockito.lenient().when(descriptor.getId()).thenReturn(1L);
        Mockito.lenient().when(descriptor.getName()).thenReturn(name);
        Mockito.lenient()
                .when(descriptor.getState())
                .thenReturn(IRecordingDescriptor.RecordingState.STOPPED);
        Mockito.lenient().when(descriptor.getStartTime()).thenReturn(zeroQuantity);
        Mockito.lenient().when(descriptor.getDuration()).thenReturn(zeroQuantity);
        Mockito.lenient().when(descriptor.isContinuous()).thenReturn(false);
        Mockito.lenient().when(descriptor.getToDisk()).thenReturn(false);
        Mockito.lenient().when(descriptor.getMaxSize()).thenReturn(zeroQuantity);
        Mockito.lenient().when(descriptor.getMaxAge()).thenReturn(zeroQuantity);
        return descriptor;
    }
}
