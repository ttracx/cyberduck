package ch.cyberduck.ui.cocoa.controller;

/*
 * Copyright (c) 2002-2016 iterate GmbH. All rights reserved.
 * https://cyberduck.io/
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

import ch.cyberduck.binding.application.NSTableColumn;

import java.util.HashMap;

public class TableColumnFactory extends HashMap<String, NSTableColumn> {
    private static final long serialVersionUID = -1455753054446012489L;

    public NSTableColumn create(final String identifier) {
        if(!this.containsKey(identifier)) {
            this.put(identifier, NSTableColumn.tableColumnWithIdentifier(identifier));
        }
        return this.get(identifier);
    }
}
