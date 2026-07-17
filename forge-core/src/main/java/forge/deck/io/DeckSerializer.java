package forge.deck.io;

import forge.deck.CardPool;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.deck.construction.DeckConstructionLedger;
import forge.util.FileSection;
import forge.util.FileSectionManual;
import forge.util.FileUtil;
import forge.util.TextUtil;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class DeckSerializer {

    public static void writeDeck(final Deck d, final File f) {
        writeDeck(d, f, DeckSerializer::writeLines);
    }

    static void writeDeck(final Deck d, final File f, final DeckFileWriter writer) {
        final List<String> serialized = serializeDeck(d);
        final Path requestedTarget = f.getAbsoluteFile().toPath();
        Path temporary = null;
        Path backup = null;
        try {
            final Path target = Files.isSymbolicLink(requestedTarget)
                    ? requestedTarget.toRealPath() : requestedTarget;
            temporary = Files.createTempFile(target.getParent(), ".forge-deck-", ".tmp");
            writer.write(temporary, serialized);
            copyReplacementAttributes(target, temporary);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException | FileAlreadyExistsException ex) {
                if (Files.exists(target)) {
                    backup = Files.createTempFile(target.getParent(), ".forge-deck-backup-", ".tmp");
                    Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.COPY_ATTRIBUTES);
                    copyReplacementAttributes(target, backup);
                }
                try {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException replacementFailure) {
                    if (backup != null) {
                        try {
                            Files.move(backup, target, StandardCopyOption.REPLACE_EXISTING);
                            backup = null;
                        } catch (IOException restoreFailure) {
                            replacementFailure.addSuppressed(restoreFailure);
                            // Preserve the only recoverable copy for manual recovery.
                            backup = null;
                        }
                    }
                    throw replacementFailure;
                }
            }
            temporary = null;
        } catch (IOException ex) {
            throw new RuntimeException("Unable to write and replace deck file: " + f, ex);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // Preserve the original serialization/write failure. The target was not replaced.
                }
            }
            if (backup != null) {
                try {
                    Files.deleteIfExists(backup);
                } catch (IOException ignored) {
                    // A successful replacement is authoritative; a stale backup is safer than deleting broadly.
                }
            }
        }
    }

    private static void copyReplacementAttributes(final Path source, final Path replacement) throws IOException {
        if (!Files.exists(source)) {
            return;
        }
        final PosixFileAttributeView sourcePosix = Files.getFileAttributeView(source,
                PosixFileAttributeView.class);
        final PosixFileAttributeView replacementPosix = Files.getFileAttributeView(replacement,
                PosixFileAttributeView.class);
        if (sourcePosix != null && replacementPosix != null) {
            final PosixFileAttributes attributes = sourcePosix.readAttributes();
            replacementPosix.setOwner(attributes.owner());
            replacementPosix.setGroup(attributes.group());
            replacementPosix.setPermissions(attributes.permissions());
        }
        final AclFileAttributeView sourceAcl = Files.getFileAttributeView(source, AclFileAttributeView.class);
        final AclFileAttributeView replacementAcl = Files.getFileAttributeView(replacement, AclFileAttributeView.class);
        if (sourceAcl != null && replacementAcl != null) {
            replacementAcl.setAcl(sourceAcl.getAcl());
            replacementAcl.setOwner(sourceAcl.getOwner());
        }
        final DosFileAttributeView sourceDos = Files.getFileAttributeView(source, DosFileAttributeView.class);
        final DosFileAttributeView replacementDos = Files.getFileAttributeView(replacement, DosFileAttributeView.class);
        if (sourceDos != null && replacementDos != null) {
            final DosFileAttributes attributes = sourceDos.readAttributes();
            replacementDos.setArchive(attributes.isArchive());
            replacementDos.setHidden(attributes.isHidden());
            replacementDos.setSystem(attributes.isSystem());
            replacementDos.setReadOnly(attributes.isReadOnly());
        }
    }

    private static void writeLines(final Path path, final List<String> lines) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            for (String line : lines) {
                writer.write(line);
                writer.newLine();
            }
            writer.flush();
        }
    }

    @FunctionalInterface
    interface DeckFileWriter {
        void write(Path path, List<String> lines) throws IOException;
    }

    static DeckFileHeader readDeckMetadata(final Map<String, List<String>> map) {
        if (map == null) {
            return null;
        }
        final List<String> metadata = map.get("metadata");
        if (metadata != null) {
            return new DeckFileHeader(FileSection.parse(metadata, FileSection.EQUALS_KV_SEPARATOR));
        }
        final List<String> general = map.get("general");
        if (general != null) {
            final FileSectionManual fs = new FileSectionManual();
            fs.put(DeckFileHeader.NAME, StringUtils.join(map.get(""), " "));
            fs.put(DeckFileHeader.DECK_TYPE, StringUtils.join(general, " "));
            return new DeckFileHeader(fs);
        }

        return null;
    }

    private static List<String> serializeDeck(Deck d) {
        final List<String> out = new ArrayList<>();
        out.add(TextUtil.enclosedBracket("metadata"));
    
        out.add(TextUtil.concatNoSpace(DeckFileHeader.NAME,"=", d.getName().replaceAll("\n", "")));
        if (d.getDeckFormat() != null) {
            out.add(TextUtil.concatNoSpace(DeckFileHeader.DECK_TYPE, "=", d.getDeckFormat().name()));
        }
        if (d.getSourceUrl() != null) {
            out.add(TextUtil.concatNoSpace(DeckFileHeader.SOURCE_URL, "=", d.getSourceUrl().replaceAll("\n", "")));
        }
        // these are optional
        if (d.getComment() != null) {
            out.add(TextUtil.concatNoSpace(DeckFileHeader.COMMENT,"=", d.getComment().replaceAll("\n", "")));
        }
        final DeckConstructionLedger constructionLedger = d.getConstructionLedger();
        final List<String> serializedTags = new ArrayList<>();
        for (String tag : d.getTags()) {
            if (!isConstructionMarker(tag)) {
                serializedTags.add(tag);
            }
        }
        serializedTags.add(DeckConstructionLedger.MARKER_PREFIX + constructionLedger.getLedgerId());
        out.add(TextUtil.concatNoSpace(DeckFileHeader.TAGS,"=",
                StringUtils.join(serializedTags, DeckFileHeader.TAGS_SEPARATOR)));
        if (!d.getAiHints().isEmpty()) {
            out.add(TextUtil.concatNoSpace(DeckFileHeader.AI_HINTS, "=", StringUtils.join(d.getAiHints(), " | ")));
        }
        if (!d.getDraftNotes().isEmpty()) {
            String sb = serializeDraftNotes(d.getDraftNotes());
            out.add(TextUtil.concatNoSpace(DeckFileHeader.DRAFT_NOTES, "=", sb));
        }
        if (!d.getKeyCards().isEmpty()) {
            out.add(TextUtil.concatNoSpace(DeckFileHeader.KEY_CARDS, "=", StringUtils.join(d.getKeyCards(), ";")));
        }

        out.add(TextUtil.enclosedBracket("construction"));
        out.addAll(constructionLedger.toLines());

        for (Entry<DeckSection, CardPool> s : d) {
            if (s.getValue().isEmpty())
                continue;
            out.add(TextUtil.enclosedBracket(s.getKey().toString()));
            out.add(s.getValue().toCardList(System.lineSeparator()));
        }
        return out;
    }

    public static String serializeDraftNotes(final Map<String, String> draftNotes) {
        StringBuilder sb = new StringBuilder();
        for (String key : draftNotes.keySet()) {
            if (sb.length() > 0) {
                sb.append(" | ");
            }

            sb.append(key).append(":").append(draftNotes.get(key));
        }
        return sb.toString();
    }

    public static Deck fromFile(final File deckFile) {
        return fromSections(FileSection.parseSections(FileUtil.readFile(deckFile)));
    }

    public static Deck fromSections(final Map<String, List<String>> sections) {
        if (sections == null || sections.isEmpty()) {
            return null;
        }
    
        final DeckFileHeader dh = readDeckMetadata(sections);
        if (dh == null) {
            return null;
        }

        Deck d = new Deck(dh.getName());
        d.setComment(dh.getComment());
        d.setDeckFormat(dh.getDeckType());
        d.setSourceUrl(dh.getSourceUrl());
        d.setAiHints(dh.getAiHints());
        d.getTags().addAll(dh.getTags());
        d.setDraftNotes(dh.getDraftNotes());
        for (String keyCard : dh.getKeyCards()) {
            d.addKeyCard(keyCard);
        }
        d.setDeferredSections(sections);
        return d;
    }

    private static boolean isConstructionMarker(final String tag) {
        return tag != null && tag.regionMatches(true, 0, DeckConstructionLedger.MARKER_PREFIX, 0,
                DeckConstructionLedger.MARKER_PREFIX.length());
    }
}
