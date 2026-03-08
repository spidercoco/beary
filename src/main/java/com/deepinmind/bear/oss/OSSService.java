package com.deepinmind.bear.oss;

import com.aliyun.oss.*;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import com.aliyun.oss.common.comm.SignVersion;
import com.aliyun.oss.model.AppendObjectRequest;
import com.aliyun.oss.model.AppendObjectResult;
import com.aliyun.oss.model.GetObjectRequest;
import com.aliyun.oss.model.ListObjectsRequest;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.OSSObjectSummary;
import com.aliyun.oss.model.ObjectListing;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Date;
import java.util.List;

@Component
public class OSSService {

    @Value("${accessKeyId}")
    private String accessKeyId;

    @Value("${accessKeySecret}")
    private String accessKeySecret;

    @Value("${namespace}")
    private String namespace;

    @Value("${bucketName}")
    private String bucketName;

    // 设置OSS地域和Endpoint
    String region = "cn-beijing";
    String endpoint = "oss-cn-beijing.aliyuncs.com";

    // 显式声明使用V4签名算法
    // clientBuilderConfiguration.setSignatureVersion(SignVersion.V4);

    // 初始化OSS客户端
    private OSS ossClient;


    public void appendObject(String objectName, String content) {
        try {
            String fullObjectName = namespace + "/" + objectName;
            
            // Check if object exists and determine position
            long position = 0L;
            if (ossClient.doesObjectExist(bucketName, fullObjectName)) {
                // Object exists, get its size as the append position
                ObjectMetadata metadata = ossClient.getObjectMetadata(bucketName, fullObjectName);
                position = metadata.getContentLength();
            }
            
            ObjectMetadata meta = new ObjectMetadata();
            meta.setContentType("text/plain");
            AppendObjectRequest appendObjectRequest = new AppendObjectRequest(bucketName, fullObjectName,
                    new ByteArrayInputStream(content.getBytes()), meta);
            appendObjectRequest.setPosition(position);
            ossClient.appendObject(appendObjectRequest);
        } catch (OSSException oe) {
            System.out.println("Caught an OSSException, which means your request made it to OSS, "
                    + "but was rejected with an error response for some reason.");
            System.out.println("Error Message:" + oe.getErrorMessage());
            System.out.println("Error Code:" + oe.getErrorCode());
            System.out.println("Request ID:" + oe.getRequestId());
            System.out.println("Host ID:" + oe.getHostId());
        } catch (ClientException ce) {
            System.out.println("Caught an ClientException, which means the client encountered "
                    + "a serious internal problem while trying to communicate with OSS, "
                    + "such as not being able to access the network.");
            System.out.println("Error Message:" + ce.getMessage());
        }
    }

    @PostConstruct
    public void init() {
        // 这里 accessKeyId / accessKeySecret 已经由 @Value 注入完成
        DefaultCredentialProvider provider = new DefaultCredentialProvider(accessKeyId, accessKeySecret);
        ClientBuilderConfiguration clientBuilderConfiguration = new ClientBuilderConfiguration();

        this.ossClient = OSSClientBuilder.create()
                .credentialsProvider(provider)
                .clientConfiguration(clientBuilderConfiguration)
                .region(region)
                .endpoint(endpoint)
                .build();
    }

    @PreDestroy
    public void destroy() {
        if (ossClient != null) {
            ossClient.shutdown();
        }
    }

    /**
     * Upload a string content to Aliyun OSS
     * 
     * @param objectKey  The object key (file path in OSS)
     * @param content    The string content to upload
     * @return The OSS URL of the uploaded file
     * @throws IOException If upload fails
     */
    public String uploadFile(String objectKey, String content) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        try (InputStream inputStream = new ByteArrayInputStream(bytes)) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType("text/plain");
            metadata.setContentLength(bytes.length);

            PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, objectKey, inputStream, metadata);
            ossClient.putObject(putObjectRequest);

            // Generate the OSS URL
            String url = "https://" + bucketName + "." + endpoint + "/" + objectKey;
            return url;
        } catch (OSSException oe) {
            throw new IOException("OSS Exception: " + oe.getErrorMessage(), oe);
        } catch (ClientException ce) {
            throw new IOException("Client Exception: " + ce.getMessage(), ce);
        }
    }

    /**
     * Upload a file to Aliyun OSS
     * 
     * @param bucketName The OSS bucket name
     * @param objectKey  The object key (file path in OSS)
     * @param file       The multipart file to upload
     * @return The OSS URL of the uploaded file
     * @throws IOException If file reading fails
     */
    public String uploadFile(String objectKey, MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            // Create ObjectMetadata to set content type
            ObjectMetadata metadata = new ObjectMetadata();
            if (file.getContentType() != null) {
                metadata.setContentType(file.getContentType());
            }
            metadata.setContentLength(file.getSize());

            PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, objectKey, inputStream, metadata);
            ossClient.putObject(putObjectRequest);

            // Generate the OSS URL
            String url = "https://" + bucketName + "." + endpoint + "/" + objectKey;
            return url;
        } catch (OSSException oe) {
            throw new IOException("OSS Exception: " + oe.getErrorMessage(), oe);
        } catch (ClientException ce) {
            throw new IOException("Client Exception: " + ce.getMessage(), ce);
        }
    }

    /**
     * 生成带签名的临时访问链接 (一个月有效期)
     * @param objectKey
     * @return
     */
    public String getPresignedUrl(String objectKey) {
        // 设置过期时间：一个月（30天）
        Date expiration = new Date(new Date().getTime() + 30L * 24 * 3600 * 1000);
        URL url = ossClient.generatePresignedUrl(bucketName, objectKey, expiration);
        return url.toString();
    }

    /**
     * Download a file from OSS and return as byte array
     */
    public byte[] downloadFile(String objectKey) throws IOException {
        try {
            if (!ossClient.doesObjectExist(bucketName, objectKey)) {
                return null;
            }
            OSSObject ossObject = ossClient.getObject(new GetObjectRequest(bucketName, objectKey));
            try (InputStream is = ossObject.getObjectContent()) {
                return is.readAllBytes();
            }
        } catch (Exception e) {
            throw new IOException("Failed to download from OSS: " + objectKey, e);
        }
    }

    /**
     * Download a file from OSS to a local temporary file
     * 
     * @param bucketName    The OSS bucket name
     * @param objectKey     The object key (file path in OSS)
     * @param localFilePath The local file path to save the downloaded file
     * @throws IOException If download fails
     */
    public void downloadFile(String bucketName, String objectKey, String localFilePath) throws IOException {
        try {
            // Check if object exists
            if (!ossClient.doesObjectExist(bucketName, objectKey)) {
                throw new IOException("文件不存在于OSS: " + objectKey);
            }

            // Download the object
            GetObjectRequest getObjectRequest = new GetObjectRequest(bucketName, objectKey);
            OSSObject ossObject = ossClient.getObject(getObjectRequest);

            // Save to local file
            try (InputStream inputStream = ossObject.getObjectContent();
                    java.io.FileOutputStream outputStream = new java.io.FileOutputStream(localFilePath)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
        } catch (OSSException oe) {
            throw new IOException("OSS Exception: " + oe.getErrorMessage(), oe);
        } catch (ClientException ce) {
            throw new IOException("Client Exception: " + ce.getMessage(), ce);
        }
    }

    /**
     * Check if an object exists in OSS
     * 
     * @param bucketName The OSS bucket name
     * @param objectKey  The object key (file path in OSS)
     * @return true if object exists, false otherwise
     */
    public boolean doesObjectExist(String bucketName, String objectKey) {
        try {
            return ossClient.doesObjectExist(bucketName, objectKey);
        } catch (Exception e) {
            return false;
        }
    }

    public void listFiles(String bucketName) {
        try {
            String nextMarker = null;
            ObjectListing objectListing;

            Path path = Paths.get("audiolist.txt");

            // 第一次：清空文件（只做一次）
            Files.write(path, new byte[0], StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            do {
                objectListing = ossClient
                        .listObjects(new ListObjectsRequest(bucketName).withMarker(nextMarker).withMaxKeys(200));

                List<OSSObjectSummary> sums = objectListing.getObjectSummaries();
                for (OSSObjectSummary s : sums) {
                    System.out.println("\t" + s.getKey());
                    Files.write(
                            path,
                            s.getKey().getBytes(),
                            StandardOpenOption.CREATE,
                            StandardOpenOption.APPEND);
                    Files.write(
                            path,
                            System.lineSeparator().getBytes(),
                            StandardOpenOption.CREATE,
                            StandardOpenOption.APPEND);
                }

                nextMarker = objectListing.getNextMarker();

            } while (objectListing.isTruncated());
        } catch (OSSException oe) {
            oe.printStackTrace();
            System.out.println("Caught an OSSException, which means your request made it to OSS, "
                    + "but was rejected with an error response for some reason.");
            System.out.println("Error Message:" + oe.getErrorMessage());
            System.out.println("Error Code:" + oe.getErrorCode());
            System.out.println("Request ID:" + oe.getRequestId());
            System.out.println("Host ID:" + oe.getHostId());
        } catch (ClientException ce) {
            System.out.println("Caught an ClientException, which means the client encountered "
                    + "a serious internal problem while trying to communicate with OSS, "
                    + "such as not being able to access the network.");
            System.out.println("Error Message:" + ce.getMessage());
            ce.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 列出可供打印的文件
     * 
     * @param bucketName
     */
    public String listPrintFiles() {
        StringBuilder content = new StringBuilder();

        try {
            String nextMarker = null;
            ObjectListing objectListing;

            objectListing = ossClient
                    .listObjects(new ListObjectsRequest("oss-filelist").withMarker(nextMarker).withMaxKeys(20));

            List<OSSObjectSummary> sums = objectListing.getObjectSummaries();
            for (OSSObjectSummary s : sums) {
                System.out.println("\t" + s.getKey());
                content.append("  - ").append(s.getKey()).append("\n");
            }

        } catch (OSSException oe) {
            oe.printStackTrace();
            System.out.println("Caught an OSSException, which means your request made it to OSS, "
                    + "but was rejected with an error response for some reason.");
            System.out.println("Error Message:" + oe.getErrorMessage());
            System.out.println("Error Code:" + oe.getErrorCode());
            System.out.println("Request ID:" + oe.getRequestId());
            System.out.println("Host ID:" + oe.getHostId());
        } catch (ClientException ce) {
            System.out.println("Caught an ClientException, which means the client encountered "
                    + "a serious internal problem while trying to communicate with OSS, "
                    + "such as not being able to access the network.");
            System.out.println("Error Message:" + ce.getMessage());
            ce.printStackTrace();
        }

        return content.toString();

    }
}
