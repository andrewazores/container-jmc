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

import java.util.Optional;
import java.util.StringJoiner;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import com.redhat.rhjmc.containerjfr.net.ConnectionDescriptor;
import com.redhat.rhjmc.containerjfr.net.TargetConnectionManager;

/** @deprecated use HTTP PATCH "STOP" /api/v1/targets/:targetId/recordings/:recordingName */
@Deprecated
@Singleton
class StopRecordingCommand extends AbstractConnectedCommand implements SerializableCommand {

    private final ClientWriter cw;

    @Inject
    StopRecordingCommand(ClientWriter cw, TargetConnectionManager targetConnectionManager) {
        super(targetConnectionManager);
        this.cw = cw;
    }

    @Override
    public String getName() {
        return "stop";
    }

    @Override
    public void execute(String[] args) throws Exception {
        String targetId = args[0];
        String name = args[1];

        targetConnectionManager.executeConnectedTask(
                new ConnectionDescriptor(targetId),
                connection -> {
                    Optional<IRecordingDescriptor> descriptor = getDescriptorByName(targetId, name);
                    if (descriptor.isPresent()) {
                        connection.getService().stop(descriptor.get());
                    } else {
                        cw.println(String.format("Recording with name \"%s\" not found", name));
                    }
                    return null;
                });
    }

    @Override
    public Output<?> serializableExecute(String[] args) {
        try {
            String targetId = args[0];
            String name = args[1];

            return targetConnectionManager.executeConnectedTask(
                    new ConnectionDescriptor(targetId),
                    connection -> {
                        Optional<IRecordingDescriptor> descriptor =
                                getDescriptorByName(targetId, name);
                        if (descriptor.isPresent()) {
                            connection.getService().stop(descriptor.get());
                            return new SuccessOutput();
                        } else {
                            return new FailureOutput(
                                    String.format("Recording with name \"%s\" not found", name));
                        }
                    });
        } catch (Exception e) {
            return new ExceptionOutput(e);
        }
    }

    @Override
    public void validate(String[] args) throws FailedValidationException {
        if (args.length != 2) {
            String errorMessage =
                    "Expected two arguments: target (host:port, ip:port, or JMX service URL) and recording name";
            cw.println(errorMessage);
            throw new FailedValidationException(errorMessage);
        }

        String targetId = args[0];
        String name = args[1];
        StringJoiner combinedErrorMessage = new StringJoiner("; ");

        if (!validateTargetId(targetId)) {
            String errorMessage = String.format("%s is an invalid connection specifier", args[0]);
            cw.println(errorMessage);
            combinedErrorMessage.add(errorMessage);
        }

        if (!validateRecordingName(name)) {
            String errorMessage = String.format("%s is an invalid recording name", name);
            cw.println(errorMessage);
            combinedErrorMessage.add(errorMessage);
        }

        if (combinedErrorMessage.length() > 0) {
            throw new FailedValidationException(combinedErrorMessage.toString());
        }
    }
}
