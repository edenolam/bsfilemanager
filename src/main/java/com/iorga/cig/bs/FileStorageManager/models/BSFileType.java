package com.iorga.cig.bs.FileStorageManager.models;

import java.nio.file.attribute.PosixFilePermission;
import java.util.*;

/**
 * Types de fichier gérés par BSFM
 * Chaque type de fichier est rattaché à un groupe *nix
 */
public class BSFileType {

    private String key;
    private String nixGroup;
    private Set<PosixFilePermission> nixPerms;

    private BSFileType(String key, String nixGroup, Set<PosixFilePermission> nixPerms) {
        this.key = key;
        this.nixGroup = nixGroup;
        this.nixPerms = nixPerms;
    }

    public String getKey() {
        return key;
    }
    public String getNixGroup() {
        return nixGroup;
    }

    public Set<PosixFilePermission> getNixPerms() {
        return nixPerms;
    }

    public static final BSFileType FILES;
    public static final BSFileType HEARDERS;
    public static final BSFileType SPECIALS;
    public static final BSFileType SPECIALS_TEMP;

    static {
        Set<PosixFilePermission> perms = new HashSet<>();
        perms.add(PosixFilePermission.OWNER_READ);
        perms.add(PosixFilePermission.OWNER_WRITE);
        perms.add(PosixFilePermission.GROUP_READ);
        FILES = new BSFileType("files", "nas-files", Collections.unmodifiableSet(perms));

        perms = new HashSet<>();
        perms.add(PosixFilePermission.OWNER_READ);
        perms.add(PosixFilePermission.OWNER_WRITE);
        perms.add(PosixFilePermission.GROUP_READ);
        SPECIALS = new BSFileType("specials", "nas-specials", Collections.unmodifiableSet(perms));

        perms = new HashSet<>();
        perms.add(PosixFilePermission.OWNER_READ);
        perms.add(PosixFilePermission.OWNER_WRITE);
        HEARDERS = new BSFileType("headers", "bsfm", Collections.unmodifiableSet(perms));

        perms = new HashSet<>();
        perms.add(PosixFilePermission.OWNER_READ);
        perms.add(PosixFilePermission.OWNER_WRITE);
        perms.add(PosixFilePermission.GROUP_READ);
        perms.add(PosixFilePermission.GROUP_WRITE);
        SPECIALS_TEMP = new BSFileType("specials_temp", "nas-specials", Collections.unmodifiableSet(perms));
    }
}
