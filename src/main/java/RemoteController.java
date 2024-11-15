import java.io.*;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Scanner;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceListener;
import javax.net.ssl.*;


public class RemoteController {

    private static int srNum = 0;

    private static X509Certificate loadCertificate(String certFilePath) throws Exception {
        FileInputStream fis = new FileInputStream(certFilePath);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate cert = (X509Certificate) cf.generateCertificate(fis);
        fis.close();
        return cert;
    }

    private static SSLContext obtainCert(String hostname, int port) throws Exception{
        String certFilePath = "C:/Users/lewig/Downloads/MT5581.prod.vizio.com";

        X509Certificate serverCert = loadCertificate(certFilePath);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, new TrustManager[]{ new X509TrustManager() {

            @Override
            public void checkClientTrusted(X509Certificate[] xcs, String string) throws CertificateException {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] xcs, String string) throws CertificateException {

                for (X509Certificate cert : xcs) {
                    if (cert.equals(serverCert)) {
                        return;
                    }
                }
                throw new CertificateException("Server certificate not trusted");
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }
        }}, null);

        return sslContext;
    }

    private static void sendRequest(HttpsURLConnection connection, String method, String body) throws Exception{
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Content-Length", Integer.toString(body.getBytes().length));

        DataOutputStream wr = new DataOutputStream(connection.getOutputStream());

        try {
            wr.writeBytes(body);
        } catch (Exception e) {
            wr.close();
            e.printStackTrace();
        }
    }
    private static String readResponse(HttpsURLConnection connection) throws Exception{
        String token = null;
        int responseCode = connection.getResponseCode();
        System.out.println("Response Code: " + responseCode);

        if (responseCode == 200) {
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();
            FileWriter file = new FileWriter("C:/Users/lewig/Desktop/viziores.html");
            PrintWriter writer = new PrintWriter(file);
            while ((inputLine = in.readLine()) != null) {
                writer.println(inputLine);
                if(inputLine.contains("PAIRING_REQ_TOKEN")) {
                    token = inputLine.substring(inputLine.indexOf(':')+2);
                    token = token.trim();
                }
                response.append(inputLine);
                response.append('\r');
            }
            in.close();
            writer.close();

            System.out.println("Response written");
            
        } else {
            System.out.println("Request failed: HTTP error code " + responseCode);
        }

        return token;
    }
    private static HttpsURLConnection setUpConnection(URL url, SSLContext sslContext) throws Exception {
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setSSLSocketFactory(sslContext.getSocketFactory());
        connection.setHostnameVerifier(new HostnameVerifier() {
            @Override
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        });

        return connection;
    }
    private static class SampleListener implements ServiceListener {
        @Override
        public void serviceAdded(ServiceEvent event) {
            System.out.println("Service added: " + event.getInfo());
        }

        @Override
        public void serviceRemoved(ServiceEvent event) {
            System.out.println("Service removed: " + event.getInfo());
        }

        @Override
        public void serviceResolved(ServiceEvent event) {
            if (srNum == 0) {
                System.out.println("Service resolved | " + event.getInfo().getInet4Addresses()[0] + ":" + event.getInfo().getPort());

                try {
                    String urlString = "https:/" + event.getInfo().getInet4Addresses()[0] + ":" + event.getInfo().getPort();
                    System.out.println(urlString);
                    String deviceId = "12353";
                    String deviceName = "Lewi";

                    String body = "{\"DEVICE_NAME\": \"" + deviceName + "\", \"DEVICE_ID\": \"" + deviceId + "\"}";
                    SSLContext sslContext = obtainCert("192.168.1.66", 7345);
                    URL url;

                    url = new URL(urlString + "/pairing/start");
                    //body = "{\"KEYLIST\": [{\"CODESET\": 5,\"CODE\": 0,\"ACTION\":\"KEYPRESS\"}]}";
                    HttpsURLConnection connection = setUpConnection(url, sslContext);
                    sendRequest(connection, "PUT", body);
                    String pToken = readResponse(connection);
                    connection.disconnect();

                    url = new URL(urlString + "/pairing/pair");
                    Scanner scnr = new Scanner(System.in);
                    System.out.println("Enter pin: ");
                    String rVal = scnr.nextLine();
                    body = "{\"DEVICE_ID\": \"" + deviceId + "\",\"CHALLENGE_TYPE\": 1,\"RESPONSE_VALUE\": \"" + rVal + "\",\"PAIRING_REQ_TOKEN\": " + pToken + "}";
                    connection = setUpConnection(url, sslContext);
                    sendRequest(connection, "PUT", body);
                    readResponse(connection);
                    connection.disconnect();
                    scnr.close();





                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            srNum++;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        try {
            // Create a JmDNS instance
            JmDNS jmdns = JmDNS.create(InetAddress.getLocalHost());

            jmdns.addServiceListener("_viziocast._tcp.local.", new SampleListener());

            // Wait a bit
            //Thread.sleep(30000);
        } catch (UnknownHostException e) {
            System.out.println(e.getMessage());
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }
}
