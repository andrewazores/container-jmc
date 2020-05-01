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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import org.openjdk.jmc.rjmx.services.jfr.FlightRecorderException;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnection;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;

@ExtendWith(MockitoExtension.class)
class StopRecordingCommandTest {

    StopRecordingCommand command;
    @Mock ClientWriter cw;
    @Mock JFRConnection connection;
    @Mock IFlightRecorderService service;

    @BeforeEach
    void setup() {
        command = new StopRecordingCommand(cw);
        command.connectionChanged(connection);
    }

    @Test
    void shouldBeNamedStop() {
        MatcherAssert.assertThat(command.getName(), Matchers.equalTo("stop"));
    }

    @Test
    void shouldNotExpectNoArg() {
        assertFalse(command.validate(new String[0]));
        verify(cw).println("Expected one argument: recording name");
    }

    @Test
    void shouldNotExpectMalformedArg() {
        assertFalse(command.validate(new String[] {"."}));
        verify(cw).println(". is an invalid recording name");
    }

    @Test
    void shouldExpectRecordingNameArg() {
        assertTrue(command.validate(new String[] {"foo"}));
        verifyZeroInteractions(cw);
    }

    @Test
    void shouldHandleNoRecordingFound() throws Exception {
        verifyZeroInteractions(service);
        verifyZeroInteractions(connection);

        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordings()).thenReturn(Collections.emptyList());

        command.execute(new String[] {"foo"});

        verifyNoMoreInteractions(service);
        verifyNoMoreInteractions(connection);
        verify(cw).println("Recording with name \"foo\" not found");
    }

    @Test
    void shouldHandleRecordingFound() throws Exception {
        verifyZeroInteractions(service);
        verifyZeroInteractions(connection);

        IRecordingDescriptor fooDescriptor = mock(IRecordingDescriptor.class);
        when(fooDescriptor.getName()).thenReturn("foo");
        IRecordingDescriptor barDescriptor = mock(IRecordingDescriptor.class);
        when(barDescriptor.getName()).thenReturn("bar");

        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordings())
                .thenReturn(Arrays.asList(barDescriptor, fooDescriptor));

        command.execute(new String[] {"foo"});

        ArgumentCaptor<IRecordingDescriptor> descriptorCaptor =
                ArgumentCaptor.forClass(IRecordingDescriptor.class);
        verify(service).stop(descriptorCaptor.capture());
        IRecordingDescriptor captured = descriptorCaptor.getValue();
        MatcherAssert.assertThat(captured, Matchers.sameInstance(fooDescriptor));

        verifyNoMoreInteractions(service);
        verifyNoMoreInteractions(connection);
        verifyZeroInteractions(cw);
    }

    @Test
    void shouldReturnSuccessOutput() throws Exception {
        verifyZeroInteractions(service);
        verifyZeroInteractions(connection);

        IRecordingDescriptor fooDescriptor = mock(IRecordingDescriptor.class);
        when(fooDescriptor.getName()).thenReturn("foo");
        IRecordingDescriptor barDescriptor = mock(IRecordingDescriptor.class);
        when(barDescriptor.getName()).thenReturn("bar");

        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordings())
                .thenReturn(Arrays.asList(barDescriptor, fooDescriptor));

        SerializableCommand.Output<?> out = command.serializableExecute(new String[] {"foo"});
        MatcherAssert.assertThat(out, Matchers.instanceOf(SerializableCommand.SuccessOutput.class));

        ArgumentCaptor<IRecordingDescriptor> descriptorCaptor =
                ArgumentCaptor.forClass(IRecordingDescriptor.class);
        verify(service).stop(descriptorCaptor.capture());
        IRecordingDescriptor captured = descriptorCaptor.getValue();
        MatcherAssert.assertThat(captured, Matchers.sameInstance(fooDescriptor));

        verifyNoMoreInteractions(service);
        verifyNoMoreInteractions(connection);
    }

    @Test
    void shouldReturnFailureOutput() throws Exception {
        verifyZeroInteractions(service);
        verifyZeroInteractions(connection);

        IRecordingDescriptor fooDescriptor = mock(IRecordingDescriptor.class);
        when(fooDescriptor.getName()).thenReturn("foo");

        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordings()).thenReturn(Collections.singletonList(fooDescriptor));

        SerializableCommand.Output<?> out = command.serializableExecute(new String[] {"bar"});
        MatcherAssert.assertThat(out, Matchers.instanceOf(SerializableCommand.FailureOutput.class));
        MatcherAssert.assertThat(
                out.getPayload(), Matchers.equalTo("Recording with name \"bar\" not found"));

        verifyNoMoreInteractions(service);
        verifyNoMoreInteractions(connection);
    }

    @Test
    void shouldReturnExceptionOutput() throws Exception {
        verifyZeroInteractions(service);
        verifyZeroInteractions(connection);

        IRecordingDescriptor fooDescriptor = mock(IRecordingDescriptor.class);
        when(fooDescriptor.getName()).thenReturn("foo");

        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordings()).thenReturn(Collections.singletonList(fooDescriptor));
        doThrow(FlightRecorderException.class).when(service).stop(Mockito.any());

        SerializableCommand.Output<?> out = command.serializableExecute(new String[] {"foo"});
        MatcherAssert.assertThat(
                out, Matchers.instanceOf(SerializableCommand.ExceptionOutput.class));
        MatcherAssert.assertThat(out.getPayload(), Matchers.equalTo("FlightRecorderException: "));
    }
}
