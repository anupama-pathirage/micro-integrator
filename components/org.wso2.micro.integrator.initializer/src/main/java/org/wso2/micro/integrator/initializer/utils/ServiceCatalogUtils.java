/*
 *  Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.micro.integrator.initializer.utils;

import org.apache.axiom.om.OMElement;
import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.commons.resolvers.SystemResolver;
import org.apache.synapse.config.SynapseConfigUtils;
import org.wso2.carbon.securevault.SecretCallbackHandlerService;
import org.wso2.config.mapper.ConfigParser;
import org.wso2.micro.application.deployer.AppDeployerUtils;
import org.wso2.micro.application.deployer.CarbonApplication;
import org.wso2.micro.core.util.CarbonException;
import org.wso2.micro.core.util.StringUtils;
import org.wso2.micro.integrator.initializer.deployment.application.deployer.CappDeployer;
import org.wso2.securevault.SecretResolver;
import org.wso2.securevault.SecretResolverFactory;
import org.wso2.securevault.commons.MiscellaneousUtil;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.wso2.micro.integrator.initializer.utils.Constants.*;

/**
 * Util class for service catalog feature.
 */
public class ServiceCatalogUtils {

    private static final Log log = LogFactory.getLog(ServiceCatalogUtils.class);
    private static SecretResolver secretResolver;

    /**
     * Update the service url by injecting env variables.
     *
     * @param currentUrl current url.
     * @return updated url.
     */
    private static String updateServiceUrl(String currentUrl) {
        /*
            Supported formats
            https://{host}:{port}/api1
            https://{url}/api1
        */
        SystemResolver resolver = new SystemResolver();
        if (currentUrl.contains(HOST) && currentUrl.contains(PORT)) {
            resolver.setVariable(MI_HOST);
            String host = resolver.resolve();
            resolver.setVariable(MI_PORT);
            String port = resolver.resolve();
            currentUrl = currentUrl.replace(HOST, host).replace(PORT, port);
        } else if (currentUrl.contains(URL)) {
            resolver.setVariable(MI_URL);
            String url = resolver.resolve();
            currentUrl = currentUrl.replace(MI_URL, url);
        }
        return currentUrl;
    }

    /**
     * Create the zip file by wrapping all the metadata files.
     *
     * @param zos                 ZipOutputStream of the output ZIP file.
     * @param fileToZip           File to be added to the ZIP file.
     * @param parentDirectoryName Name of the parent directory.
     * @throws IOException Error occurred while creating the ZIP file.
     */
    public static void addDirToZipArchive(ZipOutputStream zos, File fileToZip, String parentDirectoryName)
            throws IOException {
        if (fileToZip == null || !fileToZip.exists()) {
            return;
        }

        String zipEntryName = fileToZip.getName();
        if (parentDirectoryName != null && !parentDirectoryName.isEmpty()) {
            zipEntryName = parentDirectoryName + File.separator + fileToZip.getName();
        }

        if (fileToZip.isDirectory()) {
            for (File file : Objects.requireNonNull(fileToZip.listFiles())) {
                addDirToZipArchive(zos, file, zipEntryName);
            }
        } else {
            byte[] buffer = new byte[1024];
            FileInputStream fis = new FileInputStream(fileToZip);
            zos.putNextEntry(new ZipEntry(zipEntryName));
            int length;
            while ((length = fis.read(buffer)) > 0) {
                zos.write(buffer, 0, length);
            }
            zos.closeEntry();
            fis.close();
        }
    }

    /**
     * Update the serviceUrl of the given metadata file.
     *
     * @param yamlFile metadata yaml file.
     * @throws IOException Error occurred while updating the metadata file.
     */
    public static void updateMetadataWithServiceUrl(File yamlFile) throws IOException {
        Yaml yaml = new Yaml();
        Map<String, Object> obj =
                (Map<String, Object>) yaml.load(new FileInputStream(yamlFile));
        String currentServiceUrl = (String) obj.get(SERVICE_URL);
        obj.put(SERVICE_URL, updateServiceUrl(currentServiceUrl));

        // Additional configurations
        DumperOptions options = new DumperOptions();
        options.setIndent(2);
        options.setPrettyFlow(true);
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);

        Yaml output = new Yaml(options);
        String updatedYaml = output.dump(obj);

        DataOutputStream outputStream = new DataOutputStream(new FileOutputStream(yamlFile, false));
        outputStream.write(updatedYaml.getBytes());
        outputStream.close();
    }

    /**
     * Publish a given ZIP file to APIM service catalog endpoint. Retry if failed.
     *
     * @param secretCallbackHandlerService secret callback handler reference.
     * @param attachmentFilePath           path of the ZIP file to be updated.
     */
    public static void publishToAPIM(SecretCallbackHandlerService secretCallbackHandlerService,
                                     String attachmentFilePath) {
        Map<String, String> apimConfigs = readConfiguration(secretCallbackHandlerService);
        int responseCode = uploadZip(apimConfigs, attachmentFilePath);
        if (responseCode == -1) return; // error occurred while uploading to service catalog
        switch (responseCode) {
            case HttpURLConnection.HTTP_OK:
                log.info("Successfully updated the service catalog");
                break;
            case HttpURLConnection.HTTP_GATEWAY_TIMEOUT:
            case HttpURLConnection.HTTP_UNAVAILABLE:
                if (log.isDebugEnabled()) {
                    log.debug("APIM responds with the status code : " + responseCode + " start " +
                            "retrying");
                }
                int retryCount = RETRY_COUNT;
                for (int i = 0; i < retryCount; i++) {
                    try {
                        log.info("Retrying to connect with APIM. Remaining retry count : " + (retryCount - i));
                        Thread.sleep(INTERVAL_BETWEEN_RETRIES);
                        responseCode = uploadZip(apimConfigs, attachmentFilePath);
                        if (responseCode == HttpURLConnection.HTTP_OK) {
                            break;
                        }
                    } catch (InterruptedException e) {
                        log.error("Service catalog thread interrupted", e);
                    }
                }
                log.error("Could not connect with APIM after " + retryCount + " retries.");
                break;
            case UNAUTHENTICATED:
                log.error("Unauthenticated, please verify the username and password provided for service catalog");
                break;
            default:
                log.error("Unknown response code received from the service catalog endpoint: " + responseCode);
                break;
        }
    }

    /**
     * Upload the given ZIP file to the APIM service catalog endpoint.
     *
     * @param apimConfigs        map containing APIM configurations.
     * @param attachmentFilePath location of the zip file that needs to be uploaded.
     * @return status code returned from APIM, -1 if error occurred.
     */
    private static int uploadZip(Map<String, String> apimConfigs, String attachmentFilePath) {
        try {
            String APIMHost = apimConfigs.get(APIM_HOST);
            String encodeBytes =
                    Base64.getEncoder().encodeToString(
                            (apimConfigs.get(USER_NAME) + ":" + apimConfigs.get(PASSWORD)).getBytes());

            File binaryFile = new File(attachmentFilePath);
            String boundary = "------------------------" +
                    Long.toHexString(System.currentTimeMillis()); // Generate some unique random value.
            String CRLF = "\r\n"; // Line separator required by multipart/form-data.
            HttpsURLConnection connection = (HttpsURLConnection) new URL(APIMHost).openConnection();
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            connection.addRequestProperty("Accept", "application/json");
            connection.addRequestProperty("Authorization", "Basic " + encodeBytes);
            connection.setHostnameVerifier(getHostnameVerifier());

            OutputStream output = connection.getOutputStream();

            // Send binary file - part
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8), true);
            writer.append("--").append(boundary).append(CRLF);
            writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(
                    binaryFile.getName()).append("\"").append(CRLF);
            writer.append("Content-Type: application/octet-stream").append(CRLF);
            writer.append(CRLF).flush();

            // File data
            Files.copy(binaryFile.toPath(), output);
            output.flush();

            // End of multipart/form-data.
            writer.append(CRLF).append("--").append(boundary).append("--").flush();

            return connection.getResponseCode();
        } catch (MalformedURLException e) {
            log.error("Service catalog url is malformed, please check the configured APIM host", e);
        } catch (IOException e) {
            log.error("Error occurred while uploading metadata to service catalog endpoint", e);
        }
        return -1;
    }

    /**
     * Read APIM host configurations from deployment.toml file.
     *
     * @param secretCallbackHandlerService secret callback handler reference.
     * @return map of resolved values.
     */
    private static Map<String, String> readConfiguration(SecretCallbackHandlerService secretCallbackHandlerService) {
        Map<String, String> configMap = new HashMap<>();
        Map<String, String> catalogProperties =
                (Map<String, String>) ((ArrayList) ConfigParser.getParsedConfigs().get(
                        SERVICE_CATALOG_CONFIG)).get(0);

        String apimHost = catalogProperties.get(APIM_HOST);
        String endpoint;
        if (apimHost.endsWith("/")) {
            endpoint = apimHost + SERVICE_CATALOG_ENDPOINT;
        } else {
            endpoint = apimHost + "/" + SERVICE_CATALOG_ENDPOINT;
        }

        String userName = catalogProperties.get(USER_NAME);
        String password = catalogProperties.get(PASSWORD);
        if (secretResolver == null) {
            secretResolver = SecretResolverFactory.create((OMElement) null, false);
        }
        if (!secretResolver.isInitialized()) {
            secretResolver.init(secretCallbackHandlerService.getSecretCallbackHandler());
        }
        String alias = MiscellaneousUtil.getProtectedToken(userName);
        if (!StringUtils.isEmpty(alias)) {
            userName = secretResolver.resolve(alias);
        }
        alias = MiscellaneousUtil.getProtectedToken(password);
        if (!StringUtils.isEmpty(alias)) {
            password = secretResolver.resolve(alias);
        }

        configMap.put(APIM_HOST, endpoint);
        configMap.put(USER_NAME, userName);
        configMap.put(PASSWORD, password);
        return configMap;
    }

    /**
     * Process metadata folder and move to temporary location.
     *
     * @param tempDir            temporary directory to put metadata.
     * @param metadataFolder     metadata folder inside CAPP.
     * @param metadataYamlFolder YAML folder inside CAPP.
     * @throws IOException error occurred while moving files.
     */
    public static void processMetadata(File tempDir, File metadataFolder, File metadataYamlFolder) throws IOException {
        String metaFileName = metadataYamlFolder.getName();
        String APIName = metaFileName.substring(0, metaFileName.indexOf(METADATA_FOLDER_STRING));
        String APIVersion =
                metaFileName.substring(metaFileName.lastIndexOf(METADATA_FOLDER_STRING) +
                        METADATA_FOLDER_STRING.length());
        // Create new folder in temp directory for this API
        File newMetaFile = new File(tempDir, APIName + "_v" + APIVersion);
        newMetaFile.mkdir();
        File newYamlFile = new File(newMetaFile, METADATA_FILE_NAME);
        File metadataYamlFile =
                new File(metadataYamlFolder, APIName + METADATA_FILE_STRING + APIVersion + YAML_FILE_EXTENSION);
        // Copy metadata yaml file to the temp location.
        FileUtils.copyFile(metadataYamlFile, newYamlFile);

        // Edit metadata yaml and add host details
        updateMetadataWithServiceUrl(newYamlFile);

        File swaggerFolder = new File(metadataFolder, APIName + SWAGGER_FOLDER_STRING + APIVersion);
        File swaggerFile =
                new File(swaggerFolder, APIName + SWAGGER_FILE_STRING + APIVersion + YAML_FILE_EXTENSION);
        File newSwaggerFile = new File(newMetaFile, SWAGGER_FILE_NAME);
        // Copy swagger yaml file to the temp location.
        FileUtils.copyFile(swaggerFile, newSwaggerFile);
    }

    /**
     * Create the final ZIP file wrapping all the metadata files.
     *
     * @param tempDir      temporary directory where metadata files are copied.
     * @param cappUnzipDir location where the CAPPs are unzipped.
     * @return result of zip creation process. ( successful / failed )
     */
    public static boolean createZipFile(File tempDir, String cappUnzipDir) {
        String zipPath = cappUnzipDir + File.separator + ZIP_FOLDER_NAME;
        FileOutputStream fos;
        try {
            fos = new FileOutputStream(zipPath);
        } catch (FileNotFoundException e) {
            log.error("Invalid target ZIP file location", e);
            return false;
        }
        ZipOutputStream zos = new ZipOutputStream(fos);
        File[] metadataFiles = tempDir.listFiles();
        if (metadataFiles != null && metadataFiles.length > 0) {
            try {
                for (File metaFile : Objects.requireNonNull(tempDir.listFiles())) {
                    addDirToZipArchive(zos, metaFile, null);
                }
                zos.flush();
                fos.flush();
                zos.close();
                fos.close();
            } catch (IOException ex) {
                log.error("Error occurred while archiving the metadata files", ex);
                return false;
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Didn't find any meta-information in CAPPs");
            }
            return false;
        }
        return true;
    }

    /**
     * Disabling the hostname verification.
     *
     * @return true for all the host names.
     */
    private static HostnameVerifier getHostnameVerifier() {
        return (s, sslSession) -> true;
    }

    /**
     * Check pre-conditions before stating the service-catalog uploading process.
     *
     * @return pre-condition are matched / not matched.
     */
    public static boolean checkPreConditions() {
        if (log.isDebugEnabled()) {
            log.debug("Start service-catalog uploading process");
        }
        // Check for faulty CAPPs. If atleast one CAPP is fault MI is not ready - readiness probe.
        ArrayList<String> faultyCapps = new ArrayList<>(CappDeployer.getFaultyCapps());
        if (!faultyCapps.isEmpty()) {
            log.info("Faulty CAPPs detected - aborting the service-catalog uploader");
            return false;
        }
        // Skip if no CAPPs are deployed
        ArrayList<CarbonApplication> deployedCapps = new ArrayList<>(CappDeployer.getCarbonApps());
        if (deployedCapps.isEmpty()) {
            log.info("Cannot find carbon applications - aborting the service-catalog uploader");
            return false;
        }
        // Skip if no APIs are deployed
        Collection APITable =
                SynapseConfigUtils.getSynapseConfiguration(
                        org.wso2.micro.core.Constants.SUPER_TENANT_DOMAIN_NAME).getAPIs();
        log.info("Cannot find APIs - aborting the service-catalog uploader");
        return !APITable.isEmpty();
    }

    /**
     * Extract CAPPs and put metadata files in the temporary folder.
     *
     * @param targetDir    temporary folder location.
     * @param repoLocation location of the deployment folder of MI.
     */
    public static void extractMetadataFromCAPPs(File targetDir, String repoLocation) {
        FilenameFilter cappFilter = (f, name) -> name.endsWith(".car");
        FilenameFilter metaFilter = (f, name) -> name.contains(METADATA_FOLDER_STRING);

        /*
         * Sample folder structure of metadata inside the new Carbon Application
         * metadata
         *  - testApi_metadata_1.0.0
         *    - testApi_metadata-1.0.0.yaml
         *    - artifact.xml
         *  - testApi_swagger_1.0.0
         *    - testApi_swagger-1.0.0.yaml
         *    - artifact.xml
         */

        File cappFolder = new File(repoLocation, CAPP_FOLDER_NAME);
        File[] files = cappFolder.listFiles(cappFilter);
        assert files != null;
        for (File file : files) {
            try {
                // Extract the CAPP and get extracted location.
                String tempExtractedDirPath = AppDeployerUtils.extractCarbonApp(file.getPath());
                File metadataFolder = new File(tempExtractedDirPath, METADATA_FOLDER_NAME);
                // does not have a metadata folder -> old CAPP format.
                if (metadataFolder.exists()) {
                    File[] metadataFolders = metadataFolder.listFiles(metaFilter);
                    assert metadataFolders != null;
                    for (File metadataYamlFolder : metadataFolders) {
                        processMetadata(targetDir, metadataFolder, metadataYamlFolder);
                    }
                }
            } catch (IOException e) {
                log.error("Error occurred while processing the metadata files", e);
            } catch (CarbonException e) {
                log.error("Error occurred when extracting the carbon application", e);
            }
        }
    }
}
