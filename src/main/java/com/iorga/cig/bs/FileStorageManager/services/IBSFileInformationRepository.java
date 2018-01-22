package com.iorga.cig.bs.FileStorageManager.services;

import com.iorga.cig.bs.FileStorageManager.models.BSFileInformation;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import javax.transaction.Transactional;
import java.util.List;

public interface IBSFileInformationRepository extends CrudRepository<BSFileInformation, Long> {

    @Transactional(Transactional.TxType.NEVER)
    @Query("SELECT fi FROM BSFileInformation fi WHERE logicalFolder = :folderName " +
            "AND (:targetYear = 0 OR fi.targetYear = :targetYear) "+
            "AND (:ownerKey = '*' OR fi.ownerKey = :ownerKey) "+
            "ORDER BY originalFileName")
    List<BSFileInformation> listFilesFromFolder(@Param("folderName") String folderName,
                                                @Param("targetYear") Integer targetYear,
                                                @Param("ownerKey") String ownerKey);

    @Query("SELECT fi FROM BSFileInformation fi WHERE fi.fileKey = :fileKey")
    List<BSFileInformation> findByFileKey(@Param("fileKey") String fileKey);

    @Transactional(Transactional.TxType.NEVER)
    @Query("SELECT fi FROM BSFileInformation fi WHERE fi.fileKey IN (:fileKeys) ORDER BY originalFileName")
    List<BSFileInformation> findMultipleByFileKeys(@Param("fileKeys") List<String> fileKeys);
}
