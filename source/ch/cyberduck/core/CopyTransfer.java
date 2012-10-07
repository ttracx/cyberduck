package ch.cyberduck.core;

/*
 * Copyright (c) 2002-2011 David Kocher. All rights reserved.
 *
 * http://cyberduck.ch/
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
 *
 * Bug fixes, suggestions and comments should be sent to:
 * dkocher@cyberduck.ch
 */

import ch.cyberduck.core.i18n.Locale;
import ch.cyberduck.core.io.BandwidthThrottle;
import ch.cyberduck.core.serializer.Serializer;

import org.apache.log4j.Logger;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @version $Id$
 */
public class CopyTransfer extends Transfer {
    private static Logger log = Logger.getLogger(CopyTransfer.class);

    /**
     * Mapping source to destination files
     */
    private Map<Path, Path> files = Collections.emptyMap();

    private Session destination;

    /**
     * @param files Source to destination mapping
     */
    public CopyTransfer(Map<Path, Path> files) {
        super(new ArrayList<Path>(files.keySet()));
        this.files = files;
        this.destination = files.values().iterator().next().getSession();
    }

    public <T> CopyTransfer(T dict, Session s) {
        super(dict, s);
    }

    @Override
    public boolean isResumable() {
        return false;
    }

    @Override
    public boolean isReloadable() {
        return false;
    }

    @Override
    protected void init() {
        this.bandwidth = new BandwidthThrottle(
                Preferences.instance().getFloat("queue.download.bandwidth.bytes"));
    }

    @Override
    public <T> T getAsDictionary() {
        final Serializer dict = super.getSerializer();
        dict.setStringForKey(String.valueOf(Transfer.KIND_COPY), "Kind");
        return dict.<T>getSerialized();
    }

    @Override
    public List<Session> getSessions() {
        final ArrayList<Session> sessions = new ArrayList<Session>(super.getSessions());
        if(destination != null) {
            sessions.add(destination);
        }
        return sessions;
    }

    /**
     * Prunes the map of selected files. Files which are a child of an already included directory
     * are removed from the returned map.
     */
    @Override
    protected void normalize() {
        log.debug("normalize");
        final Map<Path, Path> normalized = new HashMap<Path, Path>();
        Iterator<Path> sourcesIter = files.keySet().iterator();
        Iterator<Path> destinationsIter = files.values().iterator();
        while(sourcesIter.hasNext()) {
            Path f = sourcesIter.next();
            Path r = destinationsIter.next();
            boolean duplicate = false;
            for(Iterator<Path> normalizedIter = normalized.keySet().iterator(); normalizedIter.hasNext(); ) {
                Path n = normalizedIter.next();
                if(f.isChild(n)) {
                    // The selected file is a child of a directory
                    // already included for deletion
                    duplicate = true;
                    break;
                }
                if(n.isChild(f)) {
                    // Remove the previously added file as it is a child
                    // of the currently evaluated file
                    normalizedIter.remove();
                }
            }
            if(!duplicate) {
                normalized.put(f, r);
            }
        }
        this.files = normalized;
        this.setRoots(new ArrayList<Path>(files.keySet()));
    }

    @Override
    public TransferAction action(boolean resumeRequested, boolean reloadRequested) {
        return TransferAction.ACTION_OVERWRITE;
    }

    private final class CopyTransferFilter extends TransferFilter {
        @Override
        public boolean accept(final Path source) {
            final Path destination = files.get(source);
            if(destination.attributes().isDirectory()) {
                // Do not attempt to create a directory that already exists
                if(destination.exists()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public void prepare(Path source) {
            if(source.attributes().isFile()) {
                source.status().setResume(false);
            }
            if(source.attributes().getSize() == -1) {
                // Read file size
                source.readSize();
            }
            if(source.attributes().isFile()) {
                final long length = source.attributes().getSize();
                // Download
                source.status().setLength(length);
                size += length;
                // Upload
                files.get(source).status().setLength(length);
                size += length;
            }
            final Path dest = files.get(source);
            if(dest.attributes().isDirectory()) {
                if(!dest.exists()) {
                    destination.cache().put(dest.getReference(), AttributedList.<Path>emptyList());
                }
            }
        }

        @Override
        public void complete(Path p) {
            ;
        }
    }

    private final TransferFilter COPY_FILTER = new CopyTransferFilter();

    @Override
    public TransferFilter filter(final TransferAction action) {
        if(log.isDebugEnabled()) {
            log.debug(String.format("Filter transfer with action %s", action.toString()));
        }
        if(action.equals(TransferAction.ACTION_OVERWRITE)) {
            return COPY_FILTER;
        }
        return super.filter(action);
    }

    @Override
    public AttributedList<Path> children(Path source) {
        final AttributedList<Path> list = source.children();
        final Path target = files.get(source);
        for(Path p : list) {
            files.put(p, PathFactory.createPath(destination,
                    target.getAbsolute(), p.getName(), p.attributes().getType()));
        }
        return list;
    }

    @Override
    protected void transfer(Path source, TransferOptions options) {
        log.debug("transfer:" + source);
        final Path destination = files.get(source);
        if(source.attributes().isFile()) {
            source.copy(destination, bandwidth, new AbstractStreamListener() {
                @Override
                public void bytesReceived(long bytes) {
                    transferred += bytes;
                }

                @Override
                public void bytesSent(long bytes) {
                    transferred += bytes;
                }
            });
        }
        else {
            if(destination.getSession().isCreateFolderSupported(destination)) {
                destination.mkdir();
            }
        }
    }

    @Override
    public String getName() {
        return MessageFormat.format(Locale.localizedString("Copying {0} to {1}", "Status"),
                files.keySet().iterator().next().getName(), files.values().iterator().next().getName());
    }

    @Override
    public String getStatus() {
        return this.isComplete() ? "Copy complete" : "Transfer incomplete";
    }

    @Override
    public String getImage() {
        return "transfer-upload.tiff";
    }
}
