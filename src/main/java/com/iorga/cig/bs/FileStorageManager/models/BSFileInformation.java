package com.iorga.cig.bs.FileStorageManager.models;

import com.iorga.cig.bs.FileStorageManager.services.Tools;

import javax.persistence.*;
import java.security.NoSuchAlgorithmException;
import java.sql.Date;
import java.time.LocalDate;

@Entity(name = "BSFileInformation")
public class BSFileInformation {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(nullable = false, updatable = false, unique = true, length = 50)
    private String fileKey;

    @Column(nullable = false, updatable = false)
    private String originalFileName;

    @Column(nullable = false, updatable = false)
    private String fileContentType;

    @Column(nullable = false, updatable = false)
    private Integer fileContentSize;

    @Column(nullable = false, updatable = false, length = 64)
    private String fileContentHash;

    @Column(nullable = false, updatable = false, length = 256)
    private String logicalFolder;

    @Column(nullable = false, updatable = false)
    private Integer targetYear;

    @Column(nullable = false, updatable = false, unique = true, length = 64)
    private String storageHashedFileName;

    @Column(nullable = false, updatable = false, length = 20)
    private String ownerKey;

    @Column(nullable = false, updatable = false)
    private Date storageDate;

    @Column(updatable = false, length = 30)
    private String externalRef;

    @Column(nullable = false)
    private Integer status;

    @Column(length = 10000)
    private String statusLinkedData;

    @Column(nullable = false)
    private Boolean isSpecial = false;

    protected BSFileInformation() {}

    private BSFileInformation(String fileKey) {
        this.fileKey = fileKey;
        this.storageDate = Date.valueOf(LocalDate.now());
        this.status = 0;
        this.isSpecial = false;
    }

    private static BSFileInformation createNew(BSFile bsFile, String folderName, Integer fileContentSize, String fileContentHash, boolean isSpecial)
            throws NoSuchAlgorithmException {
        // Calcul du nom de stockage du fichier qui doit être unique
        String computedFileName = String.format("%s/%s/%d/%s/%s",
                bsFile.getOwnerKey(),
                folderName,
                bsFile.getTargetYear(),
                bsFile.getExternalRef() != null ? bsFile.getExternalRef() : "-",
                bsFile.getOriginalFileName());

        byte[] fileKeyBuffer = Tools.computeStringSha256(computedFileName);

        // Mémorisation des informations concernant le fichier
        BSFileInformation info = new BSFileInformation(Tools.toBase64(fileKeyBuffer, true));
        info.setOriginalFileName(bsFile.getOriginalFileName());
        info.setFileContentType(bsFile.getFileContentType());
        info.setFileContentSize(fileContentSize);
        info.setFileContentHash(fileContentHash);
        info.setLogicalFolder(folderName);
        info.setTargetYear(bsFile.getTargetYear());
        info.setStorageHashedFileName(Tools.toHex(fileKeyBuffer));
        info.setOwnerKey(bsFile.getOwnerKey());
        info.setExternalRef(bsFile.getExternalRef());
        info.setIsSpecial(isSpecial);
        return info;
    }

    public static BSFileInformation createNew(BSFile bsFile, String folderName, Integer fileContentSize, String fileContentHash)
            throws NoSuchAlgorithmException {
        return createNew(bsFile, folderName, fileContentSize, fileContentHash, false);
    }

    public static BSFileInformation createNewSpecial(BSFile bsFile, String folderName, Integer fileContentSize, String fileContentHash)
            throws NoSuchAlgorithmException {
        return createNew(bsFile, folderName, fileContentSize, fileContentHash, true);
    }

    public Long getId() {
        return id;
    }

    protected void setId(Long id) {
        this.id = id;
    }

    public String getFileKey() { return fileKey; }

    protected void setFileKey(String fileKey) { this.fileKey = fileKey; }

    public String getOriginalFileName() {
        return originalFileName;
    }

    protected void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }

    public String getStorageHashedFileName() {
        return storageHashedFileName;
    }

    protected void setStorageHashedFileName(String storageHashedFileName) {
        this.storageHashedFileName = storageHashedFileName;
    }

    public Date getStorageDate() {
        return storageDate;
    }

    protected void setStorageDate(Date storageDate) {
        this.storageDate = storageDate;
    }

    public String getFileContentType() {
        return fileContentType;
    }

    protected void setFileContentType(String fileContentType) {
        this.fileContentType = fileContentType;
    }

    public String getLogicalFolder() {
        return logicalFolder;
    }

    protected void setLogicalFolder(String logicalFolder) {
        this.logicalFolder = logicalFolder;
    }

    public String getOwnerKey() {
        return ownerKey;
    }

    protected void setOwnerKey(String ownerKey) {
        this.ownerKey = ownerKey;
    }

    public String getFileContentHash() {
        return fileContentHash;
    }

    protected void setFileContentHash(String fileContentHash) {
        this.fileContentHash = fileContentHash;
    }

    public Integer getFileContentSize() {
        return fileContentSize;
    }

    protected void setFileContentSize(Integer fileContentSize) {
        this.fileContentSize = fileContentSize;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getTargetYear() { return targetYear; }

    protected void setTargetYear(Integer targetYear) { this.targetYear = targetYear; }

    public String getExternalRef() { return externalRef; }

    protected void setExternalRef(String externalRef) { this.externalRef = externalRef; }

    public Boolean getIsSpecial() { return isSpecial; }

    protected void setIsSpecial(Boolean special) { this.isSpecial = special; }

    public String getStatusLinkedData() { return statusLinkedData; }

    public void setStatusLinkedData(String statusLinkedData) { this.statusLinkedData = statusLinkedData; }

    @Override
    public String toString() {
        return String.format(
                "Customer[id=%d, fileKey='%s', originalFileName='%s', logicalFolder='%s', targetYear='%d', storageHashedFileName='%s', storageDate='%s']",
                id, fileKey, originalFileName, logicalFolder, targetYear, storageHashedFileName, storageDate);
    }

    /**
     * Ecriture du fichier de controle avec dans l'ordre les informations suivantes:
     * fileKey, ownerKey, logicalFolder, storageDate, originalFileName, fileContentType, fileContentSize, fileContentHash, targetYear, externalRef, isSpecial
     * @return
     */
    public String toHeaderFileData() {
        return String.format("%1$s%n%2$s%n%3$s%n%4$tY-%4$tm-%4$td%n%5$s%n%6$s%n%7$d%n%8$s%n%9$d%n%10$s%n%11$s%n",
                fileKey, ownerKey, logicalFolder, storageDate, originalFileName, fileContentType, fileContentSize, fileContentHash, targetYear, externalRef, isSpecial);
    }

    public String toSpecialFileData(String dataFilePath) {
        return String.format("fileKey=%1$s%nonwerKey=%2$s%ndataFilePath=%3$s%noriginalFileName=%4$s%nfileContentType=%5$s%ntargetYear=%6$d%nexternalRef=%7$s%n",
                fileKey, ownerKey, dataFilePath, originalFileName, fileContentType, targetYear, externalRef);
    }
}
