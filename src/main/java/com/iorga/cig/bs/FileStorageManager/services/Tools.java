package com.iorga.cig.bs.FileStorageManager.services;

import com.iorga.cig.bs.FileStorageManager.exceptions.Conflict409Exception;
import com.iorga.cig.bs.FileStorageManager.exceptions.NotFound404Exception;
import com.iorga.cig.bs.FileStorageManager.exceptions.ServerError500Exception;
import com.iorga.cig.bs.FileStorageManager.exceptions.VirusFound409Exception;
import com.iorga.cig.bs.FileStorageManager.models.BSFile;
import com.iorga.cig.bs.FileStorageManager.models.BSFileInformation;
import com.iorga.cig.bs.FileStorageManager.models.BSFileType;
import fi.solita.clamav.ClamAVClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

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

    @Value("${clamav.host:clamav}")
    private String clamavHost;

    @Value("${clamav.port:3310}")
    private int clamavPort;

    private final static boolean isWindowsHost;

    @Autowired
    private IBSFileInformationRepository bsfiRepository;

    static {
        isWindowsHost = System.getProperty("os.name").toLowerCase().startsWith("windows");
    }

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


    private Path setPathNixGroup(Path pPath, BSFileType fileType) throws IOException {
        if (!isWindowsHost && !StringUtils.isEmpty(fileType.getNixGroup())) {
            UserPrincipalLookupService lookupService = FileSystems.getDefault().getUserPrincipalLookupService();
            GroupPrincipal group = lookupService.lookupPrincipalByGroupName(fileType.getNixGroup());
            Files.getFileAttributeView(pPath, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS).setGroup(group);
        }
        return pPath;
    }
    /**
     * Affectation des droits d'accès au fichier en fonction de sont types BS
     * @param pPath path du fichier
     * @param pFileType  type BS du fichier
     * @return
     * @throws IOException
     */
    private Path setFilePermissions(Path pPath, BSFileType pFileType) throws IOException {
        if (!isWindowsHost) {
            Files.setPosixFilePermissions(pPath, pFileType.getNixPerms());
        }
        return setPathNixGroup(pPath, pFileType);
    }

    /**
     * Affectation des droits d'accès au répertoire en fonction de sont types BS
     * Comme les répertoires *unix doivent avoir le flag "execute" (dangereux pour les fichiers déposés)
     * on vérifie que cette affectation des droits cible bien un répertoire.
     * @param pPath path object du répertoire
     * @param pFileType  type de fichier contenus dans ce répertoire
     * @return
     * @throws IOException
     */
    private Path setDirectoryPermissions(Path pPath, BSFileType pFileType) throws IOException {
        if (!Files.isDirectory(pPath, LinkOption.NOFOLLOW_LINKS)) {
            log.error("Ce n'est pas le chemin d'accès à un répertoire.", pPath);
            throw new IOException("Ce n'est pas le chemin d'accès à un répertoire.");
        }
        if (!isWindowsHost) {
            Set<PosixFilePermission> dirPerms = new HashSet<>(pFileType.getNixPerms());
            dirPerms.add(PosixFilePermission.OWNER_EXECUTE);
            dirPerms.add(PosixFilePermission.GROUP_EXECUTE);
            Files.setPosixFilePermissions(pPath, dirPerms);
        }
        return setPathNixGroup(pPath, pFileType);
    }

    /**
     * Ecrit les données dans un fichiers dont le chemin est généré automatiquement à partir des informations
     * owner, répertoire virtuel et date du jour
     * @param filePathObj informations relatives au fichier à écrire
     * @param data      données à écrire
     * @throws Conflict409Exception
     * @throws ServerError500Exception
     */
    private Path fileWriteData(Path filePathObj, BSFileType fileType, byte[] data)
            throws Conflict409Exception, ServerError500Exception {
        try {
            boolean fileExists = Files.exists(filePathObj);
            if (!fileExists) {
                Files.write(filePathObj, data, StandardOpenOption.CREATE_NEW);
                return setFilePermissions(filePathObj, fileType);
            }
            else {
                log.warn("Un fichier de même nom existe déjà dans ce dossier", filePathObj);
                throw new Conflict409Exception("Un fichier de même nom existe déjà dans ce dossier.");
            }
        }
        catch (IOException ioExceptionObj) {
            log.error("Une erreur est survenue durant l'écriture du fichier", ioExceptionObj);
            throw new ServerError500Exception("Une erreur est survenue durant l'écriture du fichier", ioExceptionObj);
        }
    }

    private Path fileWrite(String rootDir, BSFileInformation fileInfos, String fileExt, BSFileType fileType, byte[] data)
            throws Conflict409Exception, ServerError500Exception, VirusFound409Exception {
        try {
            antivirusScan(data);
            // Initialisation de la structure de répertoire d'acceuil
            LocalDate fileDate = fileInfos.getStorageDate().toLocalDate();
            Path targetDir = setDirectoryPermissions(Files.createDirectories(Paths.get(rootDir, String.format("%tY", fileDate), String.format("%tm", fileDate), String.format("%td", fileDate))), fileType);

            // Initialisation de l'objet de stockage
            Path filePathObj = Paths.get(targetDir.toString(), fileInfos.getStorageHashedFileName() + fileExt);
            return fileWriteData(filePathObj, fileType, data);
        }
        catch (IOException ioExceptionObj) {
            log.error("Une erreur est survenue durant l'écriture du fichier", ioExceptionObj);
            throw new ServerError500Exception("Une erreur est survenue durant l'écriture du fichier", ioExceptionObj);
        }
    }

    private void antivirusScan(byte[] data) throws ServerError500Exception, VirusFound409Exception {
        antivirusScan(new ByteArrayInputStream(data));
    }

    private void antivirusScan(InputStream inputStream) throws ServerError500Exception, VirusFound409Exception {
        ClamAVClient clamAVClient = new ClamAVClient(clamavHost, clamavPort);
        byte[] scanResult;
        try {
            scanResult = clamAVClient.scan(inputStream);
        } catch (Exception e) {
            throw new ServerError500Exception("Couldn't scan the input", e);
        }
        if (!ClamAVClient.isCleanReply(scanResult)) {
            throw new VirusFound409Exception(new String(scanResult));
        }
    }

    public Path dataFileWrite(BSFileInformation fileInfos, byte[] data) throws Conflict409Exception, ServerError500Exception, VirusFound409Exception {
        return fileWrite(nasActiveRootdir, fileInfos, "", BSFileType.FILES, data);
    }

    public Path headerFileWrite(BSFileInformation fileInfos) throws Conflict409Exception, ServerError500Exception, VirusFound409Exception {
        return fileWrite(nasHeaderRootdir, fileInfos, ".bsfh", BSFileType.HEARDERS, fileInfos.toHeaderFileData().getBytes());
    }

    public Path semaphoreSpecialFileWrite(BSFileInformation fileInfos, Path dataFilePathObj) throws Conflict409Exception, ServerError500Exception {
        try {
            // Initialisation de la structure de répertoire d'acceuil
            Path targetDir = setDirectoryPermissions(Files.createDirectories(Paths.get(nasSpecialRootdir, fileInfos.getLogicalFolder())), BSFileType.SPECIALS);

            // Initialisation de l'objet de stockage
            Path semaphoreFilePathObj = Paths.get(targetDir.toString(), fileInfos.getStorageHashedFileName() + ".go");
            return fileWriteData(semaphoreFilePathObj, BSFileType.SPECIALS, fileInfos.toSpecialFileData(dataFilePathObj.toString()).getBytes());
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

    public void deleteSemaphoreFile(BSFileInformation fileInfos) throws IOException {
        Path semaphoreFilePathObj = Paths.get(nasSpecialRootdir, fileInfos.getLogicalFolder(), fileInfos.getStorageHashedFileName() + ".go");
        if (Files.exists(semaphoreFilePathObj)) {
            Files.delete(semaphoreFilePathObj);
        }
    }

    /**
     * Suppression physique des fichiers associé aux Informations données
     * @param fileInfos informations concernant le fichier à supprimer
     * @throws ServerError500Exception
     */
    public void deleteFile(BSFileInformation fileInfos) throws ServerError500Exception {
        try {
            if (fileInfos.getIsSpecial()) {
                deleteSemaphoreFile(fileInfos);
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
            log.error("Une erreur est survenue durant la suppression du fichier", ioExceptionObj);
            throw new ServerError500Exception("Une erreur est survenue durant la suppression du fichier", ioExceptionObj);
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
    public InputStreamResource getFileContentAsInputStream(BSFileInformation fileInfos) throws NotFound404Exception, ServerError500Exception, VirusFound409Exception {
        try {
            Path filePathObj = getDataFilePathObj(fileInfos);

            if (Files.exists(filePathObj)) {
                try {
                    antivirusScan(Files.newInputStream(filePathObj));
                } catch (VirusFound409Exception e) {
                    fileInfos.setStatus(BSFile.Status.VIRUS_INFECTED.value());
                    fileInfos.setStatusLinkedData(e.getMessage());
                    bsfiRepository.save(fileInfos);
                    throw e;
                }
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
