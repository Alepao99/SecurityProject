package it.unisa.securityteam.project;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 *
 * @author apaolillo
 */
public class ServerAuthenticationRecostruction {

    private static final String SKAuthFile = "SecretPartialAuth";
    
    public static void main(String[] args) throws Exception {

        if (args.length != 2) {
            printUsage();
            return;
        }
        if (System.getProperty("javax.net.ssl.trustStore") == null || System.getProperty("javax.net.ssl.trustStorePassword") == null) {
            System.setProperty("javax.net.ssl.trustStore", "truststoServerRec");
            System.setProperty("javax.net.ssl.trustStorePassword", "serverRec");
        }
        try {
            SSLSocketFactory sslsocketfactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket sslsocket = (SSLSocket) sslsocketfactory.createSocket(args[0], Integer.parseInt(args[1]));
            sslsocket.startHandshake();
            protocolRecostructionPartialKey(sslsocket);
            System.out.println("Partial secret key sent successfully");
        } catch (IOException ex) {
            Logger.getLogger(ServerAuthenticationRecostruction.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private static void printUsage() {
        System.out.println("Usage:\n\tjava client.SocketClient [address] [port]");
    }

    private static void protocolRecostructionPartialKey(SSLSocket sslsocket) throws IOException {
        OutputStream out = sslsocket.getOutputStream();
        ElGamalSK SKP = null;
        try {
            ObjectOutputStream outputStream;

            outputStream = new ObjectOutputStream(out);
            SKP = Utils.readSKByte(SKAuthFile, SKP);
            outputStream.writeObject(SKP);
            outputStream.flush();
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
