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
package com.redhat.rhjmc.containerjfr.tui.tcp;

import java.io.IOException;

import javax.inject.Singleton;

import com.redhat.rhjmc.containerjfr.ExecutionMode;
import com.redhat.rhjmc.containerjfr.commands.CommandRegistry;
import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.tui.ClientReader;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import com.redhat.rhjmc.containerjfr.net.ConnectionListener;
import com.redhat.rhjmc.containerjfr.net.NetworkConfiguration;
import com.redhat.rhjmc.containerjfr.tui.CommandExecutor;
import com.redhat.rhjmc.containerjfr.tui.ConnectionMode;

import dagger.Binds;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;

@Module
public abstract class TcpModule {
    @Provides
    @Singleton
    static SocketInteractiveShellExecutor provideSocketInteractiveShellExecutor(
            ClientReader cr, ClientWriter cw, Lazy<CommandRegistry> commandRegistry) {
        return new SocketInteractiveShellExecutor(cr, cw, commandRegistry);
    }

    @Provides
    @ConnectionMode(ExecutionMode.SOCKET)
    static CommandExecutor provideCommandExecutor(SocketInteractiveShellExecutor executor) {
        return executor;
    }

    @Binds
    @IntoSet
    abstract ConnectionListener bindConnectionListener(
            SocketInteractiveShellExecutor commandExecutor);

    @Provides
    @Singleton
    @ConnectionMode(ExecutionMode.SOCKET)
    static ClientReader provideClientReader(SocketClientReaderWriter socketReaderWriter) {
        return socketReaderWriter;
    }

    @Provides
    @Singleton
    @ConnectionMode(ExecutionMode.SOCKET)
    static ClientWriter provideClientWriter(SocketClientReaderWriter socketReaderWriter) {
        return socketReaderWriter;
    }

    @Provides
    @Singleton
    static SocketClientReaderWriter provideSocketClientReaderWriter(
            Logger logger, NetworkConfiguration netConf) {
        try {
            return new SocketClientReaderWriter(logger, netConf.getInternalCommandChannelPort());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
