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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.openjdk.jmc.rjmx.services.jfr.IEventTypeInfo;

import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import com.redhat.rhjmc.containerjfr.jmc.serialization.SerializableEventTypeInfo;

@Singleton
class SearchEventsCommand extends AbstractConnectedCommand implements SerializableCommand {

    private final ClientWriter cw;

    @Inject
    SearchEventsCommand(ClientWriter cw) {
        this.cw = cw;
    }

    @Override
    public String getName() {
        return "search-events";
    }

    @Override
    public boolean validate(String[] args) {
        // TODO better validation of search term string
        if (args.length != 1) {
            cw.println("Expected one argument: search term string");
            return false;
        }
        return true;
    }

    @Override
    public void execute(String[] args) throws Exception {
        Collection<? extends IEventTypeInfo> matchingEvents =
                getService().getAvailableEventTypes().stream()
                        .filter(event -> eventMatchesSearchTerm(event, args[0].toLowerCase()))
                        .collect(Collectors.toList());

        if (matchingEvents.isEmpty()) {
            cw.println("\tNo matches");
        }
        matchingEvents.forEach(this::printEvent);
    }

    @Override
    public Output<?> serializableExecute(String[] args) {
        try {
            Collection<? extends IEventTypeInfo> matchingEvents =
                    getService().getAvailableEventTypes().stream()
                            .filter(event -> eventMatchesSearchTerm(event, args[0].toLowerCase()))
                            .collect(Collectors.toList());
            List<SerializableEventTypeInfo> events = new ArrayList<>(matchingEvents.size());
            for (IEventTypeInfo info : matchingEvents) {
                events.add(new SerializableEventTypeInfo(info));
            }
            return new ListOutput<>(events);
        } catch (Exception e) {
            return new ExceptionOutput(e);
        }
    }

    private void printEvent(IEventTypeInfo event) {
        cw.println(
                String.format(
                        "\t%s\toptions: %s",
                        event.getEventTypeID().getFullKey(),
                        event.getOptionDescriptors().keySet().toString()));
    }

    private boolean eventMatchesSearchTerm(IEventTypeInfo event, String term) {
        Set<String> terms = new HashSet<>();
        terms.add(event.getEventTypeID().getFullKey());
        terms.addAll(Arrays.asList(event.getHierarchicalCategory()));
        terms.add(event.getDescription());
        terms.add(event.getName());

        return terms.stream()
                .filter(s -> s != null)
                .map(String::toLowerCase)
                .anyMatch(s -> s.contains(term));
    }
}
