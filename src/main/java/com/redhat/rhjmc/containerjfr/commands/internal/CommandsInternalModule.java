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

import java.util.Set;

import javax.annotation.Nullable;
import javax.inject.Singleton;

import org.openjdk.jmc.flightrecorder.configuration.recording.RecordingOptionsBuilder;

import com.redhat.rhjmc.containerjfr.ExecutionMode;
import com.redhat.rhjmc.containerjfr.commands.Command;
import com.redhat.rhjmc.containerjfr.commands.CommandRegistry;
import com.redhat.rhjmc.containerjfr.commands.SerializableCommandRegistry;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;

@Module
public abstract class CommandsInternalModule {
    @Binds
    @IntoSet
    abstract Command bindConnectCommand(ConnectCommand command);

    @Binds
    @IntoSet
    abstract Command bindDeleteCommand(DeleteCommand command);

    @Binds
    @IntoSet
    abstract Command bindDeleteSavedRecordingCommand(DeleteSavedRecordingCommand command);

    @Binds
    @IntoSet
    abstract Command bindDisconnectCommand(DisconnectCommand command);

    @Binds
    @IntoSet
    abstract Command bindDumpCommand(DumpCommand command);

    @Binds
    @IntoSet
    abstract Command bindExitCommand(ExitCommand command);

    @Binds
    @IntoSet
    abstract Command bindHelpCommand(HelpCommand command);

    @Binds
    @IntoSet
    abstract Command bindHostnameCommand(HostnameCommand command);

    @Binds
    @IntoSet
    abstract Command bindIpCommand(IpCommand command);

    @Binds
    @IntoSet
    abstract Command bindIsConnectedCommand(IsConnectedCommand command);

    @Binds
    @IntoSet
    abstract Command bindListCommand(ListCommand command);

    @Binds
    @IntoSet
    abstract Command bindListEventTypesCommand(ListEventTypesCommand command);

    @Binds
    @IntoSet
    abstract Command bindListRecordingOptionsCommand(ListRecordingOptionsCommand command);

    @Binds
    @IntoSet
    abstract Command bindListEventTemplatesCommand(ListEventTemplatesCommand command);

    @Binds
    @IntoSet
    abstract Command bindListSavedRecordingsCommand(ListSavedRecordingsCommand command);

    @Binds
    @IntoSet
    abstract Command bindPingCommand(PingCommand command);

    @Binds
    @IntoSet
    abstract Command bindPortScanCommand(ScanTargetsCommand command);

    @Binds
    @IntoSet
    abstract Command bindPrintUrlCommand(PrintUrlCommand command);

    @Binds
    @IntoSet
    abstract Command bindRecordingOptionsCustomizerCommand(
            RecordingOptionsCustomizerCommand command);

    @Binds
    @IntoSet
    abstract Command bindSaveRecordingCommand(SaveRecordingCommand command);

    @Binds
    @IntoSet
    abstract Command bindSearchEventsCommand(SearchEventsCommand command);

    @Binds
    @IntoSet
    abstract Command bindSnapshotCommand(SnapshotCommand command);

    @Binds
    @IntoSet
    abstract Command bindStartRecordingCommand(StartRecordingCommand command);

    @Binds
    @IntoSet
    abstract Command bindStopRecordingCommand(StopRecordingCommand command);

    @Binds
    @IntoSet
    abstract Command bindUploadRecordingCommand(UploadRecordingCommand command);

    @Binds
    @IntoSet
    abstract Command bindWaitCommand(WaitCommand command);

    @Binds
    @IntoSet
    abstract Command bindWaitForCommand(WaitForCommand command);

    @Binds
    @IntoSet
    abstract Command bindWaitForDownloadCommand(WaitForDownloadCommand command);

    @Provides
    static EventOptionsBuilder.Factory provideEventOptionsBuilderFactory(ClientWriter cw) {
        return new EventOptionsBuilder.Factory(cw);
    }

    @Provides
    static RecordingOptionsBuilderFactory provideRecordingOptionsBuilderFactory(
            RecordingOptionsCustomizer customizer) {
        return service -> customizer.apply(new RecordingOptionsBuilder(service));
    }

    @Provides
    @Singleton
    static RecordingOptionsCustomizer provideRecordingOptionsCustomizer(ClientWriter cw) {
        return new RecordingOptionsCustomizer(cw);
    }

    @Provides
    @Nullable
    @Singleton
    static CommandRegistry provideCommandRegistry(
            ExecutionMode mode, ClientWriter cw, Set<Command> commands) {
        if (mode.equals(ExecutionMode.WEBSOCKET)) {
            return null;
        } else {
            return new CommandRegistryImpl(cw, commands);
        }
    }

    @Provides
    @Nullable
    @Singleton
    static SerializableCommandRegistry provideSerializableCommandRegistry(
            ExecutionMode mode, Set<Command> commands) {
        if (mode.equals(ExecutionMode.WEBSOCKET)) {
            return new SerializableCommandRegistryImpl(commands);
        } else {
            return null;
        }
    }
}
