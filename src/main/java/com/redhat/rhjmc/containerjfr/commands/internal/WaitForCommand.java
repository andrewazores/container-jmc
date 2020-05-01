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
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import com.redhat.rhjmc.containerjfr.core.sys.Clock;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;

@Singleton
class WaitForCommand extends AbstractConnectedCommand {

    protected final ClientWriter cw;
    protected final Clock clock;

    @Inject
    WaitForCommand(ClientWriter cw, Clock clock) {
        this.cw = cw;
        this.clock = clock;
    }

    @Override
    public String getName() {
        return "wait-for";
    }

    /**
     * One arg expected. Given a recording name, this will slowly spinlock on recording completion.
     */
    @Override
    public void execute(String[] args) throws Exception {
        String name = args[0];
        Optional<IRecordingDescriptor> d = getDescriptorByName(name);
        if (!d.isPresent()) {
            cw.println(String.format("Recording with name \"%s\" not found in target JVM", name));
            return;
        }
        IRecordingDescriptor descriptor = d.get();

        if (descriptor.isContinuous()
                && !descriptor.getState().equals(IRecordingDescriptor.RecordingState.STOPPED)) {
            cw.println(String.format("Recording \"%s\" is continuous, refusing to wait", name));
            return;
        }

        long recordingStart = descriptor.getDataStartTime().longValue();
        long recordingEnd = descriptor.getDataEndTime().longValue();
        long recordingLength = recordingEnd - recordingStart;
        int lastDots = 0;
        boolean progressFlag = false;
        while (!descriptor.getState().equals(IRecordingDescriptor.RecordingState.STOPPED)) {
            long recordingElapsed =
                    getConnection().getApproximateServerTime(clock) - recordingStart;
            double elapsedProportion = ((double) recordingElapsed) / ((double) recordingLength);
            int currentDots = (int) Math.ceil(10 * elapsedProportion);
            if (currentDots > lastDots) {
                for (int i = 0; i < 2 * currentDots; i++) {
                    cw.print('\b');
                }
                cw.print(". ".repeat(currentDots).trim());
                lastDots = currentDots;
            } else {
                progressFlag = !progressFlag;
                if (progressFlag) {
                    cw.print('\b');
                } else {
                    cw.print('.');
                }
            }
            clock.sleep(TimeUnit.SECONDS, 1);
            descriptor = getDescriptorByName(name).get();
        }
        cw.println();
    }

    @Override
    public boolean validate(String[] args) {
        if (args.length != 1) {
            cw.println("Expected one argument");
            return false;
        }

        if (!validateRecordingName(args[0])) {
            cw.println(String.format("%s is an invalid recording name", args[0]));
            return false;
        }

        return true;
    }
}
