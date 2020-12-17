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

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.redhat.rhjmc.containerjfr.commands.Command;
import com.redhat.rhjmc.containerjfr.commands.Command.ExceptionOutput;
import com.redhat.rhjmc.containerjfr.commands.Command.FailureOutput;
import com.redhat.rhjmc.containerjfr.commands.Command.Output;
import com.redhat.rhjmc.containerjfr.commands.CommandRegistry;
import com.redhat.rhjmc.containerjfr.core.log.Logger;

class CommandRegistryImpl implements CommandRegistry {

    private final Map<String, Command> commandMap = new TreeMap<>();
    private final Logger logger;

    CommandRegistryImpl(Set<Command> commands, Logger logger) {
        for (Command command : commands) {
            String commandName = command.getName();
            if (commandMap.containsKey(commandName)) {
                throw new CommandDefinitionException(
                        commandName, command.getClass(), commandMap.get(commandName).getClass());
            }
            commandMap.put(commandName, command);
        }
        this.logger = logger;
    }

    @Override
    public Set<String> getRegisteredCommandNames() {
        return this.commandMap.keySet();
    }

    @Override
    public Set<String> getAvailableCommandNames() {
        return this.commandMap.values().stream()
                .filter(Command::isAvailable)
                .map(Command::getName)
                .collect(Collectors.toSet());
    }

    @Override
    public Output<?> execute(String commandName, String[] args) {
        if (!isCommandRegistered(commandName)) {
            return new FailureOutput(String.format("Command \"%s\" not recognized", commandName));
        }
        if (!isCommandAvailable(commandName)) {
            return new FailureOutput(String.format("Command \"%s\" unavailable", commandName));
        }
        try {
            Command c = commandMap.get(commandName);
            boolean deprecated =
                    Arrays.asList(c.getClass().getDeclaredAnnotations()).stream()
                            .anyMatch(
                                    annotation ->
                                            Deprecated.class
                                                    .getName()
                                                    .equals(annotation.annotationType().getName()));
            if (deprecated) {
                logger.warn("Command \"{}\" is deprecated", commandName);
            }
            return c.execute(args);
        } catch (Exception e) {
            return new ExceptionOutput(e);
        }
    }

    @Override
    public void validate(String commandName, String[] args) throws FailedValidationException {
        if (!isCommandRegistered(commandName)) {
            throw new FailedValidationException(
                    String.format("Command \"%s\" not recognized", commandName));
        }
        commandMap.get(commandName).validate(args);
    }

    private boolean isCommandRegistered(String commandName) {
        if (StringUtils.isBlank(commandName)) {
            return false;
        }
        return getRegisteredCommandNames().contains(commandName);
    }

    @Override
    public boolean isCommandAvailable(String commandName) {
        if (StringUtils.isBlank(commandName)) {
            return false;
        }
        return getAvailableCommandNames().contains(commandName);
    }

    @SuppressWarnings("serial")
    public static class CommandDefinitionException extends RuntimeException {
        public CommandDefinitionException(
                String commandName, Class<? extends Command> cmd1, Class<? extends Command> cmd2) {
            super(
                    String.format(
                            "\"%s\" command definitions provided by class %s AND class %s",
                            commandName, cmd1.getCanonicalName(), cmd2.getCanonicalName()));
        }
    }
}
