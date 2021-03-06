package com.iorga.cig.bs.FileStorageManager.models;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.iorga.cig.bs.FileStorageManager.services.Tools;

import javax.persistence.*;
import java.security.NoSuchAlgorithmException;
import java.sql.Date;
import java.time.LocalDate;

@Entity(name = "BSFileInformation")
public class BSFileInformation {

    private static final String privateContentUriMask = "/api/v1/fileInfos/%s/getContent";
    private static final String publicContentUriMask = "/api/v1/publicContent/%s";
    private static final String infosUriMask = "/api/v1/fileInfos/%s";

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

    @Column(nullable = false)
    private Boolean isPublic = false;

    @Column(nullable = false)
    private Boolean isAttachment = true;

    @Column(nullable = false)
    private Boolean isContentNoVirusTrusted = false;

    @Transient
    private String contentUri;
    @Transient
    private String infosUri;

    protected BSFileInformation() {}

    private BSFileInformation(String fileKey, boolean isPublic) {
        this.fileKey = fileKey;
        this.storageDate = Date.valueOf(LocalDate.now());
        this.status = 0;
        this.isSpecial = false;
        this.isPublic = isPublic;
        this.isAttachment = true;
    }

    private static BSFileInformation createNew(BSFile bsFile, String folderName, Integer fileContentSize, String fileContentHash,
                                               boolean isSpecial, boolean isPublic)
            throws NoSuchAlgorithmException {

        // Calcul du nom de stockage du fichier qui doit être unique
        String computedFileName = String.format("%s/%s/%s/%s/%d/%s/%s",
                bsFile.getOwnerKey(),
                isSpecial,
                isPublic,
                folderName,
                bsFile.getTargetYear(),
                bsFile.getExternalRef() != null ? bsFile.getExternalRef() : "-",
                bsFile.getOriginalFileName());

        byte[] fileKeyBuffer = Tools.computeStringSha256(computedFileName);

        // Mémorisation des informations concernant le fichier
        BSFileInformation info = new BSFileInformation(Tools.toBase64(fileKeyBuffer, true), isPublic);
        info.setOriginalFileName(bsFile.getOriginalFileName());
        info.setFileContentType(bsFile.getFileContentType());
        info.setFileContentSize(fileContentSize);
        info.setFileContentHash(fileContentHash);
        info.setLogicalFolder(folderName);
        info.setTargetYear(bsFile.getTargetYear());
        info.setStorageHashedFileName(Tools.toHex(fileKeyBuffer));
        info.setOwnerKey(bsFile.getOwnerKey());
        info.setExternalRef(bsFile.getExternalRef());
        info.setIsAttachment(bsFile.getIsAttachment());
        // Special file ?
        info.setIsSpecial(isSpecial);
        return info;
    }

    public static BSFileInformation createNew(BSFile bsFile, String folderName, Integer fileContentSize, String fileContentHash)
            throws NoSuchAlgorithmException {

        return createNew(bsFile, folderName, fileContentSize, fileContentHash, false, false);
    }

    public static BSFileInformation createNewSpecial(BSFile bsFile, String folderName, Integer fileContentSize, String fileContentHash)
            throws NoSuchAlgorithmException {
        return createNew(bsFile, folderName, fileContentSize, fileContentHash, true, false);
    }

    public static BSFileInformation createNewPublic(BSFile bsFile, String folderName, Integer fileContentSize, String fileContentHash)
            throws NoSuchAlgorithmException {
        return createNew(bsFile, folderName, fileContentSize, fileContentHash, false, true);
    }

    public Long getId() {
        return id;
    }

    protected void setId(Long id) {
        this.id = id;
    }

    public String getFileKey() {
        return fileKey;
    }

    protected void setFileKey(String fileKey) {
        this.fileKey = fileKey;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    private void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }

    public String getStorageHashedFileName() {
        return storageHashedFileName;
    }

    private void setStorageHashedFileName(String storageHashedFileName) {
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

    private void setFileContentType(String fileContentType) {
        this.fileContentType = fileContentType;
    }

    public String getLogicalFolder() {
        return logicalFolder;
    }

    private void setLogicalFolder(String logicalFolder) {
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

    private void setFileContentHash(String fileContentHash) {
        this.fileContentHash = fileContentHash;
    }

    public Integer getFileContentSize() {
        return fileContentSize;
    }

    private void setFileContentSize(Integer fileContentSize) {
        this.fileContentSize = fileContentSize;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getTargetYear() {
        return targetYear;
    }

    private void setTargetYear(Integer targetYear) {
        this.targetYear = targetYear;
    }

    public String getExternalRef() {
        return externalRef;
    }

    private void setExternalRef(String externalRef) {
        this.externalRef = externalRef;
    }

    public Boolean getIsSpecial() {
        return isSpecial;
    }

    private void setIsSpecial(Boolean special) {
        this.isSpecial = special;
    }

    public Boolean getIsPublic() {
        return isPublic;
    }

    private void setIsPublic(Boolean pIsPublic) {
        this.isPublic = pIsPublic;
    }

    public Boolean getIsAttachment() {
        return isAttachment;
    }

    private void setIsAttachment(Boolean pIsAttachment) {
        this.isAttachment = pIsAttachment;
    }

    public String getStatusLinkedData() {
        return statusLinkedData;
    }

    public void setStatusLinkedData(String statusLinkedData) {
        this.statusLinkedData = statusLinkedData;
    }

    @Override
    public String toString() {
        return String.format(
                "Customer[id=%d, fileKey='%s', originalFileName='%s', logicalFolder='%s', targetYear='%d', storageHashedFileName='%s', storageDate='%s']",
                id, fileKey, originalFileName, logicalFolder, targetYear, storageHashedFileName, storageDate);
    }

    /**
     * Ecriture du fichier de controle avec dans l'ordre les informations suivantes:
     * fileKey, ownerKey, logicalFolder, storageDate, originalFileName, fileContentType, fileContentSize,
     * fileContentHash, targetYear, externalRef, isSpecial, isPublic, isAttachment
     *
     * @return
     */
    public String toHeaderFileData() {
        return String.format("%1$s%n%2$s%n%3$s%n%4$tY-%4$tm-%4$td%n%5$s%n%6$s%n%7$d%n%8$s%n%9$d%n%10$s%n%11$s%n%12$s%n%13$s%n",
                fileKey, ownerKey, logicalFolder, storageDate, originalFileName, fileContentType, fileContentSize,
                fileContentHash, targetYear, externalRef, isSpecial, isPublic, isAttachment);
    }

    public String toSpecialFileData(String dataFilePath) {
        return String.format("fileKey=%1$s%nonwerKey=%2$s%ndataFilePath=%3$s%noriginalFileName=%4$s%nfileContentType=%5$s%ntargetYear=%6$d%nexternalRef=%7$s%n",
                fileKey, ownerKey, dataFilePath, originalFileName, fileContentType, targetYear, externalRef);
    }

    @JsonGetter("contentUri")
    public String getContentUri() {
        if (this.contentUri == null) this.contentUri = String.format(this.isPublic ? publicContentUriMask : privateContentUriMask, fileKey);
        return this.contentUri;
    }

    @JsonGetter("infosUri")
    public String getInfosUri() {
        if (this.infosUri == null) this.infosUri = String.format(infosUriMask, fileKey);
        return this.infosUri;
    }

    public Boolean getIsContentNoVirusTrusted() {
        return isContentNoVirusTrusted;
    }

    public void setIsContentNoVirusTrusted(Boolean contentNoVirusTrusted) {
        isContentNoVirusTrusted = contentNoVirusTrusted;
    }
}
