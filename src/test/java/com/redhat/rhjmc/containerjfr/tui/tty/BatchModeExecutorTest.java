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
package com.redhat.rhjmc.containerjfr.tui.tty;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import com.redhat.rhjmc.containerjfr.TestBase;
import com.redhat.rhjmc.containerjfr.commands.CommandRegistry;
import com.redhat.rhjmc.containerjfr.core.tui.ClientReader;
import com.redhat.rhjmc.containerjfr.tui.CommandExecutor;

@ExtendWith(MockitoExtension.class)
class BatchModeExecutorTest extends TestBase {

    CommandExecutor executor;
    @Mock ClientReader mockClientReader;
    @Mock CommandRegistry mockRegistry;

    @BeforeEach
    void setup() {
        executor = new BatchModeExecutor(mockClientReader, mockClientWriter, () -> mockRegistry);
    }

    @Test
    void shouldValidateAndExitWhenPassedNoArgs() throws Exception {
        verifyZeroInteractions(mockClientReader);
        verifyZeroInteractions(mockRegistry);

        when(mockRegistry.validate(eq("exit"), any(String[].class))).thenReturn(true);

        executor.run("");

        MatcherAssert.assertThat(stdout(), Matchers.containsString("\"exit\" \"[]\""));

        InOrder inOrder = inOrder(mockRegistry);
        inOrder.verify(mockRegistry).validate("exit", new String[0]);
        inOrder.verify(mockRegistry).execute("exit", new String[0]);

        verifyNoMoreInteractions(mockClientReader);
        verifyNoMoreInteractions(mockRegistry);
    }

    @Test
    void shouldValidateAndExitWhenPassedOnlyComment() throws Exception {
        verifyZeroInteractions(mockClientReader);
        verifyZeroInteractions(mockRegistry);

        when(mockRegistry.validate(eq("exit"), any(String[].class))).thenReturn(true);

        executor.run("# This should be a comment ;");

        MatcherAssert.assertThat(stdout(), Matchers.containsString("\"exit\" \"[]\""));

        InOrder inOrder = inOrder(mockRegistry);
        inOrder.verify(mockRegistry).validate("exit", new String[0]);
        inOrder.verify(mockRegistry).execute("exit", new String[0]);

        verifyNoMoreInteractions(mockClientReader);
        verifyNoMoreInteractions(mockRegistry);
    }

    @Test
    void shouldValidateAndExecuteSingleCommand() throws Exception {
        verifyZeroInteractions(mockClientReader);
        verifyZeroInteractions(mockRegistry);

        when(mockRegistry.validate(eq("help"), any(String[].class))).thenReturn(true);
        when(mockRegistry.validate(eq("exit"), any(String[].class))).thenReturn(true);

        executor.run("help");

        MatcherAssert.assertThat(
                stdout(),
                Matchers.allOf(
                        Matchers.containsString("\"help\" \"[]\""),
                        Matchers.containsString("\"exit\" \"[]\"")));

        verify(mockRegistry).validate("help", new String[0]);
        verify(mockRegistry).validate("exit", new String[0]);

        InOrder inOrder = inOrder(mockRegistry);
        inOrder.verify(mockRegistry).execute("help", new String[0]);
        inOrder.verify(mockRegistry).execute("exit", new String[0]);

        verifyNoMoreInteractions(mockClientReader);
        verifyNoMoreInteractions(mockRegistry);
    }

    @Test
    void shouldValidateAndExecuteSingleCommandWithSpuriousSemicolons() throws Exception {
        verifyZeroInteractions(mockClientReader);
        verifyZeroInteractions(mockRegistry);

        when(mockRegistry.validate(anyString(), any(String[].class))).thenReturn(true);

        executor.run("; ;; help;; ");

        MatcherAssert.assertThat(
                stdout(),
                Matchers.allOf(
                        Matchers.containsString("\"help\" \"[]\""),
                        Matchers.containsString("\"exit\" \"[]\"")));

        verify(mockRegistry).validate("help", new String[0]);
        verify(mockRegistry).validate("exit", new String[0]);

        InOrder inOrder = inOrder(mockRegistry);
        inOrder.verify(mockRegistry).execute("help", new String[0]);
        inOrder.verify(mockRegistry).execute("exit", new String[0]);

        verifyNoMoreInteractions(mockClientReader);
        verifyNoMoreInteractions(mockRegistry);
    }

    @Test
    void shouldValidateAndExecuteMultipleCommands() throws Exception {
        verifyZeroInteractions(mockClientReader);
        verifyZeroInteractions(mockRegistry);

        when(mockRegistry.validate(anyString(), any(String[].class))).thenReturn(true);

        executor.run("help; connect foo; disconnect;");

        MatcherAssert.assertThat(
                stdout(),
                Matchers.allOf(
                        Matchers.containsString("\"help\" \"[]\""),
                        Matchers.containsString("\"connect\" \"[foo]\""),
                        Matchers.containsString("\"disconnect\" \"[]\""),
                        Matchers.containsString("\"exit\" \"[]\"")));

        verify(mockRegistry).validate("help", new String[0]);
        verify(mockRegistry).validate("connect", new String[] {"foo"});
        verify(mockRegistry).validate("disconnect", new String[0]);
        verify(mockRegistry).validate("exit", new String[0]);

        InOrder inOrder = inOrder(mockRegistry);
        inOrder.verify(mockRegistry).execute("help", new String[0]);
        inOrder.verify(mockRegistry).execute("connect", new String[] {"foo"});
        inOrder.verify(mockRegistry).execute("disconnect", new String[0]);
        inOrder.verify(mockRegistry).execute("exit", new String[0]);

        verifyNoMoreInteractions(mockClientReader);
        verifyNoMoreInteractions(mockRegistry);
    }

    @Test
    void shouldValidateAndExecuteMultipleCommandsWithScriptStyle() throws Exception {
        verifyZeroInteractions(mockClientReader);
        verifyZeroInteractions(mockRegistry);

        when(mockRegistry.validate(anyString(), any(String[].class))).thenReturn(true);

        executor.run("help;\n# Connect to foo-host;\nconnect foo;\ndisconnect;");

        MatcherAssert.assertThat(
                stdout(),
                Matchers.allOf(
                        Matchers.containsString("\"help\" \"[]\""),
                        Matchers.containsString("\"connect\" \"[foo]\""),
                        Matchers.containsString("\"disconnect\" \"[]\""),
                        Matchers.containsString("\"exit\" \"[]\"")));

        verify(mockRegistry).validate("help", new String[0]);
        verify(mockRegistry).validate("connect", new String[] {"foo"});
        verify(mockRegistry).validate("disconnect", new String[0]);
        verify(mockRegistry).validate("exit", new String[0]);

        InOrder inOrder = inOrder(mockRegistry);
        inOrder.verify(mockRegistry).execute("help", new String[0]);
        inOrder.verify(mockRegistry).execute("connect", new String[] {"foo"});
        inOrder.verify(mockRegistry).execute("disconnect", new String[0]);
        inOrder.verify(mockRegistry).execute("exit", new String[0]);

        verifyNoMoreInteractions(mockClientReader);
        verifyNoMoreInteractions(mockRegistry);
    }

    @Test
    void shouldAbortWhenAnySuppliedCommandIsInvalid() throws Exception {
        verifyZeroInteractions(mockClientReader);
        verifyZeroInteractions(mockRegistry);

        when(mockRegistry.validate(anyString(), any(String[].class)))
                .thenAnswer(
                        new Answer<Boolean>() {
                            @Override
                            public Boolean answer(InvocationOnMock invocation) {
                                String cmd = (String) invocation.getArguments()[0];
                                return !cmd.equals("connect");
                            }
                        });

        executor.run("help; connect foo; disconnect;");

        MatcherAssert.assertThat(
                stdout(), Matchers.containsString("\"[foo]\" are invalid arguments to connect"));

        verify(mockRegistry).validate("help", new String[0]);
        verify(mockRegistry).validate("connect", new String[] {"foo"});
        verify(mockRegistry).validate("disconnect", new String[0]);
        verify(mockRegistry).validate("exit", new String[0]);

        verifyNoMoreInteractions(mockClientReader);
        // Implicit verification that no registry.execute() calls were made
        verifyNoMoreInteractions(mockRegistry);
    }

    @Test
    void shouldContinueIfCommandThrows() throws Exception {
        verifyZeroInteractions(mockClientReader);
        verifyZeroInteractions(mockRegistry);

        when(mockRegistry.validate(anyString(), any(String[].class))).thenReturn(true);
        doAnswer(
                        new Answer<Void>() {
                            @Override
                            public Void answer(InvocationOnMock invocation) throws Exception {
                                String cmd = (String) invocation.getArguments()[0];
                                if (cmd.equals("connect")) {
                                    throw new NullPointerException("SomeException");
                                }
                                return null;
                            }
                        })
                .when(mockRegistry)
                .execute(anyString(), any(String[].class));

        executor.run("help; connect foo; disconnect;");

        MatcherAssert.assertThat(
                stdout(),
                Matchers.containsString(
                        "connect foo operation failed due to SomeException\njava.lang.NullPointerException: SomeException"));

        verify(mockRegistry).validate("help", new String[0]);
        verify(mockRegistry).validate("connect", new String[] {"foo"});
        verify(mockRegistry).validate("disconnect", new String[0]);
        verify(mockRegistry).validate("exit", new String[0]);

        InOrder inOrder = inOrder(mockRegistry);
        inOrder.verify(mockRegistry).execute("help", new String[0]);
        inOrder.verify(mockRegistry).execute("connect", new String[] {"foo"});
        inOrder.verify(mockRegistry).execute("disconnect", new String[0]);
        inOrder.verify(mockRegistry).execute("exit", new String[0]);

        verifyNoMoreInteractions(mockClientReader);
        verifyNoMoreInteractions(mockRegistry);
    }
}
