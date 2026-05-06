package deltazero.amarok.network;

import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import org.xbill.DNS.Flags;
import org.xbill.DNS.Message;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.Resolver;
import org.xbill.DNS.Section;
import org.xbill.DNS.SimpleResolver;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import deltazero.amarok.PrefMgr;

public class FirewallVpnService extends VpnService implements Runnable {

    private static final String TAG = "FirewallVpnService";
    public static final String ACTION_START = "deltazero.amarok.network.START_FIREWALL";
    public static final String ACTION_STOP  = "deltazero.amarok.network.STOP_FIREWALL";

    private Thread vpnThread;
    private ParcelFileDescriptor vpnInterface;
    private FileOutputStream out;
    private ExecutorService resolverPool;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        if (ACTION_START.equals(intent.getAction())) {
            if (vpnThread == null || !vpnThread.isAlive()) {
                resolverPool = Executors.newFixedThreadPool(10);
                vpnThread = new Thread(this, "FirewallVpnThread");
                vpnThread.start();
                Log.i(TAG, "Firewall VPN Started");
            }
        } else if (ACTION_STOP.equals(intent.getAction())) {
            stopVpn();
            Log.i(TAG, "Firewall VPN Stopped");
            stopSelf();
        }

        return START_STICKY;
    }

    private void stopVpn() {
        if (vpnThread != null) {
            vpnThread.interrupt();
            vpnThread = null;
        }
        if (resolverPool != null) {
            resolverPool.shutdownNow();
            resolverPool = null;
        }
        try {
            if (vpnInterface != null) {
                vpnInterface.close();
                vpnInterface = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error closing VPN interface", e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopVpn();
    }

    @Override
    public void run() {
        try {
            Builder builder = new Builder();
            builder.addAddress("10.0.0.2", 32);
            builder.addDnsServer("10.0.0.3");
            builder.addRoute("10.0.0.3", 32);
            builder.setSession("Amarok Firewall");
            builder.setBlocking(true);
            
            vpnInterface = builder.establish();
            if (vpnInterface == null) {
                Log.e(TAG, "Failed to establish VPN interface");
                return;
            }

            FileInputStream in = new FileInputStream(vpnInterface.getFileDescriptor());
            out = new FileOutputStream(vpnInterface.getFileDescriptor());

            byte[] packet = new byte[32767];

            while (!Thread.currentThread().isInterrupted()) {
                int length = in.read(packet);
                if (length > 0) {
                    byte[] copy = new byte[length];
                    System.arraycopy(packet, 0, copy, 0, length);
                    processPacket(copy);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "VPN Thread Error", e);
        } finally {
            stopVpn();
        }
    }

    private void processPacket(byte[] packet) {
        if (packet.length < 28) return; // Min IP + UDP header

        int version = (packet[0] & 0xFF) >> 4;
        if (version != 4) return; // Only IPv4

        int ihl = (packet[0] & 0x0F) * 4;
        if (ihl < 20 || packet.length < ihl + 8) return;

        int protocol = packet[9] & 0xFF;
        if (protocol != 17) return; // Only UDP

        byte[] srcIp = new byte[4];
        System.arraycopy(packet, 12, srcIp, 0, 4);
        byte[] dstIp = new byte[4];
        System.arraycopy(packet, 16, dstIp, 0, 4);

        int srcPort = ((packet[ihl] & 0xFF) << 8) | (packet[ihl + 1] & 0xFF);
        int dstPort = ((packet[ihl + 2] & 0xFF) << 8) | (packet[ihl + 3] & 0xFF);

        if (dstPort != 53) return; // Only DNS queries

        int dnsLength = packet.length - ihl - 8;
        if (dnsLength <= 0) return;

        byte[] dnsData = new byte[dnsLength];
        System.arraycopy(packet, ihl + 8, dnsData, 0, dnsLength);

        resolverPool.execute(() -> {
            try {
                Message request = new Message(dnsData);
                if (request.getQuestion() == null) return;
                
                String domain = request.getQuestion().getName().toString(true).toLowerCase(); // without trailing dot

                Set<String> blockedUrls = PrefMgr.getBlockedUrls();
                boolean isBlocked = false;
                for (String item : blockedUrls) {
                    String targetDomain;
                    if (item.startsWith("1:") || item.startsWith("0:")) {
                        if (item.startsWith("0:")) {
                            continue; // Category is disabled
                        }
                        int secondColon = item.indexOf(':', 2);
                        if (secondColon != -1 && secondColon < item.length() - 1) {
                            targetDomain = item.substring(secondColon + 1);
                        } else {
                            continue;
                        }
                    } else {
                        // Legacy format
                        targetDomain = item;
                    }

                    if (domain.equals(targetDomain) || domain.endsWith("." + targetDomain)) {
                        isBlocked = true;
                        break;
                    }
                }

                byte[] responsePayload;

                if (isBlocked) {
                    Log.d(TAG, "Blocked DNS query for: " + domain);
                    Message response = new Message(request.getHeader().getID());
                    response.getHeader().setFlag(Flags.QR);
                    response.getHeader().setFlag(Flags.RA);
                    response.getHeader().setRcode(Rcode.NXDOMAIN);
                    response.addRecord(request.getQuestion(), Section.QUESTION);
                    responsePayload = response.toWire();
                } else {
                    // Forward to real DNS (Cloudflare)
                    int originalId = request.getHeader().getID();
                    Resolver resolver = new SimpleResolver("1.1.1.1");
                    resolver.setTimeout(3);
                    Message response = resolver.send(request);
                    response.getHeader().setID(originalId); // IMPORTANTE: Restaurar ID original!
                    responsePayload = response.toWire();
                }

                byte[] replyPacket = buildIpUdpPacket(responsePayload, dstIp, srcIp, dstPort, srcPort);
                synchronized (out) {
                    out.write(replyPacket);
                }

            } catch (Exception e) {
                // Ignore parsing/resolution errors
            }
        });
    }

    private byte[] buildIpUdpPacket(byte[] payload, byte[] srcIp, byte[] dstIp, int srcPort, int dstPort) {
        int totalLength = 20 + 8 + payload.length;
        byte[] packet = new byte[totalLength];
        
        packet[0] = 0x45; // IPv4, IHL 5
        packet[1] = 0;    // TOS
        packet[2] = (byte) (totalLength >> 8);
        packet[3] = (byte) (totalLength & 0xFF);
        packet[4] = 0; packet[5] = 1; // ID
        packet[6] = 0; packet[7] = 0; // Flags/Offset
        packet[8] = 64; // TTL
        packet[9] = 17; // UDP Protocol
        packet[10] = 0; packet[11] = 0; // Header Checksum (calculated later)
        
        System.arraycopy(srcIp, 0, packet, 12, 4);
        System.arraycopy(dstIp, 0, packet, 16, 4);
        
        // Calculate IP checksum
        long sum = 0;
        for (int i = 0; i < 20; i += 2) {
            sum += ((packet[i] & 0xFF) << 8) | (packet[i + 1] & 0xFF);
        }
        while ((sum >> 16) > 0) {
            sum = (sum & 0xFFFF) + (sum >> 16);
        }
        long checksum = ~sum & 0xFFFF;
        packet[10] = (byte) (checksum >> 8);
        packet[11] = (byte) (checksum & 0xFF);
        
        // UDP Header
        int udpOffset = 20;
        packet[udpOffset] = (byte) (srcPort >> 8);
        packet[udpOffset + 1] = (byte) (srcPort & 0xFF);
        packet[udpOffset + 2] = (byte) (dstPort >> 8);
        packet[udpOffset + 3] = (byte) (dstPort & 0xFF);
        
        int udpLen = 8 + payload.length;
        packet[udpOffset + 4] = (byte) (udpLen >> 8);
        packet[udpOffset + 5] = (byte) (udpLen & 0xFF);
        packet[udpOffset + 6] = 0; // Optional UDP checksum
        packet[udpOffset + 7] = 0;
        
        System.arraycopy(payload, 0, packet, 28, payload.length);
        return packet;
    }
}
