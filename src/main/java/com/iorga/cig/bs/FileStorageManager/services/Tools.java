package com.iorga.cig.bs.FileStorageManager.services;

import com.iorga.cig.bs.FileStorageManager.models.BSFileInformation;
import com.iorga.cig.bs.FileStorageManager.exceptions.Conflict409Exception;
import com.iorga.cig.bs.FileStorageManager.exceptions.NotFound404Exception;
import com.iorga.cig.bs.FileStorageManager.exceptions.ServerError500Exception;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.Base64;

@Service
public class Tools {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Value("${nas.active.rootdir}")
    private String nasActiveRootdir;

    @Value("${nas.special.rootdir}")
    private String nasSpecialRootdir;

    @Value("${nas.header.rootdir}")
    private String nasHeaderRootdir;

    @Value("${nas.archived.rootdir}")
    private String nasArchivedRootdir;

    @Value("${nas.archived.afterNDays}")
    private Integer nasArchivedAfterNDays;

    public static String toHex(byte[] digest) {
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(String.format("%1$02X", b));
        }
        return sb.toString();
    }

    public String computeBytesSha256ToBase64(byte[] bytes) throws NoSuchAlgorithmException {
        MessageDigest digester = MessageDigest.getInstance("SHA-256");
        digester.update(bytes);
        return Base64.getEncoder().encodeToString(digester.digest());
    }

//    public String computefileContentSha256ToBase64(File file) throws NoSuchAlgorithmException, IOException {
//        byte[] data = Files.readAllBytes(file.toPath());
//        return computeBytesSha256ToBase64(data);
//    }
//
//
    public static String toBase64(byte[] bytes, boolean urlSafe) {
        String retVal = Base64.getEncoder().encodeToString(bytes);
        return (urlSafe)
                ? retVal.replaceAll("\\+", "-").replaceAll("\\/", "_").replaceAll("=", "")
                : retVal;
    }

    public static byte[] computeStringSha256(String data) throws NoSuchAlgorithmException {
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        MessageDigest digester = MessageDigest.getInstance("SHA-256");
        digester.update(bytes);
        return digester.digest();
    }

    /**
     * Convertit une chaine de caractère en un nom de fichier en se basant sur la représentation hexa du SHA256 de la chaine.
     *
     * @param data expression représentant le nom de fichier virtuel
     * @return Nom de fichier sous la forme d'une chaine Hexa de 64 caractères
     * @throws NoSuchAlgorithmException
     */
    public String computeFilenameSha256ToHex(String data) throws NoSuchAlgorithmException {
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        MessageDigest digester = MessageDigest.getInstance("SHA-256");
        digester.update(bytes);
        return toHex(digester.digest());
    }

    public byte[] decodeFileContentFromBase64(String fileDataBase64) {
        return Base64.getDecoder().decode(fileDataBase64);
    }

    /**
     * Ecrit les données dans un fichiers dont le chemin est généré automatiquement à partir des informations
     * owner, répertoire virtuel et date du jour
     * @param filePathObj informations relatives au fichier à écrire
     * @param data      données à écrire
     * @throws Conflict409Exception
     * @throws ServerError500Exception
     */
    private Path fileWriteData(Path filePathObj, byte[] data) throws Conflict409Exception, ServerError500Exception {
        try {
            boolean fileExists = Files.exists(filePathObj);
            if (!fileExists) {
                Files.write(filePathObj, data, StandardOpenOption.CREATE_NEW);
                return filePathObj;
            } else {
                log.warn("Un fichier de même nom existe déjà dans ce dossier", filePathObj);
                throw new Conflict409Exception("Un fichier de même nom existe déjà dans ce dossier.");
            }
        }
        catch (IOException ioExceptionObj) {
            log.error("Une erreur est survenue durant l'écriture du fichier", ioExceptionObj);
            throw new ServerError500Exception("Une erreur est survenue durant l'écriture du fichier", ioExceptionObj);
        }
    }

    private Path fileWrite(String rootDir, BSFileInformation fileInfos, String fileExt, byte[] data) throws Conflict409Exception, ServerError500Exception {
        try {
            // Initialisation de la structure de répertoire d'acceuil
            LocalDate fileDate = fileInfos.getStorageDate().toLocalDate();
            Path targetDir = Files.createDirectories(Paths.get(rootDir, String.format("%tY", fileDate), String.format("%tm", fileDate), String.format("%td", fileDate)));

            // Initialisation de l'objet de stockage
            Path filePathObj = Paths.get(targetDir.toString(), fileInfos.getStorageHashedFileName() + fileExt);
            return fileWriteData(filePathObj, data);
        }
        catch (IOException ioExceptionObj) {
            log.error("Une erreur est survenue durant l'écriture du fichier", ioExceptionObj);
            throw new ServerError500Exception("Une erreur est survenue durant l'écriture du fichier", ioExceptionObj);
        }
    }

    public Path dataFileWrite(BSFileInformation fileInfos, byte[] data) throws Conflict409Exception, ServerError500Exception {
        return fileWrite(nasActiveRootdir, fileInfos, "", data);
    }

    public Path headerFileWrite(BSFileInformation fileInfos) throws Conflict409Exception, ServerError500Exception {
        return fileWrite(nasHeaderRootdir, fileInfos, ".bsfh", fileInfos.toHeaderFileData().getBytes());
    }

    public Path semaphoreSpecialFileWrite(BSFileInformation fileInfos, Path dataFilePathObj) throws Conflict409Exception, ServerError500Exception {
        try {
            // Initialisation de la structure de répertoire d'acceuil
            Path targetDir = Files.createDirectories(Paths.get(nasSpecialRootdir, fileInfos.getLogicalFolder()));

            // Initialisation de l'objet de stockage
            Path semaphoreFilePathObj = Paths.get(targetDir.toString(), fileInfos.getStorageHashedFileName() + ".go");
            return fileWriteData(semaphoreFilePathObj, fileInfos.toSpecialFileData(dataFilePathObj.toString()).getBytes());
        }
        catch (IOException ioExceptionObj) {
            log.error("Une erreur est survenue durant l'écriture du fichier", ioExceptionObj);
            throw new ServerError500Exception("Une erreur est survenue durant l'écriture du fichier", ioExceptionObj);
        }
    }

    private Path getDataFilePathObj(BSFileInformation fileInfos) {
        // Calcul du répertoire de stockage
        LocalDate fileDate = fileInfos.getStorageDate().toLocalDate();

        String rootDir = nasActiveRootdir;
        if (nasArchivedAfterNDays > 0 && LocalDate.now().minusDays(nasArchivedAfterNDays).compareTo(fileDate) > 0) {
            rootDir = nasArchivedRootdir;
        }
        return Paths.get(rootDir, String.format("%tY", fileDate), String.format("%tm", fileDate), String.format("%td", fileDate), fileInfos.getStorageHashedFileName());
    }

    private Path getHeaderFilePathObj(BSFileInformation fileInfos) {
        // Calcul du répertoire de stockage
        LocalDate fileDate = fileInfos.getStorageDate().toLocalDate();
        return Paths.get(nasHeaderRootdir, String.format("%tY", fileDate), String.format("%tm", fileDate), String.format("%td", fileDate), fileInfos.getStorageHashedFileName() + ".bsfh");
    }

    /**
     * Suppression physique des fichiers associé aux Informations données
     * @param fileInfos informations concernant le fichier à supprimer
     * @throws ServerError500Exception
     */
    public void deleteFile(BSFileInformation fileInfos) throws ServerError500Exception {
        try {
            if (fileInfos.getIsSpecial()) {
                Path semaphoreFilePathObj = Paths.get(nasSpecialRootdir, fileInfos.getLogicalFolder(), fileInfos.getStorageHashedFileName() + ".go");
                if (Files.exists(semaphoreFilePathObj)) {
                    Files.delete(semaphoreFilePathObj);
                }
            }
            Path dataFilePathObj = getDataFilePathObj(fileInfos);
            if (Files.exists(dataFilePathObj)) {
                Files.delete(dataFilePathObj);
            }
            Path headerFilePathObj = getHeaderFilePathObj(fileInfos);
            if (Files.exists(headerFilePathObj)) {
                Files.delete(headerFilePathObj);
            }
        }
        catch (IOException ioExceptionObj) {
            log.error("Une erreur est survenue durant la lecture du fichier", ioExceptionObj);
            throw new ServerError500Exception("Une erreur est survenue durant la lecture du fichier", ioExceptionObj);
        }
    }

    /**
     * Ouvre une stream de lecture vers le fichier associé aux informations fournies.
     * En fonction de l'ancienneté de la création de l'élément et de l'activation ou non de l'archivage,
     * le répertoire racine est modifié automatiquement.
     * @param fileInfos informations relatives au fichier à charger.
     * @return une stream de lecture du fichier associé.
     * @throws NotFound404Exception
     * @throws ServerError500Exception
     */
    public InputStreamResource getFileContentAsInputStream(BSFileInformation fileInfos) throws NotFound404Exception, ServerError500Exception {
        try {
            Path filePathObj = getDataFilePathObj(fileInfos);
            if (Files.exists(filePathObj)) {
                return new InputStreamResource(Files.newInputStream(filePathObj));
            } else {
                throw new NotFound404Exception();
            }
        }
        catch (IOException ioExceptionObj) {
            log.error("Une erreur est survenue durant la lecture du fichier", ioExceptionObj);
            throw new ServerError500Exception("Une erreur est survenue durant la lecture du fichier", ioExceptionObj);
        }
    }

    public String normalizeFilename(String filename) {
        return Normalizer.normalize(filename, Normalizer.Form.NFD)
                .replaceAll("[\u0300-\u036f]", "")
                .replaceAll("[^a-zA-Z0-9_.]", "_");
    }

    public String encodeStringToUTF8(String data) {
        byte[] ptext = data.getBytes(StandardCharsets.ISO_8859_1);
        return new String(ptext, StandardCharsets.UTF_8);
    }
}
