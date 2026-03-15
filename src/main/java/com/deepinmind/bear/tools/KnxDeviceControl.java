package com.deepinmind.bear.tools;

import com.deepinmind.bear.utils.DeviceIdRegistry;
import com.deepinmind.bear.utils.DeviceType;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.calimero.GroupAddress;
import io.calimero.IndividualAddress;
import io.calimero.KNXException;
import io.calimero.link.KNXNetworkLink;
import io.calimero.link.KNXNetworkLinkIP;
import io.calimero.link.medium.KNXMediumSettings;
import io.calimero.link.medium.KnxIPSettings;
import io.calimero.process.ProcessCommunication;
import io.calimero.process.ProcessCommunicator;
import io.calimero.process.ProcessCommunicatorImpl;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@Slf4j
public class KnxDeviceControl {

    @Autowired
    private DeviceIdRegistry deviceIdRegistry;

    private ExecutorService executorService;
    private InetSocketAddress localSocketAddress;
    private InetSocketAddress remoteSocketAddress;

    @PostConstruct
    public void init() {
        executorService = Executors.newSingleThreadExecutor(); // 改为单线程，确保对网关的访问是串行的
        remoteSocketAddress = new InetSocketAddress("192.168.1.200", 3671);
        localSocketAddress = initLocalAddress();
    }

    @PreDestroy
    public void destroy() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }

    private InetSocketAddress initLocalAddress() {
        try {
            InetAddress localAddress = getLocalAddress(remoteSocketAddress.getAddress());
            if (localAddress != null) {
                return new InetSocketAddress(localAddress, 0);
            }
        } catch (SocketException e) {
            log.error("无法获取网络接口: {}", e.getMessage());
        }
        return null;
    }

    private static InetAddress getLocalAddress(InetAddress remoteAddress) throws SocketException {
        InetAddress fallback = null;
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();
            if (networkInterface.isLoopback() || !networkInterface.isUp()) continue;
            Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress address = addresses.nextElement();
                if (address.getAddress().length == 4) {
                    if (fallback == null) fallback = address;
                    byte[] localBytes = address.getAddress();
                    byte[] remoteBytes = remoteAddress.getAddress();
                    if (localBytes[0] == remoteBytes[0] && localBytes[1] == remoteBytes[1] && localBytes[2] == remoteBytes[2]) {
                        return address;
                    }
                }
            }
        }
        return fallback;
    }


    @Tool(name = "knx_control_light", description = "控制灯")
    public String controlLight(@ToolParam(name = "deviceName", description = "设备列表，必须要在设备列表中") List<String> deviceNames,
        @ToolParam(name = "switchOn", description = "开关是否打开，true/false ") boolean switchOn) {
        log.info("控制设备请求: {}, 值: {}", deviceNames, switchOn);

        if (localSocketAddress == null) {
            return "错误: 本地网络地址未初始化";
        }

        List<String> ids = deviceNames.stream()
                .map(name -> deviceIdRegistry.getDeviceId(name))
                .filter(id -> id != null)
                .toList();

        if (ids.isEmpty()) {
            return "未找到对应的设备ID";
        }

        // 提交给单线程线程池处理，避免多个连接同时冲击网关
        executorService.submit(() -> processBatchControl(ids, switchOn, DeviceType.LIGHT));
        
        return "正在执行控制指令...";
    }

    @Tool(name = "knx_control_curtain", description = "控制窗帘")
    public String controlCurtain(@ToolParam(name = "deviceName", description = "设备列表，必须要在设备列表中") List<String> deviceNames,
        @ToolParam(name = "switchOn", description = "开关是否打开，true/false ") boolean switchOn) {
        log.info("控制设备请求: {}, 值: {}", deviceNames, switchOn);

        if (localSocketAddress == null) {
            return "错误: 本地网络地址未初始化";
        }

        List<String> ids = deviceNames.stream()
                .map(name -> deviceIdRegistry.getDeviceId(name))
                .filter(id -> id != null)
                .toList();

        if (ids.isEmpty()) {
            return "未找到对应的设备ID";
        }

        // 提交给单线程线程池处理，避免多个连接同时冲击网关
        executorService.submit(() -> processBatchControl(ids, switchOn, DeviceType.LIGHT));
        
        return "正在执行控制指令...";
    }

    /**
     * 批量处理设备控制，复用同一个隧道连接
     */
    private void processBatchControl(List<String> deviceIds, boolean switchOn, DeviceType type) {
        KNXNetworkLink link = null;
        ProcessCommunicator pc = null;

        try {
            IndividualAddress deviceAddress = KNXMediumSettings.BackboneRouter;
            KnxIPSettings settings = new KnxIPSettings(deviceAddress);
            
            log.info("建立 KNX 隧道连接...");
            link = KNXNetworkLinkIP.newTunnelingLink(localSocketAddress, remoteSocketAddress, false, settings);
            pc = new ProcessCommunicatorImpl(link);
            
            // 设置更长的响应超时时间（2秒），增加容错
            // pc.setResponseTimeout(2);

            if (!link.isOpen()) {
                log.error("KNX链接建立失败");
                return;
            }

            for (String deviceId : deviceIds) {
                try {
                    GroupAddress ga = new GroupAddress(deviceId);
                    log.info("执行设备控制: {} -> {}", deviceId, switchOn);
                    if(type == DeviceType.CURTAIN) {
                        pc.write(ga, switchOn ? ProcessCommunication.BOOL_UP : ProcessCommunication.BOOL_DOWN, 0);

                    } else {
                    pc.write(ga, switchOn);
                    }
                    log.info("设备 {} 操作成功", deviceId);
                    // 在连续指令间增加极短的停顿，防止总线拥塞
                    Thread.sleep(50); 
                } catch (Exception e) {
                    log.error("设备 {} 操作超时或失败 (但物理操作可能已生效): {}", deviceId, e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("KNX 批处理连接异常: {}", e.getMessage());
        } finally {
            if (pc != null) pc.close();
            if (link != null) link.close();
            log.info("KNX 隧道连接已关闭");
        }
    }
}
