package com.iorga.cig.bs.FileStorageManager.models;

public class BSFile {

    private String originalFileName;
    private String fileContentType;
    private String ownerKey;
    private String fileContent;
    private Integer targetYear;
    private String externalRef;
    private Integer status;
    private String statusLinkedData;
    private Boolean isAttachment;


    public enum Status {
        SOFT_DELETED(-1),
        VIRUS_INFECTED(-2),
        SPECIAL_READY_TO_BE_TREATED(1),
        AVAILABLE(2),;
        private final int status;

        Status(int status) {
            this.status = status;
        }

        public int value() {
            return status;
        }
    }

    public BSFile() {
        this.isAttachment = true;
    }

    public BSFile(String originalFileName, String fileContentType, String ownerKey, Integer targetYear, Boolean isAttachment) {
        this.originalFileName = originalFileName;
        this.fileContentType = fileContentType;
        this.ownerKey = ownerKey;
        this.targetYear = targetYear;
        this.isAttachment = isAttachment;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }

    public String getFileContentType() {
        return fileContentType;
    }

    public void setFileContentType(String fileContentType) {
        this.fileContentType = fileContentType;
    }

    public String getOwnerKey() {
        return ownerKey;
    }

    public void setOwnerKey(String ownerKey) {
        this.ownerKey = ownerKey;
    }

    public String getFileContent() {
        return fileContent;
    }

    public void setFileContent(String fileContent) {
        this.fileContent = fileContent;
    }

    public Integer getTargetYear() {
        return targetYear;
    }

    public void setTargetYear(Integer targetYear) {
        this.targetYear = targetYear;
    }

    public String getExternalRef() {
        return externalRef;
    }

    public void setExternalRef(String externalRef) {
        this.externalRef = externalRef;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getStatusLinkedData() {
        return statusLinkedData;
    }

    public void setStatusLinkedData(String statusLinkedData) {
        this.statusLinkedData = statusLinkedData;
    }

    public Boolean getIsAttachment() {
        return isAttachment;
    }

    private void setIsAttachment(Boolean pIsAttachment) {
        this.isAttachment = pIsAttachment;
    }
}
