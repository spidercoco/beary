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
import java.time.Duration;
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
        executorService = Executors.newSingleThreadExecutor(); 
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
        log.info("控制灯请求: {}, 值: {}", deviceNames, switchOn);

        if (localSocketAddress == null) {
            return "错误: 本地网络地址未初始化";
        }

        List<String> ids = getDeviceIds(deviceNames);
        if (ids.isEmpty()) return "未找到对应的设备ID";

        executorService.submit(() -> processBatchControl(ids, switchOn ? 1 : 0, DeviceType.LIGHT));
        return "正在执行灯控制指令...";
    }

    @Tool(name = "knx_control_curtain", description = "控制窗帘开关")
    public String controlCurtain(@ToolParam(name = "deviceName", description = "设备列表") List<String> deviceNames,
        @ToolParam(name = "open", description = "是否打开，true为打开(1)，false为关闭(2)") boolean open) {
        log.info("控制窗帘请求: {}, 打开: {}", deviceNames, open);

        if (localSocketAddress == null) return "错误: 本地网络地址未初始化";

        List<String> ids = getDeviceIds(deviceNames);
        if (ids.isEmpty()) return "未找到对应的设备ID";

        // 1=Open, 2=Close
        executorService.submit(() -> processBatchControl(ids, open ? 1 : 2, DeviceType.CURTAIN));
        return "正在执行窗帘控制指令...";
    }

    @Tool(name = "knx_stop_curtain", description = "停止窗帘运动")
    public String stopCurtain(@ToolParam(name = "deviceName", description = "设备列表") List<String> deviceNames) {
        log.info("停止窗帘请求: {}", deviceNames);

        if (localSocketAddress == null) return "错误: 本地网络地址未初始化";

        List<String> ids = getDeviceIds(deviceNames);
        if (ids.isEmpty()) return "未找到对应的设备ID";

        // 0=Stop
        executorService.submit(() -> processBatchControl(ids, 0, DeviceType.CURTAIN));
        return "正在执行停止窗帘指令...";
    }

    private List<String> getDeviceIds(List<String> deviceNames) {
        return deviceNames.stream()
                .map(name -> deviceIdRegistry.getDeviceId(name))
                .filter(id -> id != null)
                .toList();
    }

    /**
     * 批量处理设备控制
     * value: 对于灯是 0/1, 对于窗帘是 0(停)/1(开)/2(关)
     */
    private void processBatchControl(List<String> deviceIds, int value, DeviceType type) {
        KNXNetworkLink link = null;
        ProcessCommunicator pc = null;

        try {
            IndividualAddress deviceAddress = KNXMediumSettings.BackboneRouter;
            KnxIPSettings settings = new KnxIPSettings(deviceAddress);
            
            link = KNXNetworkLinkIP.newTunnelingLink(localSocketAddress, remoteSocketAddress, false, settings);
            pc = new ProcessCommunicatorImpl(link);
            pc.responseTimeout(Duration.ofSeconds(5));

            if (!link.isOpen()) {
                log.error("KNX链接建立失败");
                return;
            }

            for (String deviceId : deviceIds) {
                try {
                    GroupAddress ga = new GroupAddress(deviceId);
                    if(type == DeviceType.CURTAIN) {
                        log.info("发送窗帘 Unscaled 命令: {} -> {}", deviceId, value);
                        pc.write(ga, value, ProcessCommunication.UNSCALED);
                    } else {
                        log.info("发送灯/开关命令: {} -> {}", deviceId, value == 1);
                        pc.write(ga, value == 1);
                    }
                    // 总线指令间隔保持极短以防丢包，但不再等待网关初始状态
                    Thread.sleep(50); 
                } catch (Exception e) {
                    log.error("设备 {} 操作失败: {}", deviceId, e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("KNX 连接异常: {}", e.getMessage());
        } finally {
            if (pc != null) pc.close();
            if (link != null) link.close();
        }
    }

    public static void main(String[] args) throws Exception {
        InetSocketAddress remote = new InetSocketAddress("192.168.1.200", 3671);
        InetAddress localAddr = getLocalAddress(remote.getAddress());
        InetSocketAddress local = new InetSocketAddress(localAddr, 0);

        String curtainAddress = "2/0/67"; 
        String lightAddress = "1/0/37"; 

        KNXNetworkLink link = null;
        ProcessCommunicator pc = null;
        try {
            IndividualAddress deviceAddress = KNXMediumSettings.BackboneRouter;
            link = KNXNetworkLinkIP.newTunnelingLink(local, remote, false, new KnxIPSettings(deviceAddress));
            pc = new ProcessCommunicatorImpl(link);
            pc.responseTimeout(Duration.ofSeconds(5));

            if (!link.isOpen()) return;

            // 1. 测试窗帘: 1=Open
            System.out.println("测试窗帘 Open (1): " + curtainAddress);
            pc.write(new GroupAddress(curtainAddress), 1, ProcessCommunication.UNSCALED);
            
            Thread.sleep(500);

            // 2. 测试灯: true=On
            System.out.println("测试灯 On: " + lightAddress);
            pc.write(new GroupAddress(lightAddress), true);

        } finally {
            if (pc != null) pc.close();
            if (link != null) link.close();
            System.out.println("Done.");
        }
    }
}
