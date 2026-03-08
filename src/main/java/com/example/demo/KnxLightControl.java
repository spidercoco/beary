package com.example.demo;

import io.calimero.GroupAddress;
import io.calimero.IndividualAddress;
import io.calimero.KNXException;
import io.calimero.link.KNXNetworkLink;
import io.calimero.link.KNXNetworkLinkIP;
import io.calimero.link.medium.KNXMediumSettings;
import io.calimero.link.medium.KnxIPSettings;
import io.calimero.process.ProcessCommunicator;
import io.calimero.process.ProcessCommunicatorImpl;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public class KnxLightControl {

    /**
     * 获取与远程地址在同一网络的本地IP地址
     * 如果找不到，返回第一个可用的非回环IPv4地址
     */
    private static InetAddress getLocalAddress(InetAddress remoteAddress) throws SocketException {
        InetAddress fallback = null;
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();
            if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                continue;
            }
            Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress address = addresses.nextElement();
                // 只考虑IPv4地址
                if (address.getAddress().length == 4 && remoteAddress.getAddress().length == 4) {
                    // 保存第一个可用的IPv4地址作为备选
                    if (fallback == null) {
                        fallback = address;
                    }
                    byte[] localBytes = address.getAddress();
                    byte[] remoteBytes = remoteAddress.getAddress();
                    // 检查前三个字节是否相同（假设是 /24 子网）
                    if (localBytes[0] == remoteBytes[0] && 
                        localBytes[1] == remoteBytes[1] && 
                        localBytes[2] == remoteBytes[2]) {
                        return address;
                    }
                }
            }
        }
        // 如果找不到匹配的，返回第一个可用的IPv4地址
        return fallback;
    }

    public static void main(String[] args) {
        InetSocketAddress remote = new InetSocketAddress("192.168.1.200", 3671);
        InetSocketAddress local = null;  // null 表示让系统自动选择本地地址和端口

        try {
            // 尝试获取与远程地址在同一网络的本地IP
            InetAddress localAddress = getLocalAddress(remote.getAddress());
            if (localAddress != null) {
                local = new InetSocketAddress(localAddress, 0);  // 使用找到的IP，端口自动分配
                byte[] localBytes = localAddress.getAddress();
                byte[] remoteBytes = remote.getAddress().getAddress();
                // 检查是否在同一子网
                boolean sameSubnet = localBytes[0] == remoteBytes[0] && 
                                    localBytes[1] == remoteBytes[1] && 
                                    localBytes[2] == remoteBytes[2];
                if (sameSubnet) {
                    System.out.println("使用本地地址: " + localAddress.getHostAddress() + " (与远程地址在同一网络)");
                } else {
                    System.out.println("警告: 使用本地地址: " + localAddress.getHostAddress() + 
                                     " (与远程地址 " + remote.getAddress().getHostAddress() + " 不在同一网络)");
                }
            } else {
                System.err.println("错误: 无法找到可用的网络接口");
                return;
            }
        } catch (SocketException e) {
            System.err.println("无法获取网络接口: " + e.getMessage());
            return;
        }

        KNXNetworkLink link = null;
        ProcessCommunicator pc = null;

        try {
            // 建立 KNX/IP Tunneling 链接
            // 尝试使用 BackboneRouter (0.0.0) 让服务器自动分配地址
            // 如果遇到问题，可以尝试使用具体的地址如 "15.15.0"
            IndividualAddress deviceAddress = KNXMediumSettings.BackboneRouter;
            System.out.println("使用设备地址: " + deviceAddress + " (让服务器自动分配)");
            KnxIPSettings settings = new KnxIPSettings(deviceAddress);
            link = KNXNetworkLinkIP.newTunnelingLink(local, remote, false, settings);
            pc = new ProcessCommunicatorImpl(link);
            
            // 等待连接稳定，确保序列号同步
            Thread.sleep(500);
            
            // 注意: 如果遇到 "invalid rcv-seq" 错误，可能是:
            // 1. 连接状态不同步 - 尝试重新建立连接
            // 2. 服务器端序列号重置 - 增加等待时间
            // 3. 多个连接冲突 - 确保只有一个连接在使用

            // 检查链接状态
            if (!link.isOpen()) {
                throw new KNXException("KNX链接未打开");
            }
            System.out.println("KNX链接已建立: " + link.getName());

            GroupAddress lightGa = new GroupAddress("1/0/36");

            // 第一次写入
            System.out.println("第一次写入 - 发送命令到 " + lightGa + ": ON");
            try {
                pc.write(lightGa, true);
                System.out.println("第一次写入成功");
            } catch (KNXException e) {
                System.err.println("第一次写入失败: " + e.getMessage());
                if (e.getMessage() != null && e.getMessage().contains("timeout")) {
                    System.err.println("提示: 超时可能是由于:");
                    System.err.println("  1. KNX设备未响应");
                    System.err.println("  2. 组地址不存在或配置错误");
                    System.err.println("  3. 网络连接问题");
                }
                // 不立即抛出，继续尝试后续操作
            } catch (Exception e) {
                System.err.println("第一次写入失败: " + e.getMessage());
                e.printStackTrace();
            }
            
            // 等待更长时间，确保第一次操作完成
            Thread.sleep(2000);
            
            // 检查链接状态
            if (!link.isOpen()) {
                System.err.println("警告: KNX链接在第一次写入后已关闭，尝试重新连接...");
                // 可以在这里添加重连逻辑
            }

            // 第二次写入
            System.out.println("第二次写入 - 发送命令到 " + lightGa + ": OFF");
            try {
                pc.write(lightGa, false);
                System.out.println("第二次写入成功");
            } catch (KNXException e) {
                System.err.println("第二次写入失败: " + e.getMessage());
                if (e.getMessage() != null && e.getMessage().contains("rcv-seq")) {
                    System.err.println("提示: 序列号不匹配，可能是连接状态不同步");
                    System.err.println("建议: 重新建立连接或增加等待时间");
                }
                e.printStackTrace();
            } catch (Exception e) {
                System.err.println("第二次写入失败: " + e.getMessage());
                e.printStackTrace();
            }
            
            Thread.sleep(1000);
            
            // 检查链接状态
            if (!link.isOpen()) {
                System.err.println("警告: KNX链接在第二次写入后已关闭");
            } else {
                // 第三次写入 - 控制另一个地址
                GroupAddress lightGa2 = new GroupAddress("1/0/37");
                System.out.println("第三次写入 - 发送命令到 " + lightGa2 + ": OFF");
                try {
                    pc.write(lightGa2, false);
                    System.out.println("第三次写入成功");
                } catch (Exception e) {
                    System.err.println("第三次写入失败: " + e.getMessage());
                    e.printStackTrace();
                }
            }

            System.out.println("所有操作完成");

        } catch (KNXException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (pc != null) {
                pc.close();
            }
            if (link != null) {
                link.close();
            }
        }
    }
}
