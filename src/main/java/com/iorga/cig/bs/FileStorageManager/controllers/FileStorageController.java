package com.iorga.cig.bs.FileStorageManager.controllers;

import com.iorga.cig.bs.FileStorageManager.exceptions.*;
import com.iorga.cig.bs.FileStorageManager.models.BSFile;
import com.iorga.cig.bs.FileStorageManager.models.BSFileInformation;
import com.iorga.cig.bs.FileStorageManager.services.IBSFileInformationRepository;
import com.iorga.cig.bs.FileStorageManager.services.Tools;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
public class FileStorageController {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final static String API_VERSION = "/api/v1";

    @Autowired
    private IBSFileInformationRepository bsfiRepository;

    @Autowired
    private Tools toolServices;

    @Value("${nas.tasks.rootdir}")
    private String nasTasksRootdir;

    /**
     * Récupération des informations relatives au fichier demandé.
     * Avec vérification de la correspondance des informations trouvées avec le owner spécifié.
     *
     * @param fileKey identifiant du fichier
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

    private BSFileInformation getFileInfosForDownload(String fileKey)
            throws NotFound404Exception, Forbidden403Exception, VirusFound409Exception {

        // Récupération des informations relatives au fichier demandé
        BSFileInformation fileInfos = getFileInfos(fileKey);

        // Tests status (disponibilité)
        if (fileInfos.getStatus() == BSFile.Status.SOFT_DELETED.value()) {
            log.warn(String.format("Le fichier demandé a été supprimé (%s)", fileKey));
            throw new Forbidden403Exception("Le fichier demandé a été supprimé");
        }
        if (fileInfos.getStatus() == BSFile.Status.VIRUS_INFECTED.value()) {
            String statusLinkedData = fileInfos.getStatusLinkedData();
            log.warn(String.format("Le fichier %s est infecté par %s", fileKey, statusLinkedData));
            throw new VirusFound409Exception(statusLinkedData);
        }
        return fileInfos;
    }

    ///
    /// Opérations dédiées à la recherche/listing de fichiers
    ///

    @ApiOperation(value = "${FileStorageController.getBSFileInfo}",
            notes = "${FileStorageController.getBSFileInfo.notes}",
            response = BSFileInformation.class)
    @GetMapping(value = API_VERSION + "/fileInfos/{fileKey}", produces = "application/json")
    public ResponseEntity<BSFileInformation> getBSFileInfo(
            @ApiParam(value = "${FileStorageController.fileKey}", required = true) @PathVariable String fileKey)
            throws NotFound404Exception {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .body(getFileInfos(fileKey));
    }

    @ApiOperation(value = "${FileStorageController.getMultipleBSFileInfo}",
            notes = "${FileStorageController.getMultipleBSFileInfo.notes}",
            response = BSFileInformation.class,
            responseContainer = "List")
    @PostMapping(value = API_VERSION + "/fileInfos/getMultiple", produces = "application/json")
    public ResponseEntity<List<BSFileInformation>> getMultipleBSFileInfo(
            @ApiParam(value = "${FileStorageController.getMultipleBSFileInfo.fileKeys}", required = true) @RequestBody List<String> fileKeys)
            throws Forbidden403Exception {

        List<BSFileInformation> infos = bsfiRepository.findMultipleByFileKeys(fileKeys);
        if (infos.size() != fileKeys.size()) {
            log.error("getMultipleBSFileInfo() appellé avec au moins une fileKey inconnue. Phishing ?");
            throw new Forbidden403Exception("Au moins un fichier n'est pas reconnu.");
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS))
                .body(infos);
    }

    @ApiOperation(value = "${FileStorageController.listFilesFromFolder}",
            notes = "${FileStorageController.listFilesFromFolder.notes}",
            response = BSFileInformation.class,
            responseContainer = "List")
    @GetMapping(value = API_VERSION + "/folders/{folderName}/fileInfos", produces = "application/json")
    public ResponseEntity<List<BSFileInformation>> listFilesFromFolder(
            @ApiParam(value = "${FileStorageController.folderName}", required = true, example = "DOCS") @PathVariable String folderName,
            @ApiParam(value = "${FileStorageController.targetYear}", example = "2018") @RequestParam Integer targetYear,
            @ApiParam(value = "${FileStorageController.ownerKey}", example = "CDG-34") @RequestParam String ownerKey)
            throws NotFound404Exception {

        List<BSFileInformation> infos = bsfiRepository.listFilesFromFolder(folderName, targetYear, ownerKey);
        if (infos.size() < 1) {
            log.warn(String.format("Ce dossier ne contient aucun fichier (%s)", folderName));
            throw new NotFound404Exception();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS))
                .body(infos);
    }

    ///
    /// Opérations dédiées au dépot de fichiers
    ///

    @ApiOperation(value = "${FileStorageController.addFileIntoFolder}",
            notes = "${FileStorageController.addFileIntoFolder.notes}")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "File successfully added to folder"),
            @ApiResponse(code = 409, message = "Either the file with the same name already exists or there is a virus inside (in this case, an additional header in the reply is added: x-virus-infected)"),
    })
    @PostMapping(API_VERSION + "/folders/{folderName}/files")
    public ResponseEntity<BSFileInformation> addFileIntoFolder(
            @ApiParam(value = "${FileStorageController.folderName}", required = true, example = "DOCS") @PathVariable String folderName,
            @ApiParam(value = "${FileStorageController.addFileIntoFolder.bsFile}", required = true) @RequestBody BSFile bsFile)
            throws Conflict409Exception, ServerError500Exception, VirusFound409Exception {

        try {
            // Decoding du contenu du fichiers pour écriture disque (NAS)
            byte[] fileContent = toolServices.decodeFileContentFromBase64(bsFile.getFileContent());

            // Calcul du hash du contenu (pour vérification)
            String fileHash = toolServices.computeBytesSha256ToBase64(fileContent);

            // Mémorisation des informations concernant le fichier
            BSFileInformation info = BSFileInformation.createNew(bsFile, folderName, fileContent.length, fileHash);

            // Ecrire fichier sur le NAS
            toolServices.dataFileWrite(info, fileContent);
            // Ecrire fichier "header" sur le NAS
            toolServices.headerFileWrite(info);

            info.setStatus(BSFile.Status.AVAILABLE.value());

            info = bsfiRepository.save(info);
            if (info == null)
                return null;    // FIXME throw Ex

            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noCache())
                    .body(info);
        } catch (NoSuchAlgorithmException e) {
            throw new ServerError500Exception("SHA256 non supporté.", e);
        }
    }

    @ApiOperation(value = "${FileStorageController.addFileIntoSpecialFolder}",
            notes = "${FileStorageController.addFileIntoSpecialFolder.notes}")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "File successfully added to a \"special\" folder"),
            @ApiResponse(code = 409, message = "Either the file with the same name already exists or there is a virus inside (in this case, an additional header in the reply is added: x-virus-infected)"),
    })
    @PostMapping(API_VERSION + "/special-folders/{specialFolderName}/files")
    public ResponseEntity<BSFileInformation> addFileIntoSpecialFolder(
            @ApiParam(value = "${FileStorageController.folderName}", required = true, example = "TALENT") @PathVariable String specialFolderName,
            @ApiParam(value = "${FileStorageController.addFileIntoSpecialFolder.bsFile}", required = true) @RequestBody BSFile bsFile)
            throws BadRequest400Exception, Conflict409Exception, ServerError500Exception, VirusFound409Exception {

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

            info.setStatus(BSFile.Status.SPECIAL_READY_TO_BE_TREATED.value());
            bsfiRepository.save(info);

            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noCache())
                    .body(info);

        } catch (NoSuchAlgorithmException e) {
            throw new ServerError500Exception("SHA256 non supporté.", e);
        }
    }

    @ApiOperation(value = "${FileStorageController.addFileIntoPublicFolder}",
            notes = "${FileStorageController.addFileIntoPublicFolder.notes}")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "File successfully added to public folder"),
            @ApiResponse(code = 409, message = "Either the file with the same name already exists or there is a virus inside (in this case, an additional header in the reply is added: x-virus-infected)"),
    })
    @PostMapping(API_VERSION + "/public-folders/{publicFolderName}/files")
    public ResponseEntity<BSFileInformation> addFileIntoPublicFolder(
            @ApiParam(value = "${FileStorageController.publicFolderName}", required = true, example = "DOCS") @PathVariable String publicFolderName,
            @ApiParam(value = "${FileStorageController.addFileIntoPublicFolder.bsFile}", required = true) @RequestBody BSFile bsFile)
            throws Conflict409Exception, ServerError500Exception, VirusFound409Exception {

        try {
            // Decoding du contenu du fichiers pour écriture disque (NAS)
            byte[] fileContent = toolServices.decodeFileContentFromBase64(bsFile.getFileContent());

            // Calcul du hash du contenu (pour vérification)
            String fileHash = toolServices.computeBytesSha256ToBase64(fileContent);

            // Mémorisation des informations concernant le fichier "PUBLIC" (direct access from internet)
            BSFileInformation info = BSFileInformation.createNewPublic(bsFile, publicFolderName, fileContent.length, fileHash);

            // Ecrire fichier sur le NAS
            toolServices.dataFileWrite(info, fileContent);
            // Ecrire fichier "header" sur le NAS
            toolServices.headerFileWrite(info);

            info.setStatus(BSFile.Status.AVAILABLE.value());

            info = bsfiRepository.save(info);
            if (info == null)
                return null;    // FIXME throw Ex

            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noCache())
                    .body(info);
        } catch (NoSuchAlgorithmException e) {
            throw new ServerError500Exception("SHA256 non supporté.", e);
        }
    }

    @ApiOperation(value = "Sauvegarde d'un fichier généré par le service Long-Tasks.",
            notes = "")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "File successfully added to folder"),
            @ApiResponse(code = 409, message = "Either the file with the same name already exists."),
    })
    @PostMapping(API_VERSION + "/bsltm-folders/{folderName}/files")
    public ResponseEntity<BSFileInformation> addFileIntoBSLTMFolder(
            @ApiParam(value = "Répertoire = code de la Long Task génératrice.", required = true, example = "FULL_DGCL") @PathVariable String folderName,
            @ApiParam(value = "Informations concernant le fichier généré à enregistrer.", required = true) @RequestBody BSFile bsFile)
            throws Conflict409Exception, ServerError500Exception, NotFound404Exception {

        try {
            // Calcul du hash du contenu (pour vérification)
            Path ltFilePath = Paths.get(nasTasksRootdir, folderName, bsFile.getOriginalFileName());
            File ltFile = ltFilePath.toFile();
            String fileHash = toolServices.computeFileSha256ToBase64(ltFile);

            // Mémorisation des informations concernant le fichier
            BSFileInformation info = BSFileInformation.createNewPublic(bsFile, "BSLTM-"+folderName, (int) ltFile.length(), fileHash);

            // Déplacement du fichier sur le NAS
            toolServices.moveBSLTMFile(info, ltFilePath);
            // Ecrire fichier "header" sur le NAS
            toolServices.headerFileWrite(info);

            info.setStatus(BSFile.Status.AVAILABLE.value());

            info = bsfiRepository.save(info);
            if (info == null)
                return null;    // FIXME throw Ex

            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noCache())
                    .body(info);
        }
        catch (NoSuchAlgorithmException e) {
            throw new ServerError500Exception("SHA256 non supporté.", e);
        }
    }


    ///
    /// Opérations dédiées à la récupération du contenu de fichiers
    ///

    @ApiOperation(value = "${FileStorageController.downloadContent}",
            notes = "${FileStorageController.downloadContent.notes}")
    @ApiResponses(value = {
            @ApiResponse(code = 409, message = "There is a virus inside (an additional header in the reply is added: x-virus-infected) or the checksum failed"),
    })
    @GetMapping(value = API_VERSION + "/fileInfos/{fileKey}/getContent")
    public ResponseEntity<Resource> downloadContent(
            @ApiParam(value = "${FileStorageController.fileKey}", required = true) @PathVariable String fileKey)
            throws NotFound404Exception, Forbidden403Exception, ServerError500Exception, VirusFound409Exception, Conflict409Exception {

        // Récupération des informations relatives au fichier demandé
        BSFileInformation fileInfos = getFileInfosForDownload(fileKey);

        // Ajout des http headers permettant la récupération sous forme de téléchargement
        HttpHeaders headers = new HttpHeaders();
        if (fileInfos.getIsAttachment()) {
            headers.add("Content-Disposition", String.format("attachment; filename=%1$s;",
                    toolServices.normalizeFilename(fileInfos.getOriginalFileName())));
        }

        Resource resource = toolServices.getFileContentAsInputStream(fileInfos);
        return ResponseEntity.ok()
                .headers(headers)
                .cacheControl(CacheControl.noCache())
                .contentLength(fileInfos.getFileContentSize())
                .contentType(MediaType.parseMediaType(fileInfos.getFileContentType()))
                .body(resource);
    }

    @ApiOperation(value = "${FileStorageController.downloadPublicContent}",
            notes = "${FileStorageController.downloadPublicContent.notes}")
    @ApiResponses(value = {
            @ApiResponse(code = 403, message = "This is not a PUBLIC file"),
            @ApiResponse(code = 409, message = "There is a virus inside (an additional header in the reply is added: x-virus-infected) or the checksum failed"),
    })
    @GetMapping(value = API_VERSION + "/publicContent/{fileKey}")
    public ResponseEntity<Resource> downloadPublicContent(
            @ApiParam(value = "${FileStorageController.fileKey}", required = true) @PathVariable String fileKey)
            throws NotFound404Exception, Forbidden403Exception, ServerError500Exception, VirusFound409Exception, Conflict409Exception {

        // Récupération des informations relatives au fichier demandé
        BSFileInformation fileInfos = getFileInfosForDownload(fileKey);

        // Test complémentaire "public" ?
        if (!fileInfos.getIsPublic()) {
            log.error(String.format("Le fichier demandé n'est pas PUBLIC (%s)", fileKey));
            throw new Forbidden403Exception("");    // Pas d'indice
        }

        // Ajout des http headers permettant la récupération sous forme de téléchargement
        HttpHeaders headers = new HttpHeaders();
        if (fileInfos.getIsAttachment()) {
            headers.add("Content-Disposition", String.format("attachment; filename=%1$s;",
                    toolServices.normalizeFilename(fileInfos.getOriginalFileName())));
        }

        Resource resource = toolServices.getFileContentAsInputStream(fileInfos);
        return ResponseEntity.ok()
                .headers(headers)
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS))
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
    @PostMapping(value = API_VERSION + "/fileInfos/{fileKey}/updateStatus")
    public ResponseEntity<BSFileInformation> updateFileStatus(
            @ApiParam(value = "${FileStorageController.fileKey}", required = true) @PathVariable String fileKey,
            @ApiParam(value = "${FileStorageController.updateFileStatus.BSFile}", required = true) @RequestBody BSFile bsFile)
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

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .body(bsfiRepository.save(fileInfos));
    }

    @ApiOperation(value = "${FileStorageController.updateSpecialFileStatus}",
            notes = "${FileStorageController.updateSpecialFileStatus.notes}",
            response = BSFileInformation.class)
    @PostMapping(value = API_VERSION + "/fileInfos/{fileKey}/updateSpecialStatus")
    public ResponseEntity<BSFileInformation> updateSpecialFileStatus(
            @ApiParam(value = "${FileStorageController.fileKey}", required = true) @PathVariable String fileKey,
            @ApiParam(value = "${FileStorageController.deleteGo}", example = "true") @RequestParam Boolean deleteGo,
            @ApiParam(value = "${FileStorageController.updateSpecialFileStatus.BSFile}", required = true) @RequestBody BSFile bsFile)
            throws NotFound404Exception, BadRequest400Exception, ServerError500Exception {

        // Vérification des paramètres obligatoire
        if (bsFile.getStatus() == null) {
            throw new BadRequest400Exception("Une valeur pour le Status est obligatoire pour cette opération.");
        }

        // Récupération des informations relatives au fichier demandé
        BSFileInformation fileInfos = getFileInfos(fileKey);

        // Vérification
        if (!fileInfos.getIsSpecial()) {
            throw new BadRequest400Exception("Ce fichier n'est pas un fichier spécial.");
        }

        // Soft Suppression des informations
        fileInfos.setStatus(bsFile.getStatus());
        fileInfos.setStatusLinkedData(bsFile.getStatusLinkedData());

        if (deleteGo) {
            // Suppression du fichier go associé si demandé;
            try {
                toolServices.deleteSemaphoreFile(fileInfos);
            } catch (IOException e) {
                log.error("Une erreur est survenue durant la suppression du fichier sémaphore", e);
                throw new ServerError500Exception("Une erreur est survenue durant la suppression du fichier sémaphore.", e);
            }
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .body(bsfiRepository.save(fileInfos));
    }

    @ApiOperation(value = "${FileStorageController.deleteFile}",
            notes = "${FileStorageController.deleteFile.notes}")
    @DeleteMapping(value = API_VERSION + "/fileInfos/{fileKey}")
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
    @DeleteMapping(value = API_VERSION + "/fileInfos/{fileKey}/soft")
    @ResponseBody
    public void softDeleteFile(
            @ApiParam(value = "${FileStorageController.fileKey}", required = true) @PathVariable String fileKey)
            throws NotFound404Exception {

        // Récupération des informations relatives au fichier demandé
        BSFileInformation fileInfos = getFileInfos(fileKey);

        // Soft Suppression des informations
        fileInfos.setStatus(BSFile.Status.SOFT_DELETED.value());
        bsfiRepository.save(fileInfos);
    }

    @ExceptionHandler(VirusFound409Exception.class)
    public ResponseEntity<String> handleVirusFound409Exception(VirusFound409Exception ex) {
        // Handle virus found like told in MS spec https://msdn.microsoft.com/en-us/library/dd907072%28v=office.12%29.aspx?f=255&MSPPError=-2147217396
        return ResponseEntity.status(HttpStatus.CONFLICT).header("x-virus-infected", ex.getMessage()).build();
    }
}