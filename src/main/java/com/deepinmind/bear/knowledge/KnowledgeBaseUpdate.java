package com.deepinmind.bear.knowledge;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


// 示例代码仅供参考，请勿在生产环境中直接使用
import com.aliyun.bailian20231229.models.*;
import com.aliyun.teautil.models.RuntimeOptions;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.*;

@Component
@Data
public class KnowledgeBaseUpdate {

    @Value("${accessKeyId}")
    private String accessKeyId;

    @Value("${accessKeySecret}")
    private String accessKeySecret;

    @Value("${workspaceId}")
    private String workspaceId;
    /**
     * 检查并提示设置必要的环境变量。
     *
     * @return true 如果所有必需的环境变量都已设置，否则 false
     */
    public boolean checkEnvironmentVariables() {
        Map<String, String> requiredVars = new HashMap<>();
        requiredVars.put("ALIBABA_CLOUD_ACCESS_KEY_ID", accessKeyId);
        requiredVars.put("ALIBABA_CLOUD_ACCESS_KEY_SECRET", accessKeySecret);
        requiredVars.put("WORKSPACE_ID", workspaceId);

        List<String> missingVars = new ArrayList<>();
        for (Map.Entry<String, String> entry : requiredVars.entrySet()) {
            String value = System.getenv(entry.getKey());
            if (value == null || value.isEmpty()) {
                missingVars.add(entry.getKey());
                System.out.println("错误：请设置 " + entry.getKey() + " 环境变量 (" + entry.getValue() + ")");
            }
        }

        return missingVars.isEmpty();
    }

    /**
     * 创建并配置客户端（Client）
     *
     * @return 配置好的客户端（Client）
     */
    public  com.aliyun.bailian20231229.Client createClient() throws Exception {
        com.aliyun.teaopenapi.models.Config config = new com.aliyun.teaopenapi.models.Config()
                .setAccessKeyId(accessKeyId)
                .setAccessKeySecret(accessKeySecret);
        // 下方接入地址以公有云的公网接入地址为例，可按需更换接入地址。
        config.endpoint = "bailian.cn-beijing.aliyuncs.com";
        return new com.aliyun.bailian20231229.Client(config);
    }

    /**
     * 计算文件的MD5值
     *
     * @param filePath 文件本地路径
     * @return 文件的MD5值
     */
    public  String calculateMD5(String filePath) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (FileInputStream fis = new FileInputStream(filePath)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    /**
     * 获取文件大小（以字节为单位）
     *
     * @param filePath 文件本地路径
     * @return 文件大小（以字节为单位）
     */
    public  String getFileSize(String filePath) {
        File file = new File(filePath);
        long fileSize = file.length();
        return String.valueOf(fileSize);
    }

    /**
     * 申请文件上传租约。
     *
     * @param client      客户端对象
     * @param categoryId  类目ID
     * @param fileName    文件名称
     * @param fileMd5     文件的MD5值
     * @param fileSize    文件大小（以字节为单位）
     * @param workspaceId 业务空间ID
     * @return 阿里云百炼服务的响应对象
     */
    public  ApplyFileUploadLeaseResponse applyLease(com.aliyun.bailian20231229.Client client, String categoryId, String fileName, String fileMd5, String fileSize, String workspaceId) throws Exception {
        Map<String, String> headers = new HashMap<>();
        com.aliyun.bailian20231229.models.ApplyFileUploadLeaseRequest applyFileUploadLeaseRequest = new com.aliyun.bailian20231229.models.ApplyFileUploadLeaseRequest();
        applyFileUploadLeaseRequest.setFileName(fileName);
        applyFileUploadLeaseRequest.setMd5(fileMd5);
        applyFileUploadLeaseRequest.setSizeInBytes(fileSize);
        com.aliyun.teautil.models.RuntimeOptions runtime = new com.aliyun.teautil.models.RuntimeOptions();
        ApplyFileUploadLeaseResponse applyFileUploadLeaseResponse = null;
        applyFileUploadLeaseResponse = client.applyFileUploadLeaseWithOptions(categoryId, workspaceId, applyFileUploadLeaseRequest, headers, runtime);
        return applyFileUploadLeaseResponse;
    }

    /**
     * 上传文件到临时存储。
     *
     * @param preSignedUrl 上传租约中的 URL
     * @param headers      上传请求的头部
     * @param filePath     文件本地路径
     * @throws Exception 如果上传过程中发生错误
     */
    public  void uploadFile(String preSignedUrl, Map<String, String> headers, String filePath) throws Exception {
        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("文件不存在或不是普通文件: " + filePath);
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            URL url = new URL(preSignedUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("PUT");
            conn.setDoOutput(true);

            // 设置上传请求头
            conn.setRequestProperty("X-bailian-extra", headers.get("X-bailian-extra"));
            conn.setRequestProperty("Content-Type", headers.get("Content-Type"));

            // 分块读取并上传文件
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                conn.getOutputStream().write(buffer, 0, bytesRead);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new RuntimeException("上传失败: " + responseCode);
            }
        }
    }

    /**
     * 将文件添加到类目中。
     *
     * @param client      客户端对象
     * @param leaseId     租约ID
     * @param parser      用于文件的解析器
     * @param categoryId  类目ID
     * @param workspaceId 业务空间ID
     * @return 阿里云百炼服务的响应对象
     */
    public  AddFileResponse addFile(com.aliyun.bailian20231229.Client client, String leaseId, String parser, String categoryId, String workspaceId) throws Exception {
        Map<String, String> headers = new HashMap<>();
        com.aliyun.bailian20231229.models.AddFileRequest addFileRequest = new com.aliyun.bailian20231229.models.AddFileRequest();
        addFileRequest.setLeaseId(leaseId);
        addFileRequest.setParser(parser);
        addFileRequest.setCategoryId(categoryId);
        com.aliyun.teautil.models.RuntimeOptions runtime = new com.aliyun.teautil.models.RuntimeOptions();
        return client.addFileWithOptions(workspaceId, addFileRequest, headers, runtime);
    }

    /**
     * 查询文件的基本信息。
     *
     * @param client      客户端对象
     * @param workspaceId 业务空间ID
     * @param fileId      文件ID
     * @return 阿里云百炼服务的响应对象
     */
    public  DescribeFileResponse describeFile(com.aliyun.bailian20231229.Client client, String workspaceId, String fileId) throws Exception {
        Map<String, String> headers = new HashMap<>();
        com.aliyun.teautil.models.RuntimeOptions runtime = new com.aliyun.teautil.models.RuntimeOptions();
        return client.describeFileWithOptions(workspaceId, fileId, headers, runtime);
    }

    /**
     * 向一个文档搜索类知识库追加导入已解析的文件
     *
     * @param client      客户端（Client）
     * @param workspaceId 业务空间ID
     * @param indexId     知识库ID
     * @param fileId      文件ID
     * @param sourceType  数据类型
     * @return 阿里云百炼服务的响应
     */
    public  SubmitIndexAddDocumentsJobResponse submitIndexAddDocumentsJob(com.aliyun.bailian20231229.Client client, String workspaceId, String indexId, String fileId, String sourceType) throws Exception {
        Map<String, String> headers = new HashMap<>();
        SubmitIndexAddDocumentsJobRequest submitIndexAddDocumentsJobRequest = new SubmitIndexAddDocumentsJobRequest();
        submitIndexAddDocumentsJobRequest.setIndexId(indexId);
        submitIndexAddDocumentsJobRequest.setDocumentIds(Collections.singletonList(fileId));
        submitIndexAddDocumentsJobRequest.setSourceType(sourceType);
        RuntimeOptions runtime = new RuntimeOptions();
        return client.submitIndexAddDocumentsJobWithOptions(workspaceId, submitIndexAddDocumentsJobRequest, headers, runtime);
    }

    /**
     * 查询索引任务状态。
     *
     * @param client      客户端对象
     * @param workspaceId 业务空间ID
     * @param jobId       任务ID
     * @param indexId     知识库ID
     * @return 阿里云百炼服务的响应对象
     */
    public  GetIndexJobStatusResponse getIndexJobStatus(com.aliyun.bailian20231229.Client client, String workspaceId, String jobId, String indexId) throws Exception {
        Map<String, String> headers = new HashMap<>();
        com.aliyun.bailian20231229.models.GetIndexJobStatusRequest getIndexJobStatusRequest = new com.aliyun.bailian20231229.models.GetIndexJobStatusRequest();
        getIndexJobStatusRequest.setIndexId(indexId);
        getIndexJobStatusRequest.setJobId(jobId);
        com.aliyun.teautil.models.RuntimeOptions runtime = new com.aliyun.teautil.models.RuntimeOptions();
        GetIndexJobStatusResponse getIndexJobStatusResponse = null;
        getIndexJobStatusResponse = client.getIndexJobStatusWithOptions(workspaceId, getIndexJobStatusRequest, headers, runtime);
        return getIndexJobStatusResponse;
    }

    /**
     * 从指定的文档搜索类知识库中永久删除一个或多个文件
     *
     * @param client      客户端（Client）
     * @param workspaceId 业务空间ID
     * @param indexId     知识库ID
     * @param fileId      文件ID
     * @return 阿里云百炼服务的响应
     */
    public  DeleteIndexDocumentResponse deleteIndexDocument(com.aliyun.bailian20231229.Client client, String workspaceId, String indexId, String fileId) throws Exception {
        Map<String, String> headers = new HashMap<>();
        DeleteIndexDocumentRequest deleteIndexDocumentRequest = new DeleteIndexDocumentRequest();
        deleteIndexDocumentRequest.setIndexId(indexId);
        deleteIndexDocumentRequest.setDocumentIds(Collections.singletonList(fileId));
        com.aliyun.teautil.models.RuntimeOptions runtime = new com.aliyun.teautil.models.RuntimeOptions();
        return client.deleteIndexDocumentWithOptions(workspaceId, deleteIndexDocumentRequest, headers, runtime);
    }

    /**
     * 使用阿里云百炼服务更新知识库
     *
     * @param filePath    文件（更新后的）的实际本地路径
     * @param indexId     需要更新的知识库ID
     * @param oldFileId   需要更新的文件的FileID
     * @return 如果成功，返回知识库ID；否则返回 null
     */
    public  String updateKnowledgeBase(String filePath, String indexId, String oldFileId) {
        // 设置默认值
        String categoryId = "default";
        String parser = "DASHSCOPE_DOCMIND";
        String sourceType = "DATA_CENTER_FILE";
        try {
            // 步骤1：初始化客户端（Client）
            System.out.println("步骤1：创建Client");
            com.aliyun.bailian20231229.Client client = createClient();

            // 步骤2：准备文件信息（更新后的文件）
            System.out.println("步骤2：准备文件信息");
            String fileName = Paths.get(filePath).getFileName().toString();
            String fileMd5 = calculateMD5(filePath);
            String fileSize = getFileSize(filePath);

            // 步骤3：申请上传租约
            System.out.println("步骤3：向阿里云百炼申请上传租约");
            ApplyFileUploadLeaseResponse leaseResponse = applyLease(client, categoryId, fileName, fileMd5, fileSize, workspaceId);
            String leaseId = leaseResponse.getBody().getData().getFileUploadLeaseId();
            String uploadUrl = leaseResponse.getBody().getData().getParam().getUrl();
            Object uploadHeaders = leaseResponse.getBody().getData().getParam().getHeaders();

            // 步骤4：上传文件到临时存储
            System.out.println("步骤4：上传文件到临时存储");
            // 请自行安装jackson-databind
            // 将上一步的uploadHeaders转换为Map(Key-Value形式)
            ObjectMapper mapper = new ObjectMapper();
            Map<String, String> uploadHeadersMap = (Map<String, String>) mapper.readValue(mapper.writeValueAsString(uploadHeaders), Map.class);
            uploadFile(uploadUrl, uploadHeadersMap, filePath);

            // 步骤5：添加文件到类目中
            System.out.println("步骤5：添加文件到类目中");
            AddFileResponse addResponse = addFile(client, leaseId, parser, categoryId, workspaceId);
            String fileId = addResponse.getBody().getData().getFileId();

            // 步骤6：检查更新后的文件状态
            System.out.println("步骤6：检查阿里云百炼中的文件状态");
            while (true) {
                DescribeFileResponse describeResponse = describeFile(client, workspaceId, fileId);
                String status = describeResponse.getBody().getData().getStatus();
                System.out.println("当前文件状态：" + status);
                if ("INIT".equals(status)) {
                    System.out.println("文件待解析，请稍候...");
                } else if ("PARSING".equals(status)) {
                    System.out.println("文件解析中，请稍候...");
                } else if ("PARSE_SUCCESS".equals(status)) {
                    System.out.println("文件解析完成！");
                    break;
                } else {
                    System.out.println("未知的文件状态：" + status + "，请联系技术支持。");
                    return null;
                }
                Thread.sleep(5000);
            }

            // 步骤7：提交追加文件任务
            System.out.println("步骤7：提交追加文件任务");
            SubmitIndexAddDocumentsJobResponse indexAddResponse = submitIndexAddDocumentsJob(client, workspaceId, indexId, fileId, sourceType);
            System.out.println("步骤7："+indexAddResponse.getBody().getData().toString());

            String jobId = indexAddResponse.getBody().getData().getId();

            // 步骤8：等待追加任务完成
            System.out.println("步骤8：等待追加任务完成");
            while (true) {
                GetIndexJobStatusResponse jobStatusResponse = getIndexJobStatus(client, workspaceId, jobId, indexId);
                String status = jobStatusResponse.getBody().getData().getStatus();
                System.out.println("当前索引任务状态：" + status);
                if ("COMPLETED".equals(status)) {
                    break;
                }
                Thread.sleep(5000);
            }

            // 步骤9：删除旧文件
            System.out.println("步骤9：删除旧文件");
            deleteIndexDocument(client, workspaceId, indexId, oldFileId);

            System.out.println("阿里云百炼知识库更新成功！");
            return indexId;
        } catch (Exception e) {
            System.out.println("发生错误：" + e.getMessage());
            return null;
        }
    }

    /**
     * 主函数。
     */
//    public  void main(String[] args) {
////        if (!checkEnvironmentVariables()) {
////            System.out.println("环境变量校验未通过。");
////            return;
////        }
//
//        Scanner scanner = new Scanner(System.in);
//        System.out.print("请输入您需要上传文件（更新后的）的实际本地路径（以Linux为例：/xxx/xxx/阿里云百炼系列手机产品介绍.docx）：");
//        String filePath = scanner.nextLine();
//
//        System.out.print("请输入需要更新的知识库ID："); // 即 CreateIndex 接口返回的 Data.Id，您也可以在阿里云百炼控制台的知识库页面获取。
//        String indexId = scanner.nextLine(); // 即 AddFile 接口返回的 FileId。您也可以在阿里云百炼控制台的应用数据页面，单击文件名称旁的 ID 图标获取。
//
//        System.out.print("请输入需要更新的文件的 FileID：");
//        String oldFileId = scanner.nextLine();
//
//        String workspaceId = System.getenv("WORKSPACE_ID");
//        String result = updateKnowledgeBase(filePath, workspaceId, indexId, oldFileId);
//        if (result != null) {
//            System.out.println("知识库更新成功，返回知识库ID: " + result);
//        } else {
//            System.out.println("知识库更新失败。");
//        }
//    }
}
