/*-
 * #%L
 * Container JFR
 * %%
 * Copyright (C) 2020 Red Hat, Inc.
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
package com.redhat.rhjmc.containerjfr.commands.internal;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import com.redhat.rhjmc.containerjfr.commands.SerializableCommand.ExceptionOutput;
import com.redhat.rhjmc.containerjfr.commands.SerializableCommand.MapOutput;
import com.redhat.rhjmc.containerjfr.commands.SerializableCommand.Output;
import com.redhat.rhjmc.containerjfr.commands.internal.UploadRecordingCommand.RecordingNotFoundException;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnection;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;

@ExtendWith(MockitoExtension.class)
class UploadRecordingCommandTest {

    static final String UPLOAD_URL = "http://example.com/";

    @Mock ClientWriter cw;
    @Mock FileSystem fs;
    @Mock Path path;
    @Mock CloseableHttpClient httpClient;
    @Mock JFRConnection conn;
    UploadRecordingCommand cmd;

    @BeforeEach
    void setup() {
        this.cmd = new UploadRecordingCommand(cw, fs, path, () -> httpClient);
    }

    @Test
    void shouldBeNamedUploadRecording() {
        MatcherAssert.assertThat(cmd.getName(), Matchers.equalTo("upload-recording"));
    }

    @Test
    void shouldNotBeAvailableWhenDisconnected() {
        Assertions.assertFalse(cmd.isAvailable());
    }

    @Test
    void shouldBeAvailableWhenConnectedButNoRecordingsPath() {
        cmd.connectionChanged(conn);
        Assertions.assertTrue(cmd.isAvailable());
    }

    @Test
    void shouldBeAvailableWhenDisConnectedWithRecordingsPath() {
        Mockito.when(fs.isDirectory(path)).thenReturn(true);
        Assertions.assertTrue(cmd.isAvailable());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3})
    void shouldNotValidateWrongArgc(int c) {
        Assertions.assertFalse(cmd.validate(new String[c]));
        Mockito.verify(cw).println("Expected two arguments: recording name and upload URL");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"foo", "foo.jfr", "recording", "some-name", "another_name", "123", "abc123"})
    void shouldValidateRecordingNames(String recordingName) {
        Assertions.assertTrue(cmd.validate(new String[] {recordingName, UPLOAD_URL}));
    }

    @ParameterizedTest
    @ValueSource(strings = {".", "some recording", ""})
    void shouldNotValidateInvalidRecordingNames(String recordingName) {
        Assertions.assertFalse(cmd.validate(new String[] {recordingName, UPLOAD_URL}));
        Mockito.verify(cw).println(recordingName + " is an invalid recording name");
    }

    @Nested
    class RecordingSelection {

        @Test
        void shouldSelectInMemoryIfAvailable() throws Exception {
            IFlightRecorderService svc = Mockito.mock(IFlightRecorderService.class);
            IRecordingDescriptor rec = Mockito.mock(IRecordingDescriptor.class);
            InputStream stream = Mockito.mock(InputStream.class);
            Mockito.when(conn.getService()).thenReturn(svc);
            Mockito.when(svc.getAvailableRecordings()).thenReturn(List.of(rec));
            Mockito.when(rec.getName()).thenReturn("foo");
            Mockito.when(svc.openStream(Mockito.any(), Mockito.anyBoolean())).thenReturn(stream);

            cmd.connectionChanged(conn);
            Optional<InputStream> res = cmd.getBestRecordingForName(rec.getName());

            Assertions.assertTrue(res.isPresent());
            MatcherAssert.assertThat(res.get(), Matchers.sameInstance(stream));
            Mockito.verify(svc).openStream(rec, false);
        }

        @Test
        void shouldReadFromDiskIfNotConnected() throws Exception {
            Path rec = Mockito.mock(Path.class);
            InputStream stream = Mockito.mock(InputStream.class);
            Mockito.when(path.resolve(Mockito.anyString())).thenReturn(rec);
            Mockito.when(fs.isRegularFile(rec)).thenReturn(true);
            Mockito.when(fs.isReadable(rec)).thenReturn(true);
            Mockito.when(fs.newInputStream(rec)).thenReturn(stream);

            Optional<InputStream> res = cmd.getBestRecordingForName("foo");

            Assertions.assertTrue(res.isPresent());
            MatcherAssert.assertThat(res.get(), Matchers.instanceOf(BufferedInputStream.class));
        }

        @Test
        void shouldFallThroughToDiskIfNotInMemory() throws Exception {
            IFlightRecorderService svc = Mockito.mock(IFlightRecorderService.class);
            InputStream stream = Mockito.mock(InputStream.class);
            Mockito.when(conn.getService()).thenReturn(svc);
            Mockito.when(svc.getAvailableRecordings()).thenReturn(Collections.emptyList());

            Path rec = Mockito.mock(Path.class);
            Mockito.when(path.resolve(Mockito.anyString())).thenReturn(rec);
            Mockito.when(fs.isRegularFile(rec)).thenReturn(true);
            Mockito.when(fs.isReadable(rec)).thenReturn(true);
            Mockito.when(fs.newInputStream(rec)).thenReturn(stream);

            cmd.connectionChanged(conn);
            Optional<InputStream> res = cmd.getBestRecordingForName("foo");

            Assertions.assertTrue(res.isPresent());
            MatcherAssert.assertThat(res.get(), Matchers.instanceOf(BufferedInputStream.class));
        }

        @Test
        void shouldReturnEmptyIfNotInMemoryAndNotFile() throws Exception {
            Path rec = Mockito.mock(Path.class);
            Mockito.when(path.resolve(Mockito.anyString())).thenReturn(rec);
            Mockito.when(fs.isRegularFile(rec)).thenReturn(false);

            Optional<InputStream> res = cmd.getBestRecordingForName("foo");
            Assertions.assertFalse(res.isPresent());
        }

        @Test
        void shouldReturnEmptyIfNotInMemoryAndNotReadable() throws Exception {
            Path rec = Mockito.mock(Path.class);
            Mockito.when(path.resolve(Mockito.anyString())).thenReturn(rec);
            Mockito.when(fs.isRegularFile(rec)).thenReturn(true);
            Mockito.when(fs.isReadable(rec)).thenReturn(false);

            Optional<InputStream> res = cmd.getBestRecordingForName("foo");
            Assertions.assertFalse(res.isPresent());
        }
    }

    @Nested
    class ExecutionTest {

        @Test
        void shouldThrowExceptionIfRecordingNotFound() throws Exception {
            IFlightRecorderService svc = Mockito.mock(IFlightRecorderService.class);
            IRecordingDescriptor rec = Mockito.mock(IRecordingDescriptor.class);
            Mockito.when(conn.getService()).thenReturn(svc);
            Mockito.when(svc.getAvailableRecordings()).thenReturn(Collections.emptyList());
            Mockito.when(rec.getName()).thenReturn("foo");

            cmd.connectionChanged(conn);
            Assertions.assertThrows(
                    RecordingNotFoundException.class,
                    () -> cmd.execute(new String[] {rec.getName(), UPLOAD_URL}));
        }

        @Test
        void shouldDoUpload() throws Exception {
            IFlightRecorderService svc = Mockito.mock(IFlightRecorderService.class);
            IRecordingDescriptor rec = Mockito.mock(IRecordingDescriptor.class);
            InputStream stream = Mockito.mock(InputStream.class);
            Mockito.when(conn.getService()).thenReturn(svc);
            Mockito.when(svc.getAvailableRecordings()).thenReturn(List.of(rec));
            Mockito.when(rec.getName()).thenReturn("foo");
            Mockito.when(svc.openStream(Mockito.any(), Mockito.anyBoolean())).thenReturn(stream);

            CloseableHttpResponse httpResp = Mockito.mock(CloseableHttpResponse.class);
            HttpEntity entity = Mockito.mock(HttpEntity.class);
            StatusLine status = Mockito.mock(StatusLine.class);
            Mockito.when(httpClient.execute(Mockito.any())).thenReturn(httpResp);
            Mockito.when(httpResp.getEntity()).thenReturn(entity);
            Mockito.when(httpResp.getStatusLine()).thenReturn(status);
            Mockito.when(status.toString()).thenReturn("status_line");
            Mockito.when(entity.getContent())
                    .thenReturn(new ByteArrayInputStream("entity_response".getBytes()));

            cmd.connectionChanged(conn);
            cmd.execute(new String[] {"foo", UPLOAD_URL});

            ArgumentCaptor<HttpUriRequest> captor = ArgumentCaptor.forClass(HttpUriRequest.class);
            Mockito.verify(httpClient).execute(captor.capture());
            MatcherAssert.assertThat(captor.getValue(), Matchers.instanceOf(HttpPost.class));
            HttpPost post = (HttpPost) captor.getValue();
            MatcherAssert.assertThat(post.getURI().toString(), Matchers.equalTo(UPLOAD_URL));
            MatcherAssert.assertThat(post.getEntity(), Matchers.notNullValue());
            Mockito.verify(cw).println("[status_line] entity_response");
        }
    }

    @Nested
    class SerializableExecutionTest {

        @Test
        void shouldReturnExceptionIfRecordingNotFound() throws Exception {
            IFlightRecorderService svc = Mockito.mock(IFlightRecorderService.class);
            IRecordingDescriptor rec = Mockito.mock(IRecordingDescriptor.class);
            Mockito.when(conn.getService()).thenReturn(svc);
            Mockito.when(svc.getAvailableRecordings()).thenReturn(Collections.emptyList());
            Mockito.when(rec.getName()).thenReturn("foo");

            cmd.connectionChanged(conn);
            Output<?> out = cmd.serializableExecute(new String[] {rec.getName(), UPLOAD_URL});
            MatcherAssert.assertThat(out, Matchers.instanceOf(ExceptionOutput.class));
        }

        @Test
        void shouldDoUpload() throws Exception {
            IFlightRecorderService svc = Mockito.mock(IFlightRecorderService.class);
            IRecordingDescriptor rec = Mockito.mock(IRecordingDescriptor.class);
            InputStream stream = Mockito.mock(InputStream.class);
            Mockito.when(conn.getService()).thenReturn(svc);
            Mockito.when(svc.getAvailableRecordings()).thenReturn(List.of(rec));
            Mockito.when(rec.getName()).thenReturn("foo");
            Mockito.when(svc.openStream(Mockito.any(), Mockito.anyBoolean())).thenReturn(stream);

            CloseableHttpResponse httpResp = Mockito.mock(CloseableHttpResponse.class);
            HttpEntity entity = Mockito.mock(HttpEntity.class);
            StatusLine status = Mockito.mock(StatusLine.class);
            Mockito.when(httpClient.execute(Mockito.any())).thenReturn(httpResp);
            Mockito.when(httpResp.getEntity()).thenReturn(entity);
            Mockito.when(httpResp.getStatusLine()).thenReturn(status);
            Mockito.when(status.toString()).thenReturn("status_line");
            Mockito.when(entity.getContent())
                    .thenReturn(new ByteArrayInputStream("entity_response".getBytes()));

            cmd.connectionChanged(conn);
            Output<?> out = cmd.serializableExecute(new String[] {"foo", UPLOAD_URL});

            MatcherAssert.assertThat(out, Matchers.instanceOf(MapOutput.class));
            MatcherAssert.assertThat(
                    out.getPayload().toString(),
                    Matchers.allOf(
                            Matchers.startsWith("{"),
                            Matchers.endsWith("}"),
                            Matchers.containsString("body=entity_response"),
                            Matchers.containsString("status=status_line"),
                            Matchers.containsString(", ")));

            ArgumentCaptor<HttpUriRequest> captor = ArgumentCaptor.forClass(HttpUriRequest.class);
            Mockito.verify(httpClient).execute(captor.capture());
            MatcherAssert.assertThat(captor.getValue(), Matchers.instanceOf(HttpPost.class));
            HttpPost post = (HttpPost) captor.getValue();
            MatcherAssert.assertThat(post.getURI().toString(), Matchers.equalTo(UPLOAD_URL));
            MatcherAssert.assertThat(post.getEntity(), Matchers.notNullValue());
        }
    }
}
