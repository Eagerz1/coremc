package net.coremc.coreban.storage;

import net.coremc.coreban.model.Note;

import java.util.List;
import java.util.UUID;

/**
 * Persistence for staff-only player notes.
 *
 * <p>Reuses the same physical database (the SQLite file or MySQL instance) as
 * the punishment storage so notes survive restarts, reloads and player
 * reconnects without a second database.</p>
 */
public interface NoteStorage {

    void init();

    /** Add a note (assigns a unique id). */
    Note insert(Note n);

    /** All notes for a player, newest first. */
    List<Note> notes(UUID target);

    /** A single note by id for a player, or null if not present. */
    Note get(UUID target, int noteId);

    /** Remove a note. Returns true if a row was deleted. */
    boolean remove(UUID target, int noteId);

    void close();
}
