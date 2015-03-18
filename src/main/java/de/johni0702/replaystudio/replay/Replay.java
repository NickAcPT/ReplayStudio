package de.johni0702.replaystudio.replay;

import de.johni0702.replaystudio.collection.ReplayPart;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

/**
 * A replay which consist out of multiple packets with their timestamps and some meta data.
 */
public interface Replay extends ReplayPart {

    /**
     * Returns the meta data for this replay.
     * @return The meta data
     */
    ReplayMetaData getMetaData();

    /**
     * Sets the meta data of this replay.
     * @param metaData The new meta data
     */
    void setMetaData(ReplayMetaData metaData);

    /**
     * Saves this replay to the specified file.
     * @param file The file
     */
    void save(File file) throws IOException;

    /**
     * Saves this replay to the specified output stream.
     * @param output The output stream
     * @param raw If {@code true} only saves the packet recording itself, otherwise also saves metadata
     */
    void save(OutputStream output, boolean raw) throws IOException;

}