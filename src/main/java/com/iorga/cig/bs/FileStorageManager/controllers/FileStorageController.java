package com.iorga.cig.bs.FileStorageManager.controllers;

import com.iorga.cig.bs.FileStorageManager.exceptions.*;
import com.iorga.cig.bs.FileStorageManager.models.*;
import com.iorga.cig.bs.FileStorageManager.services.IBSFileInformationRepository;
import com.iorga.cig.bs.FileStorageManager.services.Tools;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.List;

@RestController
public class FileStorageController {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private IBSFileInformationRepository bsfiRepository;

    @Autowired
    private Tools toolServices;

    /**
     * Récupération des informations relatives au fichier demandé.
     * Avec vérification de la correspondance des informations trouvées avec le owner spécifié.
     * @param fileKey  identifiant du fichier
     * @return file informations
     * @throws NotFound404Exception
     */
    private BSFileInformation getFileInfos(String fileKey) throws NotFound404Exception {
        List<BSFileInformation> infos = bsfiRepository.findByFileKey(fileKey);
        if (infos.size() < 1) {
            log.warn(String.format("Le fichier demandé n'existe pas (%s)", fileKey));
            throw new NotFound404Exception();
        }
        return infos.get(0);
    }

    ///
    /// Opérations dédiées à la recherche/listing de fichiers
    ///

    @ApiOperation(value = "${FileStorageController.getBSFileInfo}",
            notes = "${FileStorageController.getBSFileInfo.notes}",
            response = BSFileInformation.class)
    @GetMapping(value = "/fileInfos/{fileKey}", produces = "application/json")
    @ResponseBody
    public BSFileInformation getBSFileInfo(
            @ApiParam(value = "${FileStorageController.fileKey}", required = true) @PathVariable String fileKey)
            throws NotFound404Exception {
        return getFileInfos(fileKey);
    }

    @ApiOperation(value = "${FileStorageController.getMultipleBSFileInfo}",
            notes = "${FileStorageController.getMultipleBSFileInfo.notes}",
            response = BSFileInformation.class,
            responseContainer = "List")
    @PostMapping(value = "/fileInfos/getMultiple", produces = "application/json")
    @ResponseBody
    public List<BSFileInformation> getMultipleBSFileInfo(
            @ApiParam(value = "${FileStorageController.getMultipleBSFileInfo.fileKeys}", required = true) @RequestBody List<String> fileKeys)
            throws Forbidden403Exception {

        List<BSFileInformation> infos = bsfiRepository.findMultipleByFileKeys(fileKeys);
        if (infos.size() !=  fileKeys.size()) {
            log.error("getMultipleBSFileInfo() appellé avec au moins une fileKey inconnue. Phishing ?");
            throw new Forbidden403Exception("Au moins un fichier n'est pas reconnu.");
        }
        return infos;
    }

    @ApiOperation(value = "${FileStorageController.listFilesFromFolder}",
            notes = "${FileStorageController.listFilesFromFolder.notes}",
            response = BSFileInformation.class,
            responseContainer = "List")
    @GetMapping(value = "/owners/{ownerKey}/folders/{folderName}/fileInfos", produces = "application/json")
    @ResponseBody
    public List<BSFileInformation> listFilesFromFolder(
            @ApiParam(value = "${FileStorageController.ownerKey}", required = true, example = "CIG-50") @PathVariable String ownerKey,
            @ApiParam(value = "${FileStorageController.folderName}", required = true, example = "DOCS") @PathVariable String folderName)
            throws NotFound404Exception {

        List<BSFileInformation> infos = bsfiRepository.listFilesFromFolder(ownerKey, folderName);
        if (infos.size() < 1) {
            log.warn(String.format("Ce dossier ne contient aucun fichier (%s)", folderName));
            throw new NotFound404Exception();
        }
        return infos;
    }

    ///
    /// Opérations dédiées au dépot de fichiers
    ///

    @ApiOperation(value = "${FileStorageController.addFileIntoFolder}",
            notes = "${FileStorageController.addFileIntoFolder.notes}")
    @PostMapping("/folders/{folderName}/files")
    @ResponseBody
    public BSFileInformation addFileIntoFolder(
            @ApiParam(value = "${FileStorageController.folderName}", required = true, example = "DOCS") @PathVariable String folderName,
            @ApiParam(value = "${FileStorageController.addFileIntoFolder.bsFile}", required = true) @RequestBody BSFile bsFile)
            throws Conflict409Exception, ServerError500Exception {

        try {
            // Decoding du contenu du fichiers pour écriture disque (NAS)
            byte[] fileContent = toolServices.decodeFileContentFromBase64(bsFile.getFileContent());

            // Calcul du hash du contenu (pour vérification)
            String fileHash = toolServices.computeBytesSha256ToBase64(fileContent);

            // Mémorisation des informations concernant le fichier
            BSFileInformation info = BSFileInformation.createNew(bsFile,  folderName, fileContent.length, fileHash);

            // Ecrire fichier sur le NAS
            toolServices.dataFileWrite(info, fileContent);
            // Ecrire fichier "header" sur le NAS
            toolServices.headerFileWrite(info);

            //TODO Status permet de gérer les fichiers dispo, en attente de check anti-virus, archivés, ...
            info.setStatus(2);

            info = bsfiRepository.save(info);
            if (info == null)
                return null;    // FIXME throw Ex

            return info;
        }
        catch (NoSuchAlgorithmException e) {
            throw new ServerError500Exception("SHA256 non supporté.", e);
        }
    }

    @ApiOperation(value = "${FileStorageController.addFileIntoSpecialFolder}",
            notes = "${FileStorageController.addFileIntoSpecialFolder.notes}")
    @PostMapping("/special-folders/{specialFolderName}/files")
    @ResponseBody
    public BSFileInformation addFileIntoSpecialFolder(
            @ApiParam(value = "${FileStorageController.folderName}", required = true, example = "TALENT") @PathVariable String specialFolderName,
            @ApiParam(value = "${FileStorageController.addFileIntoSpecialFolder.bsFile}", required = true) @RequestBody BSFile bsFile)
            throws BadRequest400Exception, Conflict409Exception, ServerError500Exception {

        if (bsFile.getExternalRef() == null) {
            throw new BadRequest400Exception("Une référence externe est obligatoire pour le dépot de fichier dans les dossiers spéciaux.");
        }

        try {
            // Decoding du contenu du fichiers pour écriture disque (NAS)
            byte[] fileContent = toolServices.decodeFileContentFromBase64(bsFile.getFileContent());

            // Calcul du hash du contenu (pour vérification)
            String fileHash = toolServices.computeBytesSha256ToBase64(fileContent);

            // Mémorisation des informations concernant le fichier
            BSFileInformation info = BSFileInformation.createNewSpecial(bsFile, specialFolderName, fileContent.length, fileHash);

            // Ecrire fichier sur le NAS
            Path dataFilePathObj = toolServices.dataFileWrite(info, fileContent);
            // Ecrire fichier "header" sur le NAS
            toolServices.headerFileWrite(info);

            info = bsfiRepository.save(info);
            if (info == null)
                return null;    // FIXME throw Ex

            // Ecriture du fichier semaphore permettant le traitement special
            toolServices.semaphoreSpecialFileWrite(info, dataFilePathObj);

            // Status = 1 prêt pour être traité
            info.setStatus(1);
            bsfiRepository.save(info);

            return info;
        } catch (NoSuchAlgorithmException e) {
            throw new ServerError500Exception("SHA256 non supporté.", e);
        }
    }

    ///
    /// Opérations dédiées à la récupération du contenu de fichiers
    ///

    @ApiOperation(value = "${FileStorageController.downloadContent}",
            notes = "${FileStorageController.downloadContent.notes}")
    @GetMapping(value = "/fileInfos/{fileKey}/getContent")
    public ResponseEntity<Resource> downloadContent(
            @ApiParam(value = "${FileStorageController.fileKey}", required = true) @PathVariable String fileKey)
            throws NotFound404Exception, Forbidden403Exception, ServerError500Exception {

        // Récupération des informations relatives au fichier demandé
        BSFileInformation fileInfos = getFileInfos(fileKey);

        // Tests status (disponibilité)
        if (fileInfos.getStatus() == -1) {
            log.warn(String.format("Le fichier demandé a été supprimé (%s)", fileKey));
            throw new Forbidden403Exception("Le fichier demandé a été supprimé");
        }

        // Ajout des http headers permettant la récupération sous forme de téléchargement
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition", String.format("attachment; filename=%1$s;",
                toolServices.normalizeFilename(fileInfos.getOriginalFileName())));
        headers.add("Cache-Control", "no-cache, no-store, must-revalidate");
        headers.add("Pragma", "no-cache");
        headers.add("Expires", "0");

        Resource resource = toolServices.getFileContentAsInputStream(fileInfos);
        return ResponseEntity.ok()
                .headers(headers)
                .contentLength(fileInfos.getFileContentSize())
                .contentType(MediaType.parseMediaType(fileInfos.getFileContentType()))
                .body(resource);
    }

    ///
    /// Opérations dédiées à la gestion de fichiers
    ///

    @ApiOperation(value = "${FileStorageController.updateFileStatus}",
            notes = "${FileStorageController.updateFileStatus.notes}",
            response = BSFileInformation.class)
    @PatchMapping(value = "/fileInfos/{fileKey}/updateStatus")
    public BSFileInformation updateFileStatus(
            @ApiParam(value = "${FileStorageController.fileKey}", required = true) @PathVariable String fileKey,
            @ApiParam(value = "${FileStorageController.softDeleteFile.BSFile}", required = true) @RequestBody BSFile bsFile)
            throws NotFound404Exception, BadRequest400Exception {

        // Vérification des paramètres obligatoire
        if (bsFile.getStatus() == null) {
            throw new BadRequest400Exception("Une valeur pour le Status est obligatoire pour cette opération.");
        }

        // Récupération des informations relatives au fichier demandé
        BSFileInformation fileInfos = getFileInfos(fileKey);

        // Soft Suppression des informations
        fileInfos.setStatus(bsFile.getStatus());
        fileInfos.setStatusLinkedData(bsFile.getStatusLinkedData());
        return bsfiRepository.save(fileInfos);
    }

    @ApiOperation(value = "${FileStorageController.deleteFile}",
            notes = "${FileStorageController.deleteFile.notes}")
    @DeleteMapping(value = "/fileInfos/{fileKey}")
    @ResponseBody
    public void deleteFile(
            @ApiParam(value = "${FileStorageController.fileKey}", required = true) @PathVariable String fileKey)
            throws NotFound404Exception, ServerError500Exception {

        // Récupération des informations relatives au fichier demandé
        BSFileInformation fileInfos = getFileInfos(fileKey);

        // Suppression des informations
        bsfiRepository.delete(fileInfos.getId());

        // Suppression des fichiers physiques associés
        toolServices.deleteFile(fileInfos);
    }

    @ApiOperation(value = "${FileStorageController.softDeleteFile}",
            notes = "${FileStorageController.softDeleteFile.notes}")
    @DeleteMapping(value = "/fileInfos/{fileKey}/soft")
    @ResponseBody
    public void softDeleteFile(
            @ApiParam(value = "${FileStorageController.fileKey}", required = true) @PathVariable String fileKey)
            throws NotFound404Exception {

        // Récupération des informations relatives au fichier demandé
        BSFileInformation fileInfos = getFileInfos(fileKey);

        // Soft Suppression des informations
        fileInfos.setStatus(-1);
        bsfiRepository.save(fileInfos);
    }
}