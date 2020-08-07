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
package com.redhat.rhjmc.containerjfr.net.web.handlers;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import org.openjdk.jmc.common.unit.IConstrainedMap;
import org.openjdk.jmc.common.unit.IQuantity;
import org.openjdk.jmc.common.unit.QuantityConversionException;
import org.openjdk.jmc.flightrecorder.configuration.events.EventOptionID;
import org.openjdk.jmc.flightrecorder.configuration.recording.RecordingOptionsBuilder;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import com.google.gson.Gson;

import com.redhat.rhjmc.containerjfr.MainModule;
import com.redhat.rhjmc.containerjfr.commands.internal.EventOptionsBuilder;
import com.redhat.rhjmc.containerjfr.commands.internal.RecordingOptionsBuilderFactory;
import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnection;
import com.redhat.rhjmc.containerjfr.net.AuthManager;
import com.redhat.rhjmc.containerjfr.net.TargetConnectionManager;
import com.redhat.rhjmc.containerjfr.net.TargetConnectionManager.ConnectedTask;
import com.redhat.rhjmc.containerjfr.net.web.WebServer;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.impl.HttpStatusException;

@ExtendWith(MockitoExtension.class)
class TargetRecordingsPostHandlerTest {

    TargetRecordingsPostHandler handler;
    @Mock AuthManager auth;
    @Mock TargetConnectionManager targetConnectionManager;
    @Mock RecordingOptionsBuilderFactory recordingOptionsBuilderFactory;
    @Mock EventOptionsBuilder.Factory eventOptionsBuilderFactory;
    @Mock WebServer webServer;
    @Mock Logger logger;
    Gson gson = MainModule.provideGson(logger);

    @Mock JFRConnection connection;
    @Mock IFlightRecorderService service;
    @Mock RoutingContext ctx;
    @Mock HttpServerRequest req;
    @Mock HttpServerResponse resp;

    @BeforeEach
    void setup() {
        this.handler =
                new TargetRecordingsPostHandler(
                        auth,
                        targetConnectionManager,
                        recordingOptionsBuilderFactory,
                        eventOptionsBuilderFactory,
                        () -> webServer,
                        gson);
    }

    @Test
    void shouldHandlePOST() {
        MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.POST));
    }

    @Test
    void shouldHandleCorrectPath() {
        MatcherAssert.assertThat(
                handler.path(), Matchers.equalTo("/api/v1/targets/:targetId/recordings"));
    }

    @Test
    void shouldStartRecording() throws Exception {
        Mockito.when(auth.validateHttpHeader(Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));
        Mockito.when(targetConnectionManager.executeConnectedTask(Mockito.any(), Mockito.any()))
                .thenAnswer(
                        arg0 -> ((ConnectedTask<Object>) arg0.getArgument(1)).execute(connection));
        Mockito.when(connection.getService()).thenReturn(service);
        IConstrainedMap<String> recordingOptions = Mockito.mock(IConstrainedMap.class);
        RecordingOptionsBuilder recordingOptionsBuilder =
                Mockito.mock(RecordingOptionsBuilder.class);
        Mockito.when(recordingOptionsBuilderFactory.create(Mockito.any()))
                .thenReturn(recordingOptionsBuilder);
        Mockito.when(recordingOptionsBuilder.name(Mockito.any()))
                .thenReturn(recordingOptionsBuilder);
        Mockito.when(recordingOptionsBuilder.duration(Mockito.anyLong()))
                .thenReturn(recordingOptionsBuilder);
        Mockito.when(recordingOptionsBuilder.build()).thenReturn(recordingOptions);
        EventOptionsBuilder builder = Mockito.mock(EventOptionsBuilder.class);
        Mockito.when(eventOptionsBuilderFactory.create(Mockito.any())).thenReturn(builder);
        IConstrainedMap<EventOptionID> events = Mockito.mock(IConstrainedMap.class);
        Mockito.when(builder.build()).thenReturn(events);

        Mockito.when(
                        webServer.getDownloadURL(
                                Mockito.any(JFRConnection.class), Mockito.anyString()))
                .thenReturn("example-download-url");
        Mockito.when(webServer.getReportURL(Mockito.any(JFRConnection.class), Mockito.anyString()))
                .thenReturn("example-report-url");

        IRecordingDescriptor descriptor = createDescriptor("someRecording");
        Mockito.when(service.start(Mockito.any(), Mockito.any())).thenReturn(descriptor);
        Mockito.when(service.getAvailableRecordings())
                .thenReturn(Collections.emptyList())
                .thenReturn(List.of(descriptor));

        Mockito.when(ctx.pathParam("targetId")).thenReturn("fooHost:9091");
        MultiMap attrs = MultiMap.caseInsensitiveMultiMap();
        Mockito.when(ctx.request()).thenReturn(req);
        Mockito.when(req.headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());
        Mockito.when(req.formAttributes()).thenReturn(attrs);
        attrs.add("recordingName", "someRecording");
        attrs.add("events", "foo.Bar:enabled=true");
        attrs.add("duration", "10");
        Mockito.when(ctx.response()).thenReturn(resp);

        handler.handle(ctx);

        Mockito.verify(resp).setStatusCode(201);
        Mockito.verify(resp).putHeader(HttpHeaders.LOCATION, "/someRecording");
        Mockito.verify(resp).putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        Mockito.verify(resp)
                .end(
                        "{\"downloadUrl\":\"example-download-url\",\"reportUrl\":\"example-report-url\",\"id\":1,\"name\":\"someRecording\",\"state\":\"STOPPED\",\"startTime\":0,\"duration\":0,\"continuous\":false,\"toDisk\":false,\"maxSize\":0,\"maxAge\":0}");

        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<IConstrainedMap<String>> recordingOptionsCaptor =
                ArgumentCaptor.forClass(IConstrainedMap.class);
        ArgumentCaptor<IConstrainedMap<EventOptionID>> eventsCaptor =
                ArgumentCaptor.forClass(IConstrainedMap.class);
        Mockito.verify(recordingOptionsBuilder).name(nameCaptor.capture());
        Mockito.verify(service, Mockito.atLeastOnce()).getAvailableRecordings();
        Mockito.verify(service).start(recordingOptionsCaptor.capture(), eventsCaptor.capture());

        String actualName = nameCaptor.getValue();
        IConstrainedMap<String> actualRecordingOptions = recordingOptionsCaptor.getValue();
        IConstrainedMap<EventOptionID> actualEvents = eventsCaptor.getValue();

        MatcherAssert.assertThat(actualName, Matchers.equalTo("someRecording"));
        MatcherAssert.assertThat(actualEvents, Matchers.sameInstance(events));
        MatcherAssert.assertThat(actualRecordingOptions, Matchers.sameInstance(recordingOptions));
        ArgumentCaptor<String> eventCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> optionCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(builder)
                .addEvent(eventCaptor.capture(), optionCaptor.capture(), valueCaptor.capture());
        Mockito.verify(builder).build();

        MatcherAssert.assertThat(eventCaptor.getValue(), Matchers.equalTo("foo.Bar"));
        MatcherAssert.assertThat(optionCaptor.getValue(), Matchers.equalTo("enabled"));
        MatcherAssert.assertThat(valueCaptor.getValue(), Matchers.equalTo("true"));
    }

    @Test
    void shouldHandleNameCollision() throws Exception {
        Mockito.when(auth.validateHttpHeader(Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));
        IRecordingDescriptor existingRecording = createDescriptor("someRecording");

        Mockito.when(targetConnectionManager.executeConnectedTask(Mockito.any(), Mockito.any()))
                .thenAnswer(
                        arg0 -> ((ConnectedTask<Object>) arg0.getArgument(1)).execute(connection));
        Mockito.when(connection.getService()).thenReturn(service);
        Mockito.when(service.getAvailableRecordings()).thenReturn(Arrays.asList(existingRecording));

        Mockito.when(ctx.pathParam("targetId")).thenReturn("fooHost:9091");
        MultiMap attrs = MultiMap.caseInsensitiveMultiMap();
        Mockito.when(ctx.request()).thenReturn(req);
        Mockito.when(req.headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());
        Mockito.when(req.formAttributes()).thenReturn(attrs);
        attrs.add("recordingName", "someRecording");
        attrs.add("events", "foo.Bar:enabled=true");

        HttpStatusException ex =
                Assertions.assertThrows(HttpStatusException.class, () -> handler.handle(ctx));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(400));

        Mockito.verify(service).getAvailableRecordings();
    }

    @Test
    void shouldHandleException() throws Exception {
        HttpStatusException ex =
                Assertions.assertThrows(HttpStatusException.class, () -> handler.handle(ctx));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(500));
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
