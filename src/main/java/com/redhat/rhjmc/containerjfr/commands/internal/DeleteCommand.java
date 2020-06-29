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

import javax.inject.Inject;
import javax.inject.Singleton;

import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import com.redhat.rhjmc.containerjfr.net.TargetConnectionManager;
import com.redhat.rhjmc.containerjfr.net.internal.reports.ReportService;

@Singleton
class DeleteCommand extends AbstractConnectedCommand implements SerializableCommand {

    private final ClientWriter cw;
    private final ReportService reportService;

    @Inject
    DeleteCommand(
            ClientWriter cw,
            TargetConnectionManager targetConnectionManager,
            ReportService reportService) {
        super(targetConnectionManager);
        this.cw = cw;
        this.reportService = reportService;
    }

    @Override
    public String getName() {
        return "delete";
    }

    @Override
    public void execute(String[] args) throws Exception {
        final String targetId = args[0];
        final String recordingName = args[1];
        targetConnectionManager.executeConnectedTask(
                targetId,
                connection -> {
                    Optional<IRecordingDescriptor> descriptor =
                            getDescriptorByName(targetId, recordingName);
                    if (descriptor.isPresent()) {
                        connection.getService().close(descriptor.get());
                        reportService.delete(targetId, recordingName);
                    } else {
                        cw.println(
                                String.format(
                                        "No recording with name \"%s\" found", recordingName));
                    }
                    return null;
                });
    }

    @Override
    public Output<?> serializableExecute(String[] args) {
        final String targetId = args[0];
        final String recordingName = args[1];
        try {
            return targetConnectionManager.executeConnectedTask(
                    targetId,
                    connection -> {
                        Optional<IRecordingDescriptor> descriptor =
                                getDescriptorByName(targetId, recordingName);
                        if (descriptor.isPresent()) {
                            connection.getService().close(descriptor.get());
                            reportService.delete(targetId, recordingName);
                            return new SuccessOutput();
                        } else {
                            return new FailureOutput(
                                    String.format(
                                            "No recording with name \"%s\" found", recordingName));
                        }
                    });
        } catch (Exception e) {
            return new ExceptionOutput(e);
        }
    }

    @Override
    public boolean validate(String[] args) {
        if (args.length != 2) {
            cw.println(
                    "Expected two arguments: target (host:port, ip:port, or JMX service URL) and recording name");
            return false;
        }
        boolean isValidTargetID = validateTargetId(args[0]);
        if (!isValidTargetID) {
            cw.println(String.format("%s is an invalid connection specifier", args[0]));
        }
        boolean isValidRecordingName = validateRecordingName(args[1]);
        if (!isValidRecordingName) {
            cw.println(String.format("%s is an invalid recording name", args[1]));
        }
        return isValidTargetID && isValidRecordingName;
    }
}
